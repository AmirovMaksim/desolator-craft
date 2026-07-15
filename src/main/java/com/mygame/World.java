package com.mygame;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.jme3.material.Material;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

public class World {

    private final Material[] blockMaterials;
    private final Node rootNode;
    private final String saveFileName; // Исправлено: имя переменной соответствует методам
    private final Main app;

    private final Map<String, Chunk> loadedChunks = new ConcurrentHashMap<>();
    private final Set<String> activeGenerationTasks = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingRebuildTasks = ConcurrentHashMap.newKeySet();
    // ОПТИМИЗАЦИЯ: лимит одновременных генераций чанков.
    // Без лимита при путешествии разом сабмитится весь радиус
    // (до ~280 задач) -> просадка FPS. Генерим пачками.
    private static final int MAX_GEN_TASKS = 6;

    // Хранение инвентарей сундуков: "x,y,z" -> массив слотов
    public final Map<String, Player.InventorySlot[]> chestInventories = new ConcurrentHashMap<>();

    // ФАКЕЛЫ: координаты -> точечный источник света (реальное освещение)
    private final Map<String, com.jme3.light.PointLight> activeTorches = new ConcurrentHashMap<>();

    // Хранение изменённых пользователем блоков: ChunkKey -> (BlockKey -> BlockType)
    private final Map<Long, Map<Integer, Byte>> chunkModifications = new ConcurrentHashMap<>();
    private final LongArray chunkKeyCache = new LongArray();
    private static final class LongArray {
        long[] data = new long[512]; int size = 0;
        long get(int i){return data[i];}
        void add(long v){if(size==data.length){long[] n=new long[data.length*2];System.arraycopy(data,0,n,0,size);data=n;}data[size++]=v;}
        int size(){return size;}
        void clear(){size=0;}
    }

    private long worldSeed;
    private double seedOffsetX;
    private double seedOffsetZ;
    private boolean isCreative = true;
    private boolean isFlatWorld = false;
    private int playerClass = 0; // RPG класс (0 воин, 1 маг, 2 лучник)

    private int activeRenderDistance = -1;
    private float fluidAccumulator = 0.0f;

    private final ExecutorService generationExecutor = Executors.newFixedThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors() - 1),
        new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "ChunkGenPool-" + counter.getAndIncrement());
                t.setPriority(Thread.MIN_PRIORITY + 1);
                return t;
            }
        }
    );

    private final java.util.concurrent.ConcurrentLinkedQueue<Chunk> finishedChunksQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();


    public static class WorldSaveData {
        public long seed = -1;
        public boolean creative = true;
        public boolean flat = false;
        public Vector3f playerPos = new Vector3f(0, 100, 0);
        public byte[] hotbar = new byte[9];
        public int[] hotbarCounts = new int[9];
        public int selectedSlot = 0;
        public boolean success = false;
    }

    public World(Material[] blockMaterials, Node rootNode, String saveFileName, Main app) {
        this.blockMaterials = blockMaterials;
        this.rootNode = rootNode;
        this.saveFileName = saveFileName;
        this.app = app;
    }

    public void initializeSeed(long seed) {
        this.worldSeed = seed;
        java.util.Random r = new java.util.Random(seed);
        this.seedOffsetX = r.nextDouble() * 100000.0 - 50000.0;
        this.seedOffsetZ = r.nextDouble() * 100000.0 - 50000.0;
    }

    public double getSeedOffsetX() { return seedOffsetX; }
    public double getSeedOffsetZ() { return seedOffsetZ; }
    public long getWorldSeed() { return worldSeed; }
    public boolean isCreative() { return isCreative; }
    public void setCreative(boolean creative) { this.isCreative = creative; }
    public boolean isFlatWorld() { return isFlatWorld; }
    public void setFlatWorld(boolean flat) { this.isFlatWorld = flat; }
    public void setPlayerClass(int c) { this.playerClass = c; }
    public int getPlayerClass() { return playerClass; }
    public int getLoadedChunksCount() { return loadedChunks.size(); }
    public int getCachedChunksCount() { return 0; } 
    public int getActiveTasksCount() { return activeGenerationTasks.size(); }
    public boolean isChunkLoaded(int cx, int cz) { return loadedChunks.containsKey(cx + "," + cz); }

    /** Загружены ли ВСЕ чанки в радиусе radius вокруг (cx,cz)? */
    public boolean isAreaLoaded(int cx, int cz, int radius) {
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                if (!loadedChunks.containsKey(x + "," + z)) return false;
            }
        }
        return true;
    }

    /** Явно заказать генерацию чанков в радиусе radius вокруг (cx,cz)
     *  (используется при старте мира, чтобы игрок не провалился). */
    public void requestLoadAround(int cx, int cz, int radius) {
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                String key = x + "," + z;
                if (!loadedChunks.containsKey(key) && !activeGenerationTasks.contains(key)) {
                    activeGenerationTasks.add(key);
                    generationExecutor.submit(new WorldTask(x, z, new Vector3f(cx * Chunk.SIZE_X, 64, cz * Chunk.SIZE_Z)));
                }
            }
        }
    }

    private static long getChunkKey(int x, int z) {
        int cx = Math.floorDiv(x, Chunk.SIZE_X);
        int cz = Math.floorDiv(z, Chunk.SIZE_Z);
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private static int getBlockKey(int x, int y, int z) {
        int bx = Math.floorMod(x, Chunk.SIZE_X);
        int bz = Math.floorMod(z, Chunk.SIZE_Z);
        return (bx << 12) | (y << 4) | bz;
    }

    private class WorldTask implements Runnable {
        final int taskType; // 0 = Генерация, 1 = Пересборка
        final int cx, cz;
        final Chunk chunk;
        final Vector3f playerPos;

        public WorldTask(int cx, int cz, Vector3f playerPos) {
            this.taskType = 0;
            this.cx = cx;
            this.cz = cz;
            this.chunk = null;
            this.playerPos = playerPos.clone();
        }

        public WorldTask(Chunk chunk, Vector3f playerPos) {
            this.taskType = 1;
            this.cx = chunk.getChunkX();
            this.cz = chunk.getChunkZ();
            this.chunk = chunk;
            this.playerPos = playerPos.clone();
        }

        @Override
        public void run() {
            String key = cx + "," + cz;
            try {
                if (taskType == 0) {
                    int playerCX = (int) Math.floor(playerPos.x / Chunk.SIZE_X);
                    int playerCZ = (int) Math.floor(playerPos.z / Chunk.SIZE_Z);
                    if (Math.abs(cx - playerCX) > GameSettings.renderDistance + 1 || 
                        Math.abs(cz - playerCZ) > GameSettings.renderDistance + 1) {
                        activeGenerationTasks.remove(key);
                        return;
                    }
                    Chunk newChunk = new Chunk(cx, cz, blockMaterials, World.this);
                    newChunk.prebuildMeshes();
                    finishedChunksQueue.add(newChunk);
                } else {
                    if (chunk != null) {
                        chunk.prebuildMeshes();
                        app.enqueue(() -> {
                            if (loadedChunks.containsKey(key)) {
                                chunk.applyPrebuiltMeshes(blockMaterials);
                            }
                            pendingRebuildTasks.remove(key);
                        });
                    }
                }
            } catch (Throwable ex) {
                // КРИТИЧНО: если задача упала, обязательно освобождаем слот,
                // иначе key навсегда занимает лимит MAX_GEN_TASKS и генерация
                // чанков полностью встаёт ("Bg Tasks" застревает на максимуме).
                activeGenerationTasks.remove(key);
                pendingRebuildTasks.remove(key);
                System.err.println("[ChunkGen] Задача (" + key + ") упала: " + ex);
                ex.printStackTrace();
            }
        }
    }

    public void loadSpawnChunk(Material[] blockMaterials) {
        String key = "0,0";
        if (!loadedChunks.containsKey(key)) {
            Chunk spawnChunk = new Chunk(0, 0, blockMaterials, this);
            spawnChunk.prebuildMeshes();
            spawnChunk.applyPrebuiltMeshes(blockMaterials);
            rootNode.attachChild(spawnChunk.getNode());
            loadedChunks.put(key, spawnChunk);
        }
    }

    public void update(Vector3f playerPos, float tpf) {
        fluidAccumulator += tpf;
        if (fluidAccumulator >= GameSettings.fluidTickInterval) {
            tickFluids();
            tickBlockPhysics(); 
            fluidAccumulator = 0.0f;
        }
        update(playerPos);
    }

    public void update(Vector3f playerPos) {
        int totalLoaded = finishedChunksQueue.size();
        for (int i = 0; i < Math.min(totalLoaded, 6); i++) {
            Chunk chunk = finishedChunksQueue.poll();
            if (chunk != null) {
                String key = chunk.getChunkX() + "," + chunk.getChunkZ();
                chunk.applyPrebuiltMeshes(blockMaterials);
                rootNode.attachChild(chunk.getNode());
                loadedChunks.put(key, chunk);
                activeGenerationTasks.remove(key);
                // ОПТИМИЗАЦИЯ: дальние чанки не отбрасывают тень
                // (только получают) -> shadow pass грузит лишь ближнюю округу.
                int pCX = (int) Math.floor(playerPos.x / Chunk.SIZE_X);
                int pCZ = (int) Math.floor(playerPos.z / Chunk.SIZE_Z);
                int dist = Math.max(Math.abs(chunk.getChunkX() - pCX),
                                   Math.abs(chunk.getChunkZ() - pCZ));
                chunk.setShadowCast(dist <= GameSettings.renderDistance / 2);
                // ОПТИМИЗАЦИЯ: НЕ перестраиваем соседей при каждой
                // загрузке чанка — иначе на каждый новый чанк
                // (до 5 тяжёлых rebuild'ов) FPS падает при путешествии.
                // Соседи генерируются независимо; стыковка граней
                // при копании обеспечивается setBlockAt -> rebuildNeighborChunks.
                // rebuildNeighborChunks(chunk.getChunkX(), chunk.getChunkZ(), playerPos);
            }
        }

        int playerChunkX = (int) Math.floor(playerPos.x / Chunk.SIZE_X);
        int playerChunkZ = (int) Math.floor(playerPos.z / Chunk.SIZE_Z);
        int renderDistance = GameSettings.renderDistance;

        if (renderDistance != activeRenderDistance) {
            activeRenderDistance = renderDistance;
        }

        loadedChunks.entrySet().removeIf(entry -> {
            Chunk chunk = entry.getValue();
            int distX = Math.abs(chunk.getChunkX() - playerChunkX);
            int distZ = Math.abs(chunk.getChunkZ() - playerChunkZ);
            if (distX > renderDistance + 1 || distZ > renderDistance + 1) {
                rootNode.detachChild(chunk.getNode());
                return true;
            }
            return false;
        });

        for (int x = playerChunkX - renderDistance; x <= playerChunkX + renderDistance; x++) {
            for (int z = playerChunkZ - renderDistance; z <= playerChunkZ + renderDistance; z++) {
                String key = x + "," + z;
                if (!loadedChunks.containsKey(key) && !activeGenerationTasks.contains(key)) {
                    // ОПТИМИЗАЦИЯ: не сабмитим больше MAX_GEN_TASKS
                    // одновременно — иначе при быстром путешествии
                    // разом улетает весь радиус и FPS падает.
                    if (activeGenerationTasks.size() >= MAX_GEN_TASKS) continue;
                    activeGenerationTasks.add(key);
                    generationExecutor.submit(new WorldTask(x, z, playerPos));
                }
            }
        }
    }

    private void rebuildNeighborChunks(int cx, int cz, Vector3f playerPos) {
        int[][] dirs = { {-1, 0}, {1, 0}, {0, -1}, {0, 1} };
        for (int[] d : dirs) {
            int nx = cx + d[0];
            int nz = cz + d[1];
            String neighborKey = nx + "," + nz;
            Chunk neighbor = loadedChunks.get(neighborKey);
            if (neighbor != null) {
                generationExecutor.submit(new WorldTask(neighbor, playerPos));
            }
        }
    }

    public void tickFluids() {
        Vector3f playerPos = playerPosFromMain();
        int pCX = (int) Math.floor(playerPos.x / Chunk.SIZE_X);
        int pCZ = (int) Math.floor(playerPos.z / Chunk.SIZE_Z);
        
        List<int[]> flowUpdates = new ArrayList<>();
        int range = 2; 

        for (int cx = pCX - range; cx <= pCX + range; cx++) {
            for (int cz = pCZ - range; cz <= pCZ + range; cz++) {
                String chunkKey = cx + "," + cz;
                Chunk chunk = loadedChunks.get(chunkKey);
                if (chunk == null || !chunk.hasFluids()) continue; // ОПТИМИЗАЦИЯ: пропуск "сухих" чанков

                for (int x = 0; x < Chunk.SIZE_X; x++) {
                    for (int z = 0; z < Chunk.SIZE_Z; z++) {
                        for (int y = 10; y < 85; y++) { 
                            byte type = chunk.getBlockDirect(x, y, z);
                            if (type == 27 || type == 28) { 
                                int wx = cx * Chunk.SIZE_X + x;
                                int wz = cz * Chunk.SIZE_Z + z;
                                
                                if (y > 0) {
                                    byte under = getBlockAtThreadSafe(wx, y - 1, wz);
                                    if (under == 0) {
                                        flowUpdates.add(new int[]{wx, y - 1, wz, type});
                                        continue; 
                                    }
                                }

                                int[][] dirs = {{1,0}, {-1,0}, {0,1}, {0,-1}};
                                for (int[] d : dirs) {
                                    int nx = wx + d[0];
                                    int nz = wz + d[1];
                                    byte target = getBlockAtThreadSafe(nx, y, nz);
                                    if (target == 0) {
                                        flowUpdates.add(new int[]{nx, y, nz, type});
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        for (int[] update : flowUpdates) {
            setBlockAtFromFluid(update[0], update[1], update[2], (byte) update[3]);
        }
    }

    public void tickBlockPhysics() {
        Vector3f playerPos = playerPosFromMain();
        int pCX = (int) Math.floor(playerPos.x / Chunk.SIZE_X);
        int pCZ = (int) Math.floor(playerPos.z / Chunk.SIZE_Z);
        
        List<int[]> fallingBlocks = new ArrayList<>();
        int range = 2;

        for (int cx = pCX - range; cx <= pCX + range; cx++) {
            for (int cz = pCZ - range; cz <= pCZ + range; cz++) {
                String chunkKey = cx + "," + cz;
                Chunk chunk = loadedChunks.get(chunkKey);
                if (chunk == null || !chunk.hasFallingBlocks()) continue; // ОПТИМИЗАЦИЯ: пропуск чанков без песка

                for (int x = 0; x < Chunk.SIZE_X; x++) {
                    for (int z = 0; z < Chunk.SIZE_Z; z++) {
                        for (int y = 1; y < Chunk.SIZE_Y - 1; y++) {
                            byte type = chunk.getBlockDirect(x, y, z);
                            if (type == 8) { 
                                int wx = cx * Chunk.SIZE_X + x;
                                int wz = cz * Chunk.SIZE_Z + z;
                                
                                byte under = getBlockAtThreadSafe(wx, y - 1, wz);
                                if (under == 0 || under == 27 || under == 28) {
                                    fallingBlocks.add(new int[]{wx, y, wz, wx, y - 1, wz, type});
                                }
                            }
                        }
                    }
                }
            }
        }

        for (int[] move : fallingBlocks) {
            setBlockAt(move[0], move[1], move[2], (byte) 0);
            setBlockAt(move[3], move[4], move[5], (byte) move[6]);
        }
    }

    public byte getBlockAt(int x, int y, int z) {
        if (y < 0 || y >= Chunk.SIZE_Y) return 0;
        
        long cKey = getChunkKey(x, z);
        Map<Integer, Byte> mods = chunkModifications.get(cKey);
        if (mods != null) {
            int bKey = getBlockKey(x, y, z);
            Byte modType = mods.get(bKey);
            if (modType != null) return modType;
        }

        int cx = Math.floorDiv(x, Chunk.SIZE_X);
        int cz = Math.floorDiv(z, Chunk.SIZE_Z);
        int bx = Math.floorMod(x, Chunk.SIZE_X);
        int bz = Math.floorMod(z, Chunk.SIZE_Z);

        Chunk chunk = loadedChunks.get(cx + "," + cz);
        if (chunk == null) return 0; 

        return chunk.getBlock(bx, y, bz);
    }

    public byte getBlockAtThreadSafe(int x, int y, int z) {
        if (y < 0 || y >= Chunk.SIZE_Y) return 0;

        long cKey = getChunkKey(x, z);
        Map<Integer, Byte> mods = chunkModifications.get(cKey);
        if (mods != null) {
            int bKey = getBlockKey(x, y, z);
            Byte modType = mods.get(bKey);
            if (modType != null) return modType;
        }

        int cx = Math.floorDiv(x, Chunk.SIZE_X);
        int cz = Math.floorDiv(z, Chunk.SIZE_Z);
        int bx = Math.floorMod(x, Chunk.SIZE_X);
        int bz = Math.floorMod(z, Chunk.SIZE_Z);

        Chunk chunk = loadedChunks.get(cx + "," + cz);
        if (chunk == null) return 0; 

        return chunk.getBlockDirect(bx, y, bz);
    }

    /** Bulk-запись блока БЕЗ пересборки геометрии.
     *  Пишет напрямую в массив чанка; ребилд делается отдельно rebuildChunkNow().
     *  БЕЗ этого каждый блок деревни вызывал бы полный prebuildMeshes чанка -> зависание. */
    public void setBlockAtSafe(int x, int y, int z, byte type) {
        if (y < 0 || y >= Chunk.SIZE_Y) return;
        int cx = Math.floorDiv(x, Chunk.SIZE_X);
        int cz = Math.floorDiv(z, Chunk.SIZE_Z);
        Chunk c = loadedChunks.get(cx + "," + cz);
        if (c == null) return;
        int bx = Math.floorMod(x, Chunk.SIZE_X);
        int bz = Math.floorMod(z, Chunk.SIZE_Z);
        c.setBlock(bx, y, bz, type);
    }

    /** Однократная пересборка геометрии чанка (после bulk-записи блоков). */
    public void rebuildChunkNow(int cx, int cz) {
        Chunk c = loadedChunks.get(cx + "," + cz);
        if (c != null) {
            c.prebuildMeshes();
            c.applyPrebuiltMeshes(blockMaterials);
            c.getNode().updateModelBound();
        }
    }

    public void setBlockAt(int x, int y, int z, byte type) {
        long cKey = getChunkKey(x, z);
        int bKey = getBlockKey(x, y, z);

        chunkModifications.computeIfAbsent(cKey, k -> new ConcurrentHashMap<>()).put(bKey, type);

        int cx = Math.floorDiv(x, Chunk.SIZE_X);
        int cz = Math.floorDiv(z, Chunk.SIZE_Z);
        int bx = Math.floorMod(x, Chunk.SIZE_X);
        int bz = Math.floorMod(z, Chunk.SIZE_Z);

        if (type == 0) {
            chestInventories.remove(x + "," + y + "," + z);
            removeTorch(x, y, z);
        } else if (type == 33) {
            addTorch(x, y, z);
        } else {
            removeTorch(x, y, z); // любой другой блок гасит факел на этой клетке
        }

        Chunk chunk = loadedChunks.get(cx + "," + cz);
        if (chunk != null) {
            chunk.setBlock(bx, y, bz, type);
            chunk.prebuildMeshes(); 
            chunk.applyPrebuiltMeshes(blockMaterials); 
            chunk.getNode().updateModelBound(); // ОПТИМИЗАЦИЯ: пересчёт bound только у изменённого чанка, а не всей сцены
            rebuildNeighborChunks(cx, cz, playerPosFromMain());
        }
    }

    // --- ФАКЕЛЫ: точечный свет, который реально освещает блоки вокруг ---
    private void addTorch(int x, int y, int z) {
        String key = x + "," + y + "," + z;
        if (activeTorches.containsKey(key)) return;
        com.jme3.light.PointLight torch = new com.jme3.light.PointLight();
        torch.setColor(new com.jme3.math.ColorRGBA(1.0f, 0.75f, 0.35f, 1.0f));
        torch.setRadius(11.0f);
        torch.setPosition(new com.jme3.math.Vector3f(x + 0.5f, y + 0.6f, z + 0.5f));
        rootNode.addLight(torch);
        activeTorches.put(key, torch);
    }

    private void removeTorch(int x, int y, int z) {
        String key = x + "," + y + "," + z;
        com.jme3.light.PointLight torch = activeTorches.remove(key);
        if (torch != null) rootNode.removeLight(torch);
    }

    public void setBlockAtFromFluid(int x, int y, int z, byte type) {
        long cKey = getChunkKey(x, z);
        int bKey = getBlockKey(x, y, z);

        chunkModifications.computeIfAbsent(cKey, k -> new ConcurrentHashMap<>()).put(bKey, type);

        int cx = Math.floorDiv(x, Chunk.SIZE_X);
        int cz = Math.floorDiv(z, Chunk.SIZE_Z);
        int bx = Math.floorMod(x, Chunk.SIZE_X);
        int bz = Math.floorMod(z, Chunk.SIZE_Z);

        Chunk chunk = loadedChunks.get(cx + "," + cz);
        if (chunk != null) {
            chunk.setBlock(bx, y, bz, type);
            String chunkKey = cx + "," + cz;
            if (pendingRebuildTasks.add(chunkKey)) {
                generationExecutor.submit(new WorldTask(chunk, playerPosFromMain()));
            }
        }
    }

    private Vector3f playerPosFromMain() {
        if (app != null && app.getCamera() != null) {
            return app.getCamera().getLocation();
        }
        return Vector3f.ZERO;
    }

    public void applySavedBlocksToChunk(Chunk chunk) {
        int cx = chunk.getChunkX();
        int cz = chunk.getChunkZ();
        long cKey = ((long) cx << 32) | (cz & 0xFFFFFFFFL);

        Map<Integer, Byte> mods = chunkModifications.get(cKey);
        if (mods != null) {
            for (Map.Entry<Integer, Byte> entry : mods.entrySet()) {
                int bKey = entry.getKey();
                int bx = (bKey >> 12) & 0xF;
                int y = (bKey >> 4) & 0xFF;
                int bz = bKey & 0xF;
                chunk.setBlock(bx, y, bz, entry.getValue());
            }
        }
    }

    public void saveWorldToFile(long seed, Vector3f playerPos, byte[] hotbar, int selectedSlot) {
        generationExecutor.shutdown(); 

        String finalPath = saveFileName;
        if (finalPath.endsWith(".txt")) {
            finalPath = finalPath.substring(0, finalPath.length() - 4) + ".dat";
        }

        try (FileOutputStream fos = new FileOutputStream(finalPath);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             GZIPOutputStream gzos = new GZIPOutputStream(bos);
             DataOutputStream dos = new DataOutputStream(gzos)) {

            dos.writeInt(0x44534346); 
            dos.writeInt(3); 

            dos.writeLong(seed);
            dos.writeBoolean(isCreative);
            dos.writeBoolean(isFlatWorld);
            dos.writeFloat(playerPos.x);
            dos.writeFloat(playerPos.y);
            dos.writeFloat(playerPos.z);
            dos.writeInt(selectedSlot);

            for (int i = 0; i < 9; i++) {
                dos.writeByte(hotbar[i]);
                dos.writeInt(64); 
            }

            int totalMods = 0;
            for (Map<Integer, Byte> chunkMap : chunkModifications.values()) {
                totalMods += chunkMap.size();
            }

            dos.writeInt(totalMods);
            for (Map.Entry<Long, Map<Integer, Byte>> chunkEntry : chunkModifications.entrySet()) {
                long cKey = chunkEntry.getKey();
                int cx = (int) (cKey >> 32);
                int cz = (int) cKey;
                for (Map.Entry<Integer, Byte> blockEntry : chunkEntry.getValue().entrySet()) {
                    int bKey = blockEntry.getKey();
                    int bx = (bKey >> 12) & 0xF;
                    int y = (bKey >> 4) & 0xFF;
                    int bz = bKey & 0xF;

                    dos.writeInt(cx * Chunk.SIZE_X + bx);
                    dos.writeShort(y);
                    dos.writeInt(cz * Chunk.SIZE_Z + bz);
                    dos.writeByte(blockEntry.getValue());
                }
            }

            dos.writeInt(chestInventories.size());
            for (Map.Entry<String, Player.InventorySlot[]> entry : chestInventories.entrySet()) {
                dos.writeUTF(entry.getKey());
                Player.InventorySlot[] slots = entry.getValue();
                dos.writeInt(slots.length);
                for (Player.InventorySlot s : slots) {
                    dos.writeByte(s.blockType);
                    dos.writeInt(s.count);
                }
            }

            dos.flush();
            System.out.println("SYSTEM: Saved compressed binary state.");
        } catch (Exception e) {
            System.err.println("SYSTEM ERROR: Failed to save: " + e.getMessage());
        }
    }

    public WorldSaveData loadWorldFromFile() {
        chunkModifications.clear();
        chestInventories.clear();
        WorldSaveData data = new WorldSaveData();

        String finalPath = saveFileName;
        if (finalPath.endsWith(".txt")) {
            finalPath = finalPath.substring(0, finalPath.length() - 4) + ".dat";
        }

        File file = new File(finalPath); 
        if (!file.exists()) {
            return data; 
        }

        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis);
             GZIPInputStream gzis = new GZIPInputStream(bis);
             DataInputStream dis = new DataInputStream(gzis)) {

            int magic = dis.readInt();
            if (magic != 0x44534346) {
                return data;
            }
            int version = dis.readInt();

            data.seed = dis.readLong();
            data.creative = dis.readBoolean();
            isCreative = data.creative;
            data.flat = dis.readBoolean();
            isFlatWorld = data.flat;

            data.playerPos.x = dis.readFloat();
            data.playerPos.y = dis.readFloat();
            data.playerPos.z = dis.readFloat();
            data.selectedSlot = dis.readInt();

            for (int i = 0; i < 9; i++) {
                data.hotbar[i] = dis.readByte();
                if (version >= 2) {
                    data.hotbarCounts[i] = dis.readInt();
                } else {
                    data.hotbarCounts[i] = 64;
                }
            }

            int modCount = dis.readInt();
            for (int i = 0; i < modCount; i++) {
                int x = dis.readInt();
                int y = dis.readShort();
                int z = dis.readInt();
                byte type = dis.readByte();

                long cKey = getChunkKey(x, z);
                int bKey = getBlockKey(x, y, z);
                chunkModifications.computeIfAbsent(cKey, k -> new ConcurrentHashMap<>()).put(bKey, type);
            }

            if (version >= 3) {
                int numChests = dis.readInt();
                for (int i = 0; i < numChests; i++) {
                    String coordKey = dis.readUTF();
                    int numSlots = dis.readInt();
                    Player.InventorySlot[] slots = new Player.InventorySlot[numSlots];
                    for (int j = 0; j < numSlots; j++) {
                        slots[j] = new Player.InventorySlot();
                        slots[j].blockType = dis.readByte();
                        slots[j].count = dis.readInt();
                    }
                    chestInventories.put(coordKey, slots);
                }
            }

            data.success = true;
        } catch (Exception e) {
            System.err.println("SYSTEM ERROR: Failed to unpack binary save: " + e.getMessage());
        }
        return data;
    }

    // ДОБАВЛЕНО: взрыв TNT. Выбивает блоки в радиусе, разбрасывает частицы,
    // роняет предметы (если мир не в креативе) и наносит игроку урон,
    // ослабевающий с расстоянием от эпицентра.
    public void explodeAt(Vector3f pos, float radius, float power) {
        createExplosion((int) Math.floor(pos.x), (int) Math.floor(pos.y), (int) Math.floor(pos.z), radius, app);
    }

    public void createExplosion(int cx, int cy, int cz, float radius, Main app) {
        int r = (int) Math.ceil(radius);

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist > radius) continue;

                    int bx = cx + dx;
                    int by = cy + dy;
                    int bz = cz + dz;
                    if (by < 0 || by >= Chunk.SIZE_Y) continue;

                    byte type = getBlockAt(bx, by, bz);
                    if (type == 0) continue;

                    setBlockAt(bx, by, bz, (byte) 0);

                    if (app.particleManager != null) {
                        app.particleManager.spawnBreakParticles(bx, by, bz, type, blockMaterials);
                    }

                    if (!isCreative && app.itemDropManager != null && Math.random() > 0.4) {
                        app.itemDropManager.spawnDroppedItemAt(bx, by + 0.2f, bz, type, false, Vector3f.ZERO, blockMaterials);
                    }
                }
            }
        }

        if (app.player != null) {
            float playerDist = app.player.pos.distance(new Vector3f(cx, cy, cz));
            if (playerDist <= radius * 1.5f) {
                float falloff = 1.0f - (playerDist / (radius * 1.5f));
                float damage = 12.0f * falloff;
                if (damage > 0.0f) {
                    app.player.damage(damage);
                }
            }
        }
    }

    public int getBiomeAt(int x, int z) {
        if (isFlatWorld) return 0; 
        return Chunk.getBiomeAt(x, z, this);
    }

    public int getMaxHeightAt(int x, int z) {
        if (isFlatWorld) return 4; 

        int biome = Chunk.getBiomeAt(x, z, this);
        // Единый источник высоты — та же формула, что и в генерации чанка,
        // чтобы спавн/подгонка не расходились с реальной поверхностью.
        double heightValue = Chunk.biomeHeight(biome, x + seedOffsetX, z + seedOffsetZ);
        return (int) Math.max(5, Math.min(heightValue, Chunk.SIZE_Y - 1));
    }
}