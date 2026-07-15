package com.mygame;

import com.jme3.material.Material;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;

public class Chunk {
    public static final int SIZE_X = 16;
    public static final int SIZE_Y = 128;
    public static final int SIZE_Z = 16;

    private final byte[][][] blocks = new byte[SIZE_X][SIZE_Y][SIZE_Z];
    private final Node chunkNode = new Node("ChunkNode");
    private final int chunkX;
    private final int chunkZ;
    private final World world;

    private final Mesh[] prebuiltMeshes = new Mesh[31];

    private volatile boolean hasFluids = false;
    private volatile boolean hasFallingBlocks = false;

    public boolean hasFluids() { return hasFluids; }
    public boolean hasFallingBlocks() { return hasFallingBlocks; }

    private static double fbm(double x, double z, int octaves, double persistence, double scale) {
        double total = 0;
        double frequency = scale;
        double amplitude = 1;
        double maxValue = 0;
        for (int i = 0; i < octaves; i++) {
            total += PerlinNoise.noise(x * frequency, z * frequency) * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= 2.0;
        }
        return total / maxValue;
    }

    // Ридж-шум (острые гребни) — для гор и дюн. |noise| перевёрнут -> хребты.
    private static double ridged(double x, double z, int octaves, double persistence, double scale) {
        double total = 0, freq = scale, amp = 1, maxV = 0;
        for (int i = 0; i < octaves; i++) {
            double n = 1.0 - Math.abs(PerlinNoise.noise(x * freq, z * freq));
            total += n * n * amp;
            maxV += amp;
            amp *= persistence;
            freq *= 2.0;
        }
        return total / maxV;
    }

    /**
     * Детализированная высота ландшафта для конкретного биома.
     * Раньше на каждую колонку считались ВСЕ высоты (впустую); теперь только нужная,
     * с добавлением рельефа: гребни гор, дюны пустыни, холмы леса/джунглей.
     */
    static double biomeHeight(int biome, double x, double z) {
        switch (biome) {
            case 1: { // DESERT — дюны (мягкие волны + мелкая рябь)
                double dunes = fbm(x, z, 3, 0.45, 0.012) * 10.0;
                double ripple = Math.sin((x + z) * 0.06) * 1.6;
                return 68 + dunes + ripple;
            }
            case 2: // TUNDRA — пологие холмы
                return 70 + fbm(x, z, 3, 0.40, 0.010) * 12.0;
            case 3: { // MOUNTAINS — цельные массивы с гребнями (не спайки!)
                // Крупная низкочастотная база задаёт форму горы, ridged добавляет
                // умеренные гребни. Малая амплитуда + низкая частота -> НЕ узкие столбы.
                double base = fbm(x, z, 4, 0.5, 0.005) * 26.0 + 82;
                double ridge = ridged(x, z, 3, 0.5, 0.006) * 26.0;
                double h = base + ridge;
                return Math.min(h, 118); // потолок ниже SIZE_Y, чтобы не упираться в край
            }
            case 4: // REDWOOD — крупные холмы
                return 74 + fbm(x, z, 4, 0.50, 0.010) * 24.0;
            case 5: { // VOLCANIC — неровное плато с провалами
                double h = fbm(x, z, 4, 0.60, 0.015) * 12.0 + 64;
                double crater = ridged(x, z, 3, 0.5, 0.02) * 8.0;
                return h - crater * 0.5;
            }
            case 6: // SWAMP — почти плоско у воды
                return 61.5 + fbm(x, z, 2, 0.30, 0.020) * 4.0;
            case 7: // CHERRY — мягкие холмы
                return 72 + fbm(x, z, 3, 0.50, 0.010) * 16.0;
            case 8: // OAK FOREST — холмистый
                return 73 + fbm(x, z, 4, 0.48, 0.009) * 20.0;
            case 9: // AUTUMN — холмистый
                return 73 + fbm(x, z, 3, 0.45, 0.011) * 15.0;
            case 10: // GLACIER — высокие сглаженные плато
                return 76 + fbm(x, z, 4, 0.55, 0.008) * 26.0;
            case 11: // MYCELIUM — низкие холмы
                return 66 + fbm(x, z, 3, 0.40, 0.014) * 11.0;
            case 12: // OCEAN — дно
                return 38 + fbm(x, z, 2, 0.30, 0.02) * 4.0;
            case 13: // BEACH — почти плоский берег
                return 62 + fbm(x, z, 2, 0.30, 0.02) * 2.0;
            case 14: { // SAVANNA — плато со ступенями (акациевые равнины)
                double h = fbm(x, z, 3, 0.42, 0.011) * 13.0 + 71;
                double plateau = Math.floor(fbm(x, z, 2, 0.4, 0.006) * 4.0) * 2.0;
                return h + plateau;
            }
            case 15: { // JUNGLE — бугристый, густой рельеф
                double h = fbm(x, z, 4, 0.52, 0.012) * 22.0 + 72;
                double bumps = ridged(x, z, 3, 0.5, 0.03) * 4.0;
                return h + bumps;
            }
            default: // OAK FOREST по умолчанию
                return 73 + fbm(x, z, 3, 0.48, 0.010) * 18.0;
        }
    }

    public Chunk(int cx, int cz, Material[] blockMaterials, World world) {
        this.chunkX = cx;
        this.chunkZ = cz;
        this.world = world;

        chunkNode.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);

        double seedX = world.getSeedOffsetX();
        double seedZ = world.getSeedOffsetZ();

        java.util.Random borderRand = new java.util.Random();

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                int worldX = cx * SIZE_X + x;
                int worldZ = cz * SIZE_Z + z;

                if (world.isFlatWorld()) {
                    int height = 4;
                    for (int y = 0; y < SIZE_Y; y++) {
                        if (y == 0) blocks[x][y][z] = 3;
                        else if (y < height - 1) blocks[x][y][z] = 2;
                        else if (y == height - 1) blocks[x][y][z] = 1;
                        else blocks[x][y][z] = 0;
                    }
                    continue;
                }

                double worldXS = worldX + seedX;
                double worldZS = worldZ + seedZ;

                int biome = getBiomeAt(worldX, worldZ, world);

                // ОПТИМИЗАЦИЯ + ДЕТАЛИЗАЦИЯ: считаем высоту ТОЛЬКО для нужного биома.
                // Блендим ВЫСОТУ (не тип биома!) по 5 точкам окрестности -> плавные
                // склоны на стыках биомов без отвесных обрывов. Высота непрерывна,
                // поэтому усреднять её корректно (в отличие от номеров биомов).
                double heightValue = biomeHeight(biome, worldXS, worldZS);
                {
                    int[][] off = { {8, 0}, {-8, 0}, {0, 8}, {0, -8} };
                    double acc = heightValue;
                    for (int[] o : off) {
                        int nb = getBiomeAt(worldX + o[0], worldZ + o[1], world);
                        acc += biomeHeight(nb, worldXS + o[0], worldZS + o[1]);
                    }
                    heightValue = acc / 5.0;
                }

                int height = (int) Math.max(5, Math.min(heightValue, SIZE_Y - 1));

                long seed = (long) (worldX * 1234567L + worldZ * 7654321L);
                borderRand.setSeed(seed);
                double randVal = borderRand.nextDouble();

                byte topBlock = 1;
                byte subBlock = 2;

                if (biome == 1) {
                    topBlock = 8;
                    subBlock = 8;
                } else if (biome == 2) {
                    topBlock = 10;
                    subBlock = 2;
                } else if (biome == 3) {
                    topBlock = (byte) (randVal > 0.65 ? 2 : 3);
                    subBlock = 3;
                } else if (biome == 10) {
                    topBlock = 22;
                    subBlock = 22;
                } else if (biome == 11) {
                    topBlock = 23;
                    subBlock = 2;
                } else if (biome == 5) {
                    topBlock = (byte) (randVal > 0.85 ? 14 : 13);
                    subBlock = 13;
                } else if (biome == 12) {
                    topBlock = 3;   // дно океана — камень
                    subBlock = 3;
                } else if (biome == 13) {
                    topBlock = 8;   // пляж — песок
                    subBlock = 8;
                } else if (biome == 14) {
                    topBlock = 1;   // саванна — трава
                    subBlock = 2;
                } else if (biome == 15) {
                    topBlock = 1;   // джунгли — трава
                    subBlock = 2;
                } else {
                    topBlock = 1;
                    subBlock = 2;
                }

                for (int y = 0; y < SIZE_Y; y++) {
                    if (y < height - 4) {
                        double oreNoise = PerlinNoise.noise((worldX + seedX) * 0.15, y * 0.15, (worldZ + seedZ) * 0.15);

                        if (biome == 8 && y < 45 && oreNoise > 0.65) {
                            blocks[x][y][z] = 19;
                        } else if (y < 45 && oreNoise > 0.72) {
                            blocks[x][y][z] = 4;
                        } else if (y < 35 && oreNoise < -0.76) {
                            blocks[x][y][z] = 5;
                        } else {
                            blocks[x][y][z] = 3;
                        }
                    } else if (y < height - 1) {
                        blocks[x][y][z] = subBlock;
                    } else if (y == height - 1) {
                        blocks[x][y][z] = topBlock;
                    }
                }

                for (int y = 4; y < height - 6; y++) {
                    double sampleX = (worldX + seedX) * 0.035;
                    double sampleY = y * 0.07;
                    double sampleZ = (worldZ + seedZ) * 0.035;

                    double caveNoise = PerlinNoise.noise(sampleX, sampleY, sampleZ);
                    boolean isSpaghetti = Math.abs(caveNoise) < 0.065;
                    boolean isCheese = caveNoise > 0.54 && y < 45;

                    if (isSpaghetti || isCheese) {
                        blocks[x][y][z] = 0;
                    }
                }

                for (int y = 0; y < SIZE_Y; y++) {
                    if (blocks[x][y][z] == 0) {
                        if (y <= 62 && y >= height - 1) {
                            blocks[x][y][z] = 27;
                            hasFluids = true;
                        } else if (y < 12) {
                            blocks[x][y][z] = 28;
                            hasFluids = true;
                        }
                    } else if (blocks[x][y][z] == 8) {
                        hasFallingBlocks = true;
                    }
                }

                blocks[x][0][z] = 3;
                blocks[x][1][z] = 3;
            }
        }

        if (world.isFlatWorld()) {
            world.applySavedBlocksToChunk(this);
            return;
        }

        java.util.Random rand = new java.util.Random((long) (cx * 7392 + cz * 4821 + world.getWorldSeed() % 10000));
        for (int x = 2; x < SIZE_X - 2; x++) {
            for (int z = 2; z < SIZE_Z - 2; z++) {
                int worldX = cx * SIZE_X + x;
                int worldZ = cz * SIZE_Z + z;
                int biome = getBiomeAt(worldX, worldZ, world);

                int surfaceY = -1;
                for (int y = SIZE_Y - 1; y >= 0; y--) {
                    byte b = blocks[x][y][z];
                    if (b == 1 || b == 10 || b == 8 || b == 23 || b == 22) {
                        surfaceY = y;
                        break;
                    }
                }

                if (surfaceY != -1 && surfaceY < SIZE_Y - 20) {
                    byte blockBelow = blocks[x][surfaceY][z];

                    if (biome == 9 && blockBelow == 1 && rand.nextFloat() < 0.015f) {
                        int trunkHeight = 5 + rand.nextInt(3);
                        for (int ty = 1; ty <= trunkHeight; ty++) {
                            blocks[x][surfaceY + ty][z] = 20;
                        }
                        int leafCenterY = surfaceY + trunkHeight + 1;
                        for (int lx = -2; lx <= 2; lx++) {
                            for (int ly = -1; ly <= 2; ly++) {
                                for (int lz = -2; lz <= 2; lz++) {
                                    if (Math.abs(lx) + Math.abs(lz) <= 3) {
                                        int py = leafCenterY + ly;
                                        if (py < SIZE_Y && blocks[x + lx][py][z + lz] == 0) {
                                            blocks[x + lx][py][z + lz] = 7;
                                        }
                                    }
                                }
                            }
                        }
                    } else if (biome == 11 && blockBelow == 23 && rand.nextFloat() < 0.025f) {
                        int trunkHeight = 4 + rand.nextInt(3);
                        for (int ty = 1; ty <= trunkHeight; ty++) {
                            blocks[x][surfaceY + ty][z] = 10;
                        }
                        int hatY = surfaceY + trunkHeight + 1;
                        for (int lx = -2; lx <= 2; lx++) {
                            for (int lz = -2; lz <= 2; lz++) {
                                if (Math.abs(lx) == 2 && Math.abs(lz) == 2) continue;
                                if (hatY < SIZE_Y) {
                                    blocks[x + lx][hatY][z + lz] = 24;
                                }
                            }
                        }
                    } else if (biome == 10 && blockBelow == 22 && rand.nextFloat() < 0.008f) {
                        int spikeHeight = 8 + rand.nextInt(10);
                        for (int ty = 1; ty <= spikeHeight; ty++) {
                            int r = Math.max(1, (spikeHeight - ty) / 4);
                            for (int lx = -r; lx <= r; lx++) {
                                for (int lz = -r; lz <= r; lz++) {
                                    if (surfaceY + ty < SIZE_Y) {
                                        blocks[x + lx][surfaceY + ty][z + lz] = 22;
                                    }
                                }
                            }
                        }
                    } else if (biome == 0 && blockBelow == 1 && rand.nextFloat() < 0.015f) {
                        int trunkHeight = 5 + rand.nextInt(3);
                        for (int ty = 1; ty <= trunkHeight; ty++) {
                            blocks[x][surfaceY + ty][z] = 6;
                        }
                        int leafCenterY = surfaceY + trunkHeight + 1;
                        for (int lx = -2; lx <= 2; lx++) {
                            for (int ly = -1; ly <= 2; ly++) {
                                for (int lz = -2; lz <= 2; lz++) {
                                    if (Math.abs(lx) + Math.abs(lz) <= 3) {
                                        int py = leafCenterY + ly;
                                        if (py < SIZE_Y && blocks[x + lx][py][z + lz] == 0) {
                                            blocks[x + lx][py][z + lz] = 7;
                                        }
                                    }
                                }
                            }
                        }
                    } else if (biome == 4 && blockBelow == 2 && rand.nextFloat() < 0.012f) {
                        // REDWOOD: высокая узкая ёлка
                        int trunkHeight = 9 + rand.nextInt(5);
                        for (int ty = 1; ty <= trunkHeight; ty++) {
                            blocks[x][surfaceY + ty][z] = 12;
                        }
                        for (int ly = 0; ly < trunkHeight; ly += 2) {
                            int r = 1 + (ly / 4);
                            int py = surfaceY + ly + 2;
                            for (int lx = -r; lx <= r; lx++) {
                                for (int lz = -r; lz <= r; lz++) {
                                    if ((Math.abs(lx) + Math.abs(lz) <= r + 1) && blocks[x + lx][py][z + lz] == 0) {
                                        blocks[x + lx][py][z + lz] = 11;
                                    }
                                }
                            }
                        }
                    } else if (biome == 7 && blockBelow == 1 && rand.nextFloat() < 0.016f) {
                        // CHERRY: розовое дерево
                        int trunkHeight = 5 + rand.nextInt(3);
                        for (int ty = 1; ty <= trunkHeight; ty++) {
                            blocks[x][surfaceY + ty][z] = 15;
                        }
                        int leafCenterY = surfaceY + trunkHeight + 1;
                        for (int lx = -2; lx <= 2; lx++) {
                            for (int ly = -1; ly <= 2; ly++) {
                                for (int lz = -2; lz <= 2; lz++) {
                                    if (Math.abs(lx) + Math.abs(lz) <= 3 && rand.nextFloat() < 0.92f) {
                                        int py = leafCenterY + ly;
                                        if (py < SIZE_Y && blocks[x + lx][py][z + lz] == 0) {
                                            blocks[x + lx][py][z + lz] = 16;
                                        }
                                    }
                                }
                            }
                        }
                    } else if (biome == 8 && blockBelow == 1 && rand.nextFloat() < 0.014f) {
                        // BIRCH (forest): белая берёза
                        int trunkHeight = 5 + rand.nextInt(3);
                        for (int ty = 1; ty <= trunkHeight; ty++) {
                            blocks[x][surfaceY + ty][z] = 20;
                        }
                        int leafCenterY = surfaceY + trunkHeight + 1;
                        for (int lx = -2; lx <= 2; lx++) {
                            for (int ly = -1; ly <= 2; ly++) {
                                for (int lz = -2; lz <= 2; lz++) {
                                    if (Math.abs(lx) + Math.abs(lz) <= 3) {
                                        int py = leafCenterY + ly;
                                        if (py < SIZE_Y && blocks[x + lx][py][z + lz] == 0) {
                                            blocks[x + lx][py][z + lz] = 7;
                                        }
                                    }
                                }
                            }
                        }
                    } else if (biome == 1 && blockBelow == 8 && rand.nextFloat() < 0.02f) {
                        // DESERT: кактус
                        int h = 3 + rand.nextInt(3);
                        for (int ty = 1; ty <= h; ty++) {
                            if (surfaceY + ty < SIZE_Y) blocks[x][surfaceY + ty][z] = 9;
                        }
                    } else if (biome == 3 && blockBelow == 3 && rand.nextFloat() < 0.015f) {
                        // MOUNTAIN: низкий валун (не столб!)
                        int h = 1 + rand.nextInt(2);
                        for (int ty = 1; ty <= h; ty++) {
                            if (surfaceY + ty < SIZE_Y) blocks[x][surfaceY + ty][z] = 3;
                        }
                    } else if (biome == 14 && blockBelow == 1 && rand.nextFloat() < 0.010f) {
                    // SAVANNA: акация (тонкий высокий ствол + плоская крона)
                    int trunkH = 7 + rand.nextInt(4);
                    for (int ty = 1; ty <= trunkH; ty++) {
                        if (surfaceY + ty < SIZE_Y) blocks[x][surfaceY + ty][z] = 12;
                    }
                    int capY = surfaceY + trunkH + 1;
                    for (int lx = -2; lx <= 2; lx++) {
                        for (int lz = -2; lz <= 2; lz++) {
                            if (Math.abs(lx) <= 1 || Math.abs(lz) <= 1) {
                                int py = capY;
                                if (py < SIZE_Y && blocks[x + lx][py][z + lz] == 0) blocks[x + lx][py][z + lz] = 11;
                            }
                        }
                    }
                } else if (biome == 14 && blockBelow == 1 && rand.nextFloat() < 0.25f) {
                    // SAVANNA: высокая трава (мелкий декор)
                    blocks[x][surfaceY + 1][z] = 16;
                } else if (biome == 15 && blockBelow == 1 && rand.nextFloat() < 0.022f) {
                    // JUNGLE: широкое дерево (толстый ствол + огромная крона)
                    int trunkH = 8 + rand.nextInt(6);
                    for (int ty = 1; ty <= trunkH; ty++) {
                        if (surfaceY + ty < SIZE_Y) blocks[x][surfaceY + ty][z] = 6;
                    }
                    int baseY = surfaceY + trunkH;
                    for (int ly = 0; ly < 4; ly++) {
                        int r = 3 - ly / 2;
                        int py = baseY - ly;
                        for (int lx = -r; lx <= r; lx++) {
                            for (int lz = -r; lz <= r; lz++) {
                                if (ly > 0 && (Math.abs(lx) == r && Math.abs(lz) == r)) continue;
                                if (py > 0 && py < SIZE_Y && blocks[x + lx][py][z + lz] == 0) blocks[x + lx][py][z + lz] = 7;
                            }
                        }
                    }
                } else if (biome == 15 && blockBelow == 1 && rand.nextFloat() < 0.30f) {
                    // JUNGLE: лианы/кусты (мелкий декор)
                    blocks[x][surfaceY + 1][z] = (byte) (rand.nextFloat() < 0.5f ? 7 : 16);
                } else if ((biome == 0 || biome == 4 || biome == 7) && blockBelow == 1 && rand.nextFloat() < 0.05f) {
                        // КУСТЫ / ЦВЕТЫ (мелкий декор)
                        blocks[x][surfaceY + 1][z] = (byte) (rand.nextFloat() < 0.5f ? 7 : 16);
                    }
                }
            }
        }

        world.applySavedBlocksToChunk(this);
    }

    public static int getBiomeAt(int x, int z, World world) {
        // Биом берём напрямую из непрерывного низкочастотного шума (temp/humid).
        // Плавность границ обеспечивают САМИ поля temp/humid + мягкий джиттер в
        // rawBiomeAt. РАНЬШЕ здесь усреднялись НОМЕРА биомов (desert=1..jungle=15) —
        // это математически бессмысленно и порождало ложные биомы на стыках
        // (среднее пустыни и джунглей давало посторонний биом). Убрано.
        return rawBiomeAt(x, z, world);
    }

    // "Сырой" биом без сглаживания — используется внутри getBiomeAt и генерации
    public static int rawBiomeAt(int x, int z, World world) {
        double seedX = world.getSeedOffsetX();
        double seedZ = world.getSeedOffsetZ();

        // Континентальный маскинг: очень крупный шум -> суша/океан
        double continent = PerlinNoise.noise((x + seedX) * 0.0006, (z + seedZ) * 0.0006);

        // Лёгкий джиттер границ (чтобы края биомов были не идеально ровными,
        // но и НЕ дробили биомы на мелкие пятна): низкая частота + малая амплитуда.
        double jitterX = PerlinNoise.noise((x + seedX) * 0.01, (z + seedZ) * 0.01) * 40.0;
        double jitterZ = PerlinNoise.noise((z + seedX) * 0.01, (x + seedZ) * 0.01) * 40.0;

        // КРУПНЫЕ биомы: низкая частота temp/humid -> большие цельные зоны
        // (было 0.0012/0.0015 -> биомы были мелкими и хаотичными).
        double temp = PerlinNoise.noise((x + seedX + jitterX) * 0.0004, (z + seedZ + jitterZ) * 0.0004);
        double humid = PerlinNoise.noise((x + seedX + jitterX + 10000) * 0.0005, (z + seedZ + jitterZ - 10000) * 0.0005);

        // Океан/глубоководье по континенту -> не плодим сушу везде
        if (continent < -0.55) return 12; // OCEAN
        if (continent < -0.38) return 13; // BEACH/SHORE


        if (temp < -0.45) {
            return humid > 0.1 ? 10 : 2;
        } else if (temp < -0.15) {
            return humid > 0.3 ? 8 : 7;
        } else if (temp > 0.45) {
            return humid > 0.2 ? 5 : 6;
        } else if (temp > 0.15) {
            return humid < -0.2 ? 1 : 11;
        } else {
            if (humid > 0.6 && temp > 0.3) {
                return 15; // JUNGLE (тёплый + влажный)
            } else if (humid > 0.35) {
                return 9;
            } else if (humid > 0.0) {
                return 4;
            } else if (temp > 0.55 && humid > 0.2) {
                return 14; // SAVANNA (жаркий, средне-влажный)
            } else {
                return 0;
            }
        }
    }

    public byte getBlockDirect(int x, int y, int z) {
        if (x < 0 || x >= SIZE_X || y < 0 || y >= SIZE_Y || z < 0 || z >= SIZE_Z) return 0;
        return blocks[x][y][z];
    }

    private boolean isFaceVisible(byte currentBlock, int neighborX, int neighborY, int neighborZ) {
        if (neighborY < 0 || neighborY >= SIZE_Y) return true;

        byte neighborBlock;
        int lx = neighborX - chunkX * SIZE_X;
        int lz = neighborZ - chunkZ * SIZE_Z;

        if (lx >= 0 && lx < SIZE_X && lz >= 0 && lz < SIZE_Z) {
            neighborBlock = blocks[lx][neighborY][lz];
        } else {
            neighborBlock = world.getBlockAtThreadSafe(neighborX, neighborY, neighborZ);
        }

        if (neighborBlock == 0) return true;
        if (neighborBlock == currentBlock) return false;

        if (currentBlock == 27 && neighborBlock == 27) return false;
        if (currentBlock == 28 && neighborBlock == 28) return false;

        if (currentBlock == 27) {
            return neighborBlock == 0 || neighborBlock == 7 || neighborBlock == 11 || neighborBlock == 16 || neighborBlock == 21 || neighborBlock == 28;
        }
        if (currentBlock == 28) {
            return neighborBlock == 0 || neighborBlock == 7 || neighborBlock == 11 || neighborBlock == 16 || neighborBlock == 21 || neighborBlock == 27;
        }

        return (neighborBlock == 7 || neighborBlock == 11 || neighborBlock == 16 || neighborBlock == 21 || neighborBlock == 27 || neighborBlock == 28);
    }

    public final void prebuildMeshes() {
        for (int i = 1; i <= 30; i++) {
            prebuiltMeshes[i] = null;
        }

        MeshBuilder[] builders = new MeshBuilder[31];
        for (int i = 1; i <= 30; i++) {
            builders[i] = new MeshBuilder();
        }

        for (int d = 0; d < 3; d++) {
            int u = (d + 1) % 3;
            int v = (d + 2) % 3;

            int[] x = new int[3];
            int[] q = new int[3];

            q[d] = 1;

            int limitD = (d == 0) ? SIZE_X : (d == 1) ? SIZE_Y : SIZE_Z;
            int limitU = (u == 0) ? SIZE_X : (u == 1) ? SIZE_Y : SIZE_Z;
            int limitV = (v == 0) ? SIZE_X : (v == 1) ? SIZE_Y : SIZE_Z;

            int[][] mask = new int[limitU][limitV];
            boolean[][] backFace = new boolean[limitU][limitV];

            for (x[d] = -1; x[d] < limitD; ) {
                for (x[u] = 0; x[u] < limitU; x[u]++) {
                    for (x[v] = 0; x[v] < limitV; x[v]++) {
                        int blockCurrent = 0;
                        int blockNeighbor = 0;

                        int wx = chunkX * SIZE_X + x[0];
                        int wy = x[1];
                        int wz = chunkZ * SIZE_Z + x[2];

                        if (x[d] >= 0) {
                            blockCurrent = getBlockDirect(x[0], x[1], x[2]);
                        }
                        if (x[d] < limitD - 1) {
                            blockNeighbor = getBlockDirect(x[0] + q[0], x[1] + q[1], x[2] + q[2]);
                        }

                        boolean currentSolid = (blockCurrent > 0);
                        boolean neighborSolid = (blockNeighbor > 0);

                        mask[x[u]][x[v]] = 0;
                        backFace[x[u]][x[v]] = false;

                        if (currentSolid != neighborSolid) {
                            if (currentSolid) {
                                if (isFaceVisible((byte) blockCurrent, wx + q[0], wy + q[1], wz + q[2])) {
                                    mask[x[u]][x[v]] = blockCurrent;
                                    backFace[x[u]][x[v]] = false;
                                }
                            } else {
                                if (isFaceVisible((byte) blockNeighbor, wx, wy, wz)) {
                                    mask[x[u]][x[v]] = blockNeighbor;
                                    backFace[x[u]][x[v]] = true;
                                }
                            }
                        }
                    }
                }

                x[d]++;

                boolean[][] visited = new boolean[limitU][limitV];
                for (int j = 0; j < limitU; j++) {
                    for (int i = 0; i < limitV; i++) {
                        int type = mask[j][i];
                        if (type <= 0 || visited[j][i]) continue;

                        boolean isBack = backFace[j][i];

                        int w, h;
                        for (w = 1; i + w < limitV && mask[j][i + w] == type && backFace[j][i + w] == isBack && !visited[j][i + w]; w++);

                        boolean ok = true;
                        for (h = 1; j + h < limitU; h++) {
                            for (int k = 0; k < w; k++) {
                                if (mask[j + h][i + k] != type || backFace[j + h][i + k] != isBack || visited[j + h][i + k]) {
                                    ok = false;
                                    break;
                                }
                            }
                            if (!ok) break;
                        }

                        for (int dy = 0; dy < h; dy++) {
                            for (int dx = 0; dx < w; dx++) {
                                visited[j + dy][i + dx] = true;
                            }
                        }

                        float[] v1 = new float[3];
                        float[] v2 = new float[3];
                        float[] v3 = new float[3];
                        float[] v4 = new float[3];

                        int[] du = new int[3]; du[u] = h;
                        int[] dv = new int[3]; dv[v] = w;

                        int[] pos = new int[3];
                        pos[d] = x[d];
                        pos[u] = j;
                        pos[v] = i;

                        float mx = chunkX * SIZE_X;
                        float mz = chunkZ * SIZE_Z;

                        v1[0] = mx + pos[0];                     v1[1] = pos[1];                     v1[2] = mz + pos[2];
                        v2[0] = mx + pos[0] + du[0];             v2[1] = pos[1] + du[1];             v2[2] = mz + pos[2] + du[2];
                        v3[0] = mx + pos[0] + du[0] + dv[0];     v3[1] = pos[1] + du[1] + dv[1];     v3[2] = mz + pos[2] + du[2] + dv[2];
                        v4[0] = mx + pos[0] + dv[0];             v4[1] = pos[1] + dv[1];             v4[2] = mz + pos[2] + dv[2];

                        float[] verts;
                        if (isBack) {
                            verts = new float[]{ v1[0], v1[1], v1[2], v4[0], v4[1], v4[2], v3[0], v3[1], v3[2], v2[0], v2[1], v2[2] };
                        } else {
                            verts = new float[]{ v1[0], v1[1], v1[2], v2[0], v2[1], v2[2], v3[0], v3[1], v3[2], v4[0], v4[1], v4[2] };
                        }

                        float nx_val = q[0] * (isBack ? -1 : 1);
                        float ny_val = q[1] * (isBack ? -1 : 1);
                        float nz_val = q[2] * (isBack ? -1 : 1);
                        float[] norms = new float[12];
                        for (int k = 0; k < 4; k++) {
                            norms[k * 3] = nx_val;
                            norms[k * 3 + 1] = ny_val;
                            norms[k * 3 + 2] = nz_val;
                        }

                        float xMin = Math.min(Math.min(v1[0], v2[0]), Math.min(v3[0], v4[0]));
                        float yMin = Math.min(Math.min(v1[1], v2[1]), Math.min(v3[1], v4[1]));
                        float zMin = Math.min(Math.min(v1[2], v2[2]), Math.min(v3[2], v4[2]));

                        float xMax = Math.max(Math.max(v1[0], v2[0]), Math.max(v3[0], v4[0]));
                        float zMax = Math.max(Math.max(v1[2], v2[2]), Math.max(v3[2], v4[2]));

                        float[] uvs = new float[8];
                        for (int k = 0; k < 4; k++) {
                            float vx = verts[k * 3];
                            float vy = verts[k * 3 + 1];
                            float vz = verts[k * 3 + 2];

                            float uCoord = 0, vCoord = 0;
                            if (d == 0) {
                                uCoord = isBack ? (vz - zMin) : (zMax - vz);
                                vCoord = vy - yMin;
                            } else if (d == 1) {
                                uCoord = vx - xMin;
                                vCoord = isBack ? (vz - zMin) : (zMax - vz);
                            } else if (d == 2) {
                                uCoord = isBack ? (xMax - vx) : (vx - xMin);
                                vCoord = vy - yMin;
                            }
                            uvs[k * 2] = uCoord;
                            uvs[k * 2 + 1] = vCoord;
                        }

                        int targetType = type;
                        if (d == 1) {
                            if (type == 1) targetType = isBack ? 2 : 17;
                            else if (type == 23) targetType = isBack ? 2 : 25;
                            else if (type == 6) targetType = 18;
                            else if (type == 20) targetType = 26;
                        }

                        builders[targetType].addFace(verts, norms, uvs);
                    }
                }
            }
        }

        for (int i = 1; i <= 30; i++) {
            prebuiltMeshes[i] = builders[i].build();
        }
    }

    public final void applyPrebuiltMeshes(Material[] blockMaterials) {
        chunkNode.detachAllChildren();
        for (int i = 1; i <= 30; i++) {
            Mesh mesh = prebuiltMeshes[i];
            if (mesh != null) {
                Geometry geom = new Geometry("BlockMesh_" + i, mesh);
                geom.setMaterial(blockMaterials[i]);
                if (i == 27) {
                    geom.setQueueBucket(RenderQueue.Bucket.Transparent);
                } else {
                    geom.setQueueBucket(RenderQueue.Bucket.Opaque);
                }
                chunkNode.attachChild(geom);
            }
        }
    }

    // ОПТИМИЗАЦИЯ: дальние чанки не отбрасывают тень
    // (CastAndReceive -> Receive), иначе весь радиус рисуется
    // в shadow map каждый кадр — главный CPU/FPS-пожиратель
    // при путешествии. Визуально дальние тени не видны.
    public final void setShadowCast(boolean cast) {
        chunkNode.setShadowMode(cast
                ? RenderQueue.ShadowMode.CastAndReceive
                : RenderQueue.ShadowMode.Receive);
    }

    public byte getBlock(int x, int y, int z) {
        if (x < 0 || x >= SIZE_X || y < 0 || y >= SIZE_Y || z < 0 || z >= SIZE_Z) return 0;
        return blocks[x][y][z];
    }

    public void setBlock(int x, int y, int z, byte type) {
        if (x >= 0 && x < SIZE_X && y >= 0 && y < SIZE_Y && z >= 0 && z < SIZE_Z) {
            blocks[x][y][z] = type;
            if (type == 27 || type == 28) {
                hasFluids = true;
            } else if (type == 8) {
                hasFallingBlocks = true;
            }
        }
    }

    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    public Node getNode() { return chunkNode; }

    private static class FloatArrayList {
        private float[] data = new float[1024];
        private int size = 0;

        public void add(float value) {
            if (size == data.length) {
                float[] newData = new float[data.length * 2];
                System.arraycopy(data, 0, newData, 0, size);
                data = newData;
            }
            data[size++] = value;
        }

        public boolean isEmpty() { return size == 0; }

        public float[] toArray() {
            float[] result = new float[size];
            System.arraycopy(data, 0, result, 0, size);
            return result;
        }
    }

    private static class IntArrayList {
        private int[] data = new int[512];
        private int size = 0;

        public void add(int value) {
            if (size == data.length) {
                int[] newData = new int[data.length * 2];
                System.arraycopy(data, 0, newData, 0, size);
                data = newData;
            }
            data[size++] = value;
        }

        public int[] toArray() {
            int[] result = new int[size];
            System.arraycopy(data, 0, result, 0, size);
            return result;
        }
    }

    private static class MeshBuilder {
        private final FloatArrayList positions = new FloatArrayList();
        private final FloatArrayList normals = new FloatArrayList();
        private final FloatArrayList uvs = new FloatArrayList();
        private final IntArrayList indices = new IntArrayList();
        private int vertexCount = 0;

        public void addFace(float[] faceVerts, float[] faceNormals, float[] faceUVs) {
            for (float v : faceVerts) positions.add(v);
            for (float n : faceNormals) normals.add(n);
            for (float u : faceUVs) uvs.add(u);

            indices.add(vertexCount);
            indices.add(vertexCount + 1);
            indices.add(vertexCount + 2);

            indices.add(vertexCount);
            indices.add(vertexCount + 2);
            indices.add(vertexCount + 3);

            vertexCount += 4;
        }

        public Mesh build() {
            if (positions.isEmpty()) return null;

            Mesh mesh = new Mesh();
            mesh.setBuffer(VertexBuffer.Type.Position, 3, positions.toArray());
            mesh.setBuffer(VertexBuffer.Type.Normal, 3, normals.toArray());
            mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, uvs.toArray());
            mesh.setBuffer(VertexBuffer.Type.Index, 1, indices.toArray());

            mesh.updateBound();
            mesh.setStatic();

            return mesh;
        }
    }

    public static class PerlinNoise {
        private static final int[] p = new int[512];

        static {
            for (int i = 0; i < 256; i++) {
                p[i] = i;
            }

            java.util.Random rand = new java.util.Random(12345);
            for (int i = 255; i > 0; i--) {
                int j = rand.nextInt(i + 1);
                int temp = p[i];
                p[i] = p[j];
                p[j] = temp;
            }

            for (int i = 0; i < 256; i++) {
                p[256 + i] = p[i];
            }
        }

        public static double noise(double x, double y) {
            int X = (int) Math.floor(x) & 255;
            int Y = (int) Math.floor(y) & 255;
            x -= Math.floor(x);
            y -= Math.floor(y);
            double u = fade(x);
            double v = fade(y);
            int A = p[X]+Y, B = p[X+1]+Y;
            return lerp(v, lerp(u, grad(p[A], x, y), grad(p[B], x-1, y)),
                           lerp(u, grad(p[A+1], x, y-1), grad(p[B+1], x-1, y-1)));
        }

        public static double noise(double x, double y, double z) {
            int X = (int) Math.floor(x) & 255;
            int Y = (int) Math.floor(y) & 255;
            int Z = (int) Math.floor(z) & 255;

            x -= Math.floor(x);
            y -= Math.floor(y);
            z -= Math.floor(z);

            double u = fade(x);
            double v = fade(y);
            double w = fade(z);

            int A  = (p[X] + Y) & 255;
            int AA = (p[A] + Z) & 255;
            int AB = (p[(A + 1) & 255] + Z) & 255;

            int B  = (p[(X + 1) & 255] + Y) & 255;
            int BA = (p[B] + Z) & 255;
            int BB = (p[(B + 1) & 255] + Z) & 255;

            return lerp(w, lerp(v, lerp(u, grad(p[AA], x, y, z),
                                           grad(p[BA], x-1, y, z)),
                                   lerp(u, grad(p[AB], x, y-1, z),
                                           grad(p[BB], x-1, y-1, z))),
                           lerp(v, lerp(u, grad(p[(AA + 1) & 255], x, y, z-1),
                                           grad(p[(BA + 1) & 255], x-1, y, z-1)),
                                   lerp(u, grad(p[(AB + 1) & 255], x, y-1, z-1),
                                           grad(p[(BB + 1) & 255], x-1, y-1, z-1))));
        }

        private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
        private static double lerp(double t, double a, double b) { return a + t * (b - a); }

        private static double grad(int hash, double x, double y) {
            int h = hash & 7;
            double u = h < 4 ? x : y;
            double v = h < 4 ? y : x;
            return ((h&1) == 0 ? u : -u) + ((h&2) == 0 ? 2.0*v : -2.0*v);
        }
        private static double grad(int hash, double x, double y, double z) {
            int h = hash & 15;
            double u = h < 8 ? x : y;
            double v = h < 4 ? y : h == 12 || h == 14 ? x : z;
            return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
        }
    }
}
