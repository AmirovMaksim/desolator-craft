package com.mygame;

import java.util.HashSet;
import java.util.Set;
import com.jme3.math.Vector3f;

public class VillageManager {

    private final World world;
    private final Main app;
    private final Set<Long> builtChunks = new HashSet<Long>();

    private static final byte WALL = 6;
    private static final byte ROOF = 15;
    private static final byte FLOOR = 2;
    private static final byte ROAD = 5;
    private static final byte FENCE = 18;
    private static final byte CROP = 9;
    private static final byte LEAF = 9;
    private static final byte LOG = 18;
    private static final byte BANNER = 24;
    private static final byte TORCH = 33;

    // СТРОГО ТОЛЬКО равнины (как просил пользователь)
    private static final java.util.Set<Integer> VILLAGE_BIOMES = new java.util.HashSet<Integer>();
    static {
        VILLAGE_BIOMES.add(0); // PLAINS
    }

    public VillageManager(World world, Main app) {
        this.world = world;
        this.app = app;
    }

    // редкая решётка 16x16, ~2%
    private boolean shouldBuild(int cx, int cz) {
        int gx = cx / 16;
        int gz = cz / 16;
        long h = ((long) gx * 73856093L) ^ ((long) gz * 19349663L);
        h = (h ^ (h >>> 13)) * 1274126177L;
        h = h ^ (h >>> 16);
        return (Math.abs(h) % 100) < 2;
    }

    public void onChunkLoaded(int cx, int cz) {
        Long key = ((long) cx << 32) | ((long) cz & 0xFFFFFFFFL);
        if (builtChunks.contains(key)) return;
        builtChunks.add(key);

        int biome = Chunk.getBiomeAt(cx * Chunk.SIZE_X + 8, cz * Chunk.SIZE_Z + 8, world);
        if (!VILLAGE_BIOMES.contains(biome)) return;
        if (!shouldBuild(cx, cz)) return;

        int x0 = cx * Chunk.SIZE_X;
        int z0 = cz * Chunk.SIZE_Z;
        int x1 = x0 + Chunk.SIZE_X - 1;
        int z1 = z0 + Chunk.SIZE_Z - 1;
        int baseX = x0 + 8;
        int baseZ = z0 + 8;

        int houses = 3 + (int) (Math.random() * 3);
        for (int i = 0; i < houses; i++) {
            double ang = Math.random() * Math.PI * 2;
            int dist = 2 + (int) (Math.random() * 5);
            int hx = clamp(baseX + (int) (Math.cos(ang) * dist), x0 + 3, x1 - 8);
            int hz = clamp(baseZ + (int) (Math.sin(ang) * dist), z0 + 3, z1 - 8);
            int hy = world.getMaxHeightAt(hx, hz);
            int type = (int) (Math.random() * 3);
            buildHouse(type, hx, hy, hz, x0, z0, x1, z1);
            if (app.mobManager != null) {
                app.mobManager.spawnMob(10, app.getAssetManagerSafe(),
                        new Vector3f(hx + 1.0f, hy + 1.0f, hz + 1.0f));
            }
        }

        if (Math.random() < 0.6f) buildWell(baseX, baseZ, x0, z0, x1, z1);
        buildFarm(baseX + 2, baseZ + 2, x0, z0, x1, z1);
        buildTree(baseX - 4, baseZ - 3, x0, z0, x1, z1);
        buildTorch(baseX + 3, baseZ - 2, x0, z0, x1, z1);
        buildBanner(baseX, baseZ, x0, z0, x1, z1);

        world.rebuildChunkNow(cx, cz);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void set(int x, int y, int z, byte b, int x0, int z0, int x1, int z1) {
        if (x < x0 || x > x1 || z < z0 || z > z1) return;
        if (y < 1 || y >= Chunk.SIZE_Y) return;
        world.setBlockAtSafe(x, y, z, b);
    }

    private void buildHouse(int type, int x, int y, int z, int x0, int z0, int x1, int z1) {
        int w, d, h;
        if (type == 0) { w = 5; d = 5; h = 4; }
        else if (type == 1) { w = 7; d = 5; h = 3; }
        else { w = 4; d = 4; h = 5; }
        int baseY = y;
        for (int dx = -1; dx <= w; dx++) {
            for (int dz = -1; dz <= d; dz++) {
                int px = clamp(x + dx, x0, x1);
                int pz = clamp(z + dz, z0, z1);
                baseY = Math.max(baseY, world.getMaxHeightAt(px, pz));
            }
        }
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < d; dz++) {
                for (int dy = 1; dy <= h; dy++) {
                    boolean edge = (dx == 0 || dx == w - 1 || dz == 0 || dz == d - 1);
                    int by = baseY + dy;
                    if (dy == 1) set(x + dx, by, z + dz, FLOOR, x0, z0, x1, z1);
                    else if (edge) set(x + dx, by, z + dz, WALL, x0, z0, x1, z1);
                }
            }
        }
        int roofBase = baseY + h + 1;
        if (type == 2) {
            int[][] layers = { {2, 2}, {1, 1}, {0, 0} };
            for (int li = 0; li < layers.length; li++) {
                int off = layers[li][0];
                for (int dx = -off; dx <= w - 1 + off; dx++)
                    for (int dz = -off; dz <= d - 1 + off; dz++)
                        set(x + dx, roofBase + li, z + dz, ROOF, x0, z0, x1, z1);
            }
        } else {
            for (int dx = -1; dx <= w; dx++)
                for (int dz = -1; dz <= d; dz++)
                    set(x + dx, roofBase, z + dz, ROOF, x0, z0, x1, z1);
            for (int dx = 0; dx <= w - 1; dx++)
                for (int dz = 0; dz <= d - 1; dz++)
                    set(x + dx, roofBase + 1, z + dz, ROOF, x0, z0, x1, z1);
        }
        set(x + (w / 2), baseY + 1, z, (byte) 0, x0, z0, x1, z1);
        set(x + (w / 2), baseY + 2, z, (byte) 0, x0, z0, x1, z1);
    }

    private void buildWell(int x, int z, int x0, int z0, int x1, int z1) {
        int py = world.getMaxHeightAt(x, z);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) set(x, py + 1, z, (byte) 27, x0, z0, x1, z1);
                else set(x + dx, py + 1, z + dz, FENCE, x0, z0, x1, z1);
            }
        }
        set(x, py + 2, z, FENCE, x0, z0, x1, z1);
    }

    private void buildFarm(int x, int z, int x0, int z0, int x1, int z1) {
        int w = 7, d = 7;
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < d; dz++) {
                int px = x + dx, pz = z + dz;
                int py = world.getMaxHeightAt(px, pz);
                boolean edge = (dx == 0 || dx == w - 1 || dz == 0 || dz == d - 1);
                if (edge) {
                    if ((dx + dz) % 2 == 0) set(px, py + 1, pz, FENCE, x0, z0, x1, z1);
                } else if ((dx + dz) % 2 == 0) {
                    set(px, py + 1, pz, CROP, x0, z0, x1, z1);
                } else {
                    set(px, py + 1, pz, FLOOR, x0, z0, x1, z1);
                }
            }
        }
    }

    private void buildTree(int x, int z, int x0, int z0, int x1, int z1) {
        int py = world.getMaxHeightAt(x, z);
        int th = 4;
        for (int dy = 1; dy <= th; dy++) set(x, py + dy, z, LOG, x0, z0, x1, z1);
        int top = py + th + 1;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                    set(x + dx, top + dy, z + dz, LEAF, x0, z0, x1, z1);
                }
            }
        }
    }

    private void buildTorch(int x, int z, int x0, int z0, int x1, int z1) {
        int py = world.getMaxHeightAt(x, z);
        set(x, py + 1, z, FENCE, x0, z0, x1, z1);
        set(x, py + 2, z, TORCH, x0, z0, x1, z1);
    }

    private void buildBanner(int x, int z, int x0, int z0, int x1, int z1) {
        int py = world.getMaxHeightAt(x, z);
        set(x, py + 1, z, FENCE, x0, z0, x1, z1);
        set(x, py + 2, z, FENCE, x0, z0, x1, z1);
        set(x, py + 3, z, BANNER, x0, z0, x1, z1);
    }
}
