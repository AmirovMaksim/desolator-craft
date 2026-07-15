package com.mygame;

import java.awt.DisplayMode;
import java.awt.GraphicsEnvironment;
import java.util.List;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.input.event.JoyAxisEvent;
import com.jme3.input.event.JoyButtonEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.input.event.TouchEvent;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.FogFilter;
import com.jme3.post.ssao.SSAOFilter;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.shadow.DirectionalLightShadowFilter;
import com.jme3.system.AppSettings;

public class Main extends SimpleApplication implements RawInputListener {

    World world;
    MainMenuState mainMenuState;
    PauseMenuState pauseMenuState;

    boolean gameStarted = false;
    boolean isPaused = false;

    boolean isInventoryOpen = false;
    float inventoryProgress = 0.0f; 

    final Player player = new Player();
    final PlayerCamera playerCam = new PlayerCamera();
    final FirstPersonHand hand = new FirstPersonHand();
    final PlayerModel playerModel = new PlayerModel();
    final UserInterfaceManager ui = new UserInterfaceManager();
    final MobManager mobManager = new MobManager(); 
    VillageManager villageManager = null;

    /** Безопасный доступ к assetManager для внешних менеджеров (VillageManager и т.п.). */
    public AssetManager getAssetManagerSafe() { return assetManager; }

    final EnvironmentManager environmentManager = new EnvironmentManager();
    final ItemDropManager itemDropManager = new ItemDropManager();
    final ParticleManager particleManager = new ParticleManager();
    final WeatherManager weatherManager = new WeatherManager();
    final AchievementManager achievements = new AchievementManager();
    final InteractionHandler interactionHandler = new InteractionHandler();
    final SoundManager soundManager = new SoundManager();
    private float prevBobTimer = 0.0f;
    private float autoSaveTimer = 0.0f;
    private boolean spaceHeld = false;
    // СОСТОЯНИЕ ЗАГРУЗКИ МИРА (экран как в Minecraft)
    private final WorldLoadingScreen loadingScreen = new WorldLoadingScreen();
    private final CloudManager cloudManager = new CloudManager();
    private float worldLoadTimeout = 0.0f;
    private boolean worldLoading = false;
    private int spawnChunkX = 0, spawnChunkZ = 0;
    private boolean loadFromSave = false;
    private boolean prevLandingSound = false;
    private boolean prevDead = false;

    private float physicsAccumulator = 0.0f;
    private static final float PHYSICS_TIME_STEP = 1.0f / 60.0f;

    private int lastBiome = -1;
    private float biomeDisplayTime = 0.0f;
    private BitmapText biomeTextNode;

    private FilterPostProcessor fpp;
    private DirectionalLight sun;
    private AmbientLight ambient;

    private float lastScreenWidth = 0;
    private float lastScreenHeight = 0;
    private int selectedSlot = 0;
    
    float dayTimer = 1.5f; 
    boolean isTimeLocked = false;
    boolean isRaining = false;

    public static final byte[] hotbarBlocks = {0, 0, 0, 0, 0, 0, 0, 0, 0};
    final Material[] blockMaterials = new Material[34]; 

    private boolean isConsoleOpen = false;
    private float consoleProgress = 0.0f;
    private String typedCommand = "";

    public static void main(String[] args) {
        // Глобальный перехватчик: пишет ЛЮБОЙ необработанный exception
        // (в т.ч. из потока рендера jME) в dc_crash.log рядом с проектом,
        // чтобы при вылете в мир лог не был пустым.
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                String logPath = "dc_crash.log";
                // пытаемся положить лог рядом с рабочей папкой
                try {
                    String cp = System.getProperty("user.dir");
                    if (cp != null) logPath = cp + java.io.File.separator + "dc_crash.log";
                } catch (Exception ignore) {}
                try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(logPath, true))) {
                    pw.println("=== UNCAUGHT EXCEPTION on thread " + t.getName() + " ===");
                    pw.println(new java.util.Date());
                    e.printStackTrace(pw);
                    pw.println("=========================================");
                    pw.flush();
                }
            } catch (Exception ignored) {}
        });

        Main app = new Main();
        AppSettings settings = new AppSettings(true);
        settings.setTitle("DESOLATOR CRAFT"); 
        settings.setWidth(1280);
        settings.setHeight(720);
        settings.setFullscreen(false);
        settings.setResizable(true);
        app.setSettings(settings);
        app.start();
    }

    public DirectionalLight getSunLight() { return sun; }
    public AmbientLight getAmbientLight() { return ambient; }
    public int getSelectedSlot() { return selectedSlot; }

    @Override
    public void simpleInitApp() {
        inputManager.deleteMapping(SimpleApplication.INPUT_MAPPING_EXIT);

        inputManager.addMapping("Toggle_F11", new KeyTrigger(KeyInput.KEY_F11));
        inputManager.addListener(f11Listener, "Toggle_F11");
        inputManager.addMapping("ToggleCameraMode", new KeyTrigger(KeyInput.KEY_F5));
        inputManager.addListener(cameraModeListener, "ToggleCameraMode");
        inputManager.addMapping("ToggleDevMenu", new KeyTrigger(KeyInput.KEY_F4));
        inputManager.addListener(devMenuListener, "ToggleDevMenu");

        inputManager.addMapping("MoveForward", new KeyTrigger(KeyInput.KEY_W));
        inputManager.addMapping("MoveBackward", new KeyTrigger(KeyInput.KEY_S));
        inputManager.addMapping("MoveLeft", new KeyTrigger(KeyInput.KEY_A));
        inputManager.addMapping("MoveRight", new KeyTrigger(KeyInput.KEY_D));
        inputManager.addMapping("Jump", new KeyTrigger(KeyInput.KEY_SPACE));
        inputManager.addMapping("MoveDown", new KeyTrigger(KeyInput.KEY_LCONTROL));
        inputManager.addMapping("Sprint", new KeyTrigger(KeyInput.KEY_LSHIFT));
        inputManager.addMapping("PauseGame", new KeyTrigger(KeyInput.KEY_ESCAPE));
        inputManager.addMapping("ToggleInventory", new KeyTrigger(KeyInput.KEY_E));

        inputManager.addMapping("Interact", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping("PlaceBlock", new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
        inputManager.addMapping("DropItem", new KeyTrigger(KeyInput.KEY_Q));
        inputManager.addMapping("OpenConsole", new KeyTrigger(KeyInput.KEY_T));
        inputManager.addMapping("Respawn", new KeyTrigger(KeyInput.KEY_R));

        inputManager.addListener(movementListener, "MoveForward", "MoveBackward", "MoveLeft", "MoveRight", "Jump", "MoveDown", "Sprint");
        inputManager.addListener(pauseListener, "PauseGame");
        inputManager.addListener(interactListener, "Interact", "PlaceBlock");
        inputManager.addListener(inventoryListener, "ToggleInventory");
        inputManager.addListener(gameplayActionListener, "DropItem", "OpenConsole");
        inputManager.addListener(respawnListener, "Respawn");

        for (int i = 0; i < 9; i++) {
            inputManager.addMapping("Slot_" + i, new KeyTrigger(KeyInput.KEY_1 + i));
            inputManager.addListener(slotKeyListener, "Slot_" + i);
        }

        inputManager.addMapping("ScrollUp", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
        inputManager.addMapping("ScrollDown", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));
        inputManager.addListener(scrollListener, "ScrollUp", "ScrollDown");

        mainMenuState = new MainMenuState((slot, seed, creative, flat) -> startGame(slot, seed, creative, flat));
        stateManager.attach(mainMenuState);
        inputManager.addRawInputListener(this);
    }

    private final ActionListener f11Listener = (name, isPressed, tpf) -> {
        if (name.equals("Toggle_F11") && !isPressed) toggleFullscreen();
    };

    private final ActionListener cameraModeListener = (name, isPressed, tpf) -> {
        if (name.equals("ToggleCameraMode") && !isPressed && gameStarted && !isPaused && !player.isDead) {
            playerCam.cycleCameraMode();
        }
    };

    private final ActionListener devMenuListener = (name, isPressed, tpf) -> {
        if (name.equals("ToggleDevMenu") && !isPressed && gameStarted && !isPaused && !isConsoleOpen && !player.isDead) toggleDevMenu();
    };

    private final ActionListener gameplayActionListener = (name, isPressed, tpf) -> {
        if (!gameStarted || isPaused || isInventoryOpen || player.isDead) return;
        if (name.equals("DropItem") && !isPressed && !isConsoleOpen) {
            triggerDropItem();
        } else if (name.equals("OpenConsole") && !isPressed && !isConsoleOpen) {
            openConsole();
        }
    };

    private final ActionListener respawnListener = (name, isPressed, tpf) -> {
        if (name.equals("Respawn") && !isPressed && gameStarted && player.isDead) triggerRespawn();
    };

    private final ActionListener movementListener = (name, isPressed, tpf) -> {
        if (!gameStarted || isPaused || isInventoryOpen || isConsoleOpen || player.isDead || stateManager.hasState(stateManager.getState(DevMenuState.class))) return;
        switch (name) {
            case "MoveForward" -> player.up = isPressed;
            case "MoveBackward" -> player.down = isPressed;
            case "MoveLeft" -> player.left = isPressed;
            case "MoveRight" -> player.right = isPressed;
            case "MoveDown" -> player.crouch = isPressed;
            case "Sprint" -> player.sprint = isPressed;
            case "Jump" -> {
                player.jump = isPressed;
                spaceHeld = isPressed;
                if (isPressed) {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - player.lastSpacePressTime < 300) { 
                        // Полёт ТОЛЬКО в креативе. В выживании (вкл. flat) — запрещён.
                        if (world != null && world.isCreative()) player.isFlying = !player.isFlying;
                        player.velocity.set(0, 0, 0); 
                    } else if (player.onGround) {
                        soundManager.jump();
                    }
                    player.lastSpacePressTime = currentTime;
                }
            }
        }
    };

    private final ActionListener pauseListener = (name, isPressed, tpf) -> {
        if (name.equals("PauseGame") && !isPressed && gameStarted && !player.isDead) {
            if (stateManager.hasState(stateManager.getState(DevMenuState.class))) {
                toggleDevMenu();
                return;
            }
            if (isConsoleOpen) closeConsole();
            else if (isInventoryOpen) closeInventory();
            else if (isPaused) resumeGame();
            else pauseGame();
        }
    };

    private final ActionListener interactListener = (name, isPressed, tpf) -> {
        if (!gameStarted || isPaused || isConsoleOpen || player.isDead || stateManager.hasState(stateManager.getState(DevMenuState.class))) return;
        if (isInventoryOpen) {
            if (name.equals("Interact")) {
                Vector2f mousePos = inputManager.getCursorPosition();
                if (isPressed) {
                    ui.startInventoryDrag(assetManager, mousePos.x, mousePos.y, player, world, selectedSlot);
                } else {
                    ui.endInventoryDrag(assetManager, mousePos.x, mousePos.y, player, world, selectedSlot);
                }
                syncHotbarArray();
            }
            return;
        }
        if (name.equals("Interact")) {
            interactionHandler.setLeftClickHeld(isPressed);
        } else if (name.equals("PlaceBlock") && isPressed) {
            interactionHandler.triggerPlaceOrOpenBlock(cam, this);
        }
    };

    private final ActionListener inventoryListener = (name, isPressed, tpf) -> {
        if (name.equals("ToggleInventory") && !isPressed && gameStarted && !isPaused && !isConsoleOpen && !player.isDead) {
            if (isInventoryOpen) closeInventory(); else openInventory();
        }
    };

    private final ActionListener slotKeyListener = (name, isPressed, tpf) -> {
        if (isPressed && gameStarted && !isPaused && !isInventoryOpen && !isConsoleOpen && !player.isDead) {
            selectedSlot = Integer.parseInt(name.substring(5));
        }
    };

    private final ActionListener scrollListener = (name, isPressed, tpf) -> {
        if (isPressed && gameStarted && !isPaused && !isInventoryOpen && !isConsoleOpen && !player.isDead) {
            if (name.equals("ScrollUp")) {
                selectedSlot = (selectedSlot - 1 < 0) ? 8 : selectedSlot - 1;
            } else if (name.equals("ScrollDown")) {
                selectedSlot = (selectedSlot + 1 > 8) ? 0 : selectedSlot + 1;
            }
        }
    };

    void toggleDevMenu() {
        DevMenuState devState = stateManager.getState(DevMenuState.class);
        if (devState == null) {
            player.resetMovement();
            flyCam.setEnabled(false);
            inputManager.setCursorVisible(true);
            ui.setHudVisible(false);
            hand.setVisible(false);
            stateManager.attach(new DevMenuState(this));
        } else {
            stateManager.detach(devState);
            flyCam.setEnabled(true);
            inputManager.setCursorVisible(false);
            ui.setHudVisible(true);
            hand.setVisible(true);
        }
    }

    public void openInventory() {
        isInventoryOpen = true;
        player.resetMovement();
        flyCam.setEnabled(false);
        inputManager.setCursorVisible(true);
    }

    private void closeInventory() {
        isInventoryOpen = false;
        ui.isChestOpen = false;
        ui.currentChestCoords = "";
        ui.cancelInventoryDrag(player, world);
        flyCam.setEnabled(true);
        inputManager.setCursorVisible(false);
    }

    private void pauseGame() {
        isPaused = true;
        player.resetMovement();
        flyCam.setEnabled(false);
        inputManager.setCursorVisible(true);
        ui.setHudVisible(false);
        hand.setVisible(false);
        pauseMenuState = new PauseMenuState(this::resumeGame, this::quitToMainMenu);
        stateManager.attach(pauseMenuState);
    }

    private void resumeGame() {
        stateManager.detach(pauseMenuState);
        flyCam.setEnabled(true);
        inputManager.setCursorVisible(false);
        isPaused = false;
        ui.setHudVisible(true);
        hand.setVisible(true);
    }

    private void quitToMainMenu() {
        if (world != null) {
            syncHotbarArray();
            world.saveWorldToFile(world.getWorldSeed(), player.pos, hotbarBlocks, selectedSlot); 
        }
        stateManager.detach(pauseMenuState);
        isPaused = false; isInventoryOpen = false; isConsoleOpen = false; gameStarted = false;
        rootNode.detachAllChildren();
        environmentManager.clear();
        itemDropManager.clear();
        particleManager.clear();

        if (sun != null) { rootNode.removeLight(sun); sun = null; }
        if (ambient != null) { rootNode.removeLight(ambient); ambient = null; }
        if (fpp != null) { viewPort.removeProcessor(fpp); fpp = null; }

        ui.cleanup(guiNode);
        hand.cleanup();
        playerModel.cleanup();
        achievements.cleanup();
        weatherManager.cleanup();
        loadingScreen.cleanup();
        cloudManager.cleanup();
        mobManager.cleanup(); 
        mainMenuState = new MainMenuState((slot, seed, creative, flat) -> startGame(slot, seed, creative, flat));
        stateManager.attach(mainMenuState);
    }

    /** Запуск асинхронной загрузки мира с экраном (как в Minecraft). */
    private void beginWorldLoad() {
        worldLoading = true;
        worldLoadTimeout = 0.0f;
        if (ui != null) ui.setHudVisible(false);
        loadingScreen.show();
        loadingScreen.setProgress(0.02f);
        world.requestLoadAround(spawnChunkX, spawnChunkZ, 2);
    }

    /** Вызывается из update: ждёт загрузки, затем спавнит игрока. */
    private void updateWorldLoad(float tpf) {
        if (!worldLoading) return;
        worldLoadTimeout += tpf;
        int r = 2;
        int total = (2 * r + 1) * (2 * r + 1);
        int loaded = 0;
        for (int x = spawnChunkX - r; x <= spawnChunkX + r; x++)
            for (int z = spawnChunkZ - r; z <= spawnChunkZ + r; z++)
                if (world.isChunkLoaded(x, z)) loaded++;
        float p = Math.max(0.02f, loaded / (float) total);
        loadingScreen.setProgress(p);

        boolean areaReady = world.isAreaLoaded(spawnChunkX, spawnChunkZ, r);
        // FALLBACK: если какой-то чанк не докачался (завис на 96%), всё равно спавним
        // через 8 секунд ожидания, чтобы экран загрузки не висел вечно.
        if (areaReady || worldLoadTimeout > 8.0f) {
            int spawnY = -1;
            if (loadFromSave) {
                int baseX = spawnChunkX * Chunk.SIZE_X;
                int baseZ = spawnChunkZ * Chunk.SIZE_Z;
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        int y = Chunk.SIZE_Y - 1;
                        while (y > 0 && world.getBlockAt(baseX + dx, y, baseZ + dz) == 0) y--;
                        if (y > spawnY) spawnY = y;
                    }
                }
                if (spawnY >= 0) player.pos.set(baseX, spawnY + 2.0f, baseZ);
            } else {
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        int y = Chunk.SIZE_Y - 1;
                        while (y > 0 && world.getBlockAt(dx, y, dz) == 0) y--;
                        if (y > spawnY) spawnY = y;
                    }
                }
                if (spawnY < 0) spawnY = 64;
                player.pos.set(0, spawnY + 2.0f, 0);
            }
            player.velocity.set(0, 0, 0);
            player.onGround = false;
            player.isFlying = world.isCreative();
            worldLoading = false;
            loadingScreen.setProgress(1.0f);
            loadingScreen.hide();
            if (ui != null) ui.setHudVisible(true);
        }
    }

    public void saveGame() {
        if (world != null && player != null) {
            syncHotbarArray();
            world.saveWorldToFile(world.getWorldSeed(), player.pos, hotbarBlocks, selectedSlot);
        }
    }

    public void syncHotbarArray() {
        for (int i = 0; i < 9; i++) {
            hotbarBlocks[i] = player.inventory[i].blockType;
        }
    }

    /** Атака мобов: бьёт всех мобов в радиусе перед игроком (по направлению камеры). */
    public void attackMobs() {
        Vector3f origin = cam.getLocation();
        Vector3f dir = cam.getDirection().clone().mult(3.5f); // дистанция удара ~3.5 блока
        Vector3f center = origin.add(dir);
        center.y -= 0.5f; // бьём чуть ниже (по телу моба)
        List<com.mygame.MobManager.Mob> killed = mobManager.damageMobsInRadius(center, 2.2f, 5.0f);
        if (!killed.isEmpty()) {
            for (com.mygame.MobManager.Mob m : killed) {
                // Дроп предмета при смерти моба (случайный блок из хотбара-подобных)
                byte dropType = (byte) (1 + (int) (Math.random() * 8));
                mobManager.removeMob(m);
                Vector3f mp = m.getNode().getLocalTranslation();
                itemDropManager.spawnDroppedItemAt(mp.x, mp.y + 0.5f, mp.z, dropType, false, Vector3f.ZERO, blockMaterials);
            }
            soundManager.hit();
            achievements.unlock("FIRST_KILL", "First Blood");
        }
    }

    public void startGame(String slotFile, long customSeed, boolean creative, boolean flat) {
        stateManager.detach(mainMenuState);
        ColorRGBA skyColor = new ColorRGBA(0.45f, 0.65f, 0.95f, 1.0f);
        viewPort.setBackgroundColor(skyColor);
        cam.setFrustumPerspective(GameSettings.fov, (float) cam.getWidth() / cam.getHeight(), 0.01f, 1000f);

        startGameInitMaterials();

        rootNode.setShadowMode(RenderQueue.ShadowMode.CastAndReceive); 
        world = new World(blockMaterials, rootNode, slotFile, this);
        villageManager = new VillageManager(world, this);
        world.setVillageManager(villageManager);
        loadingScreen.init(assetManager, guiNode, guiFont, cam.getWidth(), cam.getHeight());
        cloudManager.init(assetManager, rootNode);

        World.WorldSaveData saveData = world.loadWorldFromFile();
        if (!saveData.success) {
            world.setCreative(creative);
            world.setFlatWorld(flat);
            world.initializeSeed(customSeed);
            
            // Хотбар ПУСТ по умолчанию (блоки добываются в выживании вручную).
            selectedSlot = 0;

            // ВРЕМЕННО: игрок высоко в воздухе, пока мир грузится
            // (чтобы не провалиться в незагруженные чанки).
            player.pos.set(0, 140f, 0);
            spawnChunkX = 0; spawnChunkZ = 0;
            loadFromSave = false;
            beginWorldLoad();
        } else {
            world.initializeSeed(saveData.seed);
            for (int i = 0; i < 9; i++) {
                player.inventory[i].blockType = saveData.hotbar[i];
                player.inventory[i].count = saveData.hotbarCounts[i];
            }
            syncHotbarArray();
            selectedSlot = saveData.selectedSlot;
            // ВРЕМЕННО высоко, пока мир грузится
            player.pos.set(saveData.playerPos.x, 140f, saveData.playerPos.z);
            spawnChunkX = (int) Math.floor(saveData.playerPos.x / Chunk.SIZE_X);
            spawnChunkZ = (int) Math.floor(saveData.playerPos.z / Chunk.SIZE_Z);
            loadFromSave = true;
            beginWorldLoad();
        }

        player.isGodMode = world.isCreative();
        // НЕ вызываем world.update здесь — мир грузится асинхронно
        // через экран загрузки (beginWorldLoad + проверка в update).
        rootNode.updateModelBound();

        itemDropManager.init(rootNode);
        particleManager.init(rootNode, assetManager);
        interactionHandler.init(assetManager, rootNode);

        sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-0.4f, -0.8f, -0.5f).normalizeLocal());
        sun.setColor(new ColorRGBA(0.98f, 0.96f, 0.90f, 1.0f)); 
        rootNode.addLight(sun);

        ambient = new AmbientLight();
        ambient.setColor(new ColorRGBA(0.40f, 0.42f, 0.48f, 1.0f)); 
        rootNode.addLight(ambient);

        environmentManager.init(assetManager, rootNode);

        fpp = new FilterPostProcessor(assetManager);
        FogFilter fogFilter = new FogFilter();
        fogFilter.setFogColor(skyColor);
        fogFilter.setFogDistance(140.0f); 
        fogFilter.setFogDensity(0.40f);
        fpp.addFilter(fogFilter);

        DirectionalLightShadowFilter shadowFilter = new DirectionalLightShadowFilter(assetManager, 1024, 3);
        shadowFilter.setLight(sun);
        shadowFilter.setShadowIntensity(0.42f); 
        shadowFilter.setLambda(0.65f);
        // БЕЗ edge filtering (PCF-poisson) — shadow pass ~в 4 раза дешевле,
        // визуально почти без разницы, но FPS на слабых GPU заметно растёт.
        shadowFilter.setEdgesThickness(1); 
        fpp.addFilter(shadowFilter);

        SSAOFilter ssaoFilter = new SSAOFilter(0.48f, 2.8f, 0.22f, 0.12f);
        fpp.addFilter(ssaoFilter);

        BloomFilter bloomFilter = new BloomFilter(BloomFilter.GlowMode.Scene);
        bloomFilter.setBloomIntensity(0.9f);
        bloomFilter.setExposurePower(1.1f);
        bloomFilter.setExposureCutOff(0.82f);
        fpp.addFilter(bloomFilter);

        viewPort.addProcessor(fpp);

        ui.init(assetManager, guiNode, guiFont, settings.getWidth(), settings.getHeight());
        hand.init(assetManager, guiNode);
        playerModel.init(assetManager, rootNode);
        weatherManager.init(assetManager, guiNode);
        achievements.init(assetManager, guiNode, guiFont);
        soundManager.init(assetManager);
        soundManager.setEnabled(GameSettings.soundEnabled);
        hand.setSoundManager(soundManager);
        interactionHandler.setSoundManager(soundManager);
        interactionHandler.setApp(this);
        player.setSoundManager(soundManager);
        itemDropManager.setSoundManager(soundManager);

        for (int i = 0; i < 9; i++) ui.updateHotbarIcon(assetManager, i, player.inventory[i].blockType);
        
        mobManager.init(assetManager, rootNode); 
        mobManager.setApp(this);
        for (int i = 0; i < 15; i++) {
            float rx = (float) (Math.random() * 160 - 80);
            float rz = (float) (Math.random() * 160 - 80);
            int biome = world.getBiomeAt((int) rx, (int) rz);
            mobManager.spawnMobAtBiome(assetManager, new Vector3f(rx, 80.0f, rz), biome); 
        }

        biomeTextNode = new BitmapText(guiFont);
        biomeTextNode.setSize(38);
        biomeTextNode.setColor(ColorRGBA.White);
        guiNode.attachChild(biomeTextNode);

        flyCam.setEnabled(true);
        flyCam.setMoveSpeed(0);
        inputManager.setCursorVisible(false);

        cam.setLocation(player.pos.add(0, 1.6f, 0));
        cam.lookAtDirection(new Vector3f(0.5f, -0.2f, 0.8f).normalizeLocal(), Vector3f.UNIT_Y);

        gameStarted = true;
        isPaused = false;
        isInventoryOpen = false;
        dayTimer = 1.5f; 
    }

    private void startGameInitMaterials() {
        for (int i = 1; i <= 33; i++) {
            ColorRGBA color = switch (i) {
                case 1, 17 -> new ColorRGBA(0.25f, 0.65f, 0.25f, 1.0f); 
                case 2 -> new ColorRGBA(0.42f, 0.28f, 0.15f, 1.0f);
                case 8 -> new ColorRGBA(0.85f, 0.82f, 0.55f, 1.0f);
                case 10 -> new ColorRGBA(0.95f, 0.95f, 0.98f, 1.0f);
                case 6 -> new ColorRGBA(0.38f, 0.24f, 0.12f, 1.0f); 
                case 18 -> new ColorRGBA(0.72f, 0.55f, 0.38f, 1.0f); 
                case 7 -> new ColorRGBA(0.12f, 0.50f, 0.12f, 1.0f);
                case 9 -> new ColorRGBA(0.18f, 0.45f, 0.12f, 1.0f);
                case 11 -> new ColorRGBA(0.08f, 0.32f, 0.18f, 1.0f);
                case 12 -> new ColorRGBA(0.42f, 0.18f, 0.12f, 1.0f); 
                case 13 -> new ColorRGBA(0.15f, 0.15f, 0.16f, 1.0f); 
                case 14 -> new ColorRGBA(0.35f, 0.10f, 0.05f, 1.0f); 
                case 15 -> new ColorRGBA(0.85f, 0.70f, 0.55f, 1.0f); 
                case 16 -> new ColorRGBA(0.98f, 0.65f, 0.75f, 1.0f); 
                case 19 -> new ColorRGBA(0.68f, 0.28f, 0.98f, 1.0f); 
                case 20 -> new ColorRGBA(0.92f, 0.92f, 0.95f, 1.0f); 
                case 21 -> new ColorRGBA(0.95f, 0.52f, 0.10f, 1.0f); 
                case 22 -> new ColorRGBA(0.48f, 0.85f, 0.98f, 1.0f); 
                case 23 -> new ColorRGBA(0.48f, 0.25f, 0.52f, 1.0f); 
                case 24 -> new ColorRGBA(0.88f, 0.12f, 0.12f, 1.0f); 
                case 25 -> new ColorRGBA(0.58f, 0.32f, 0.15f, 1.0f); 
                case 26 -> new ColorRGBA(0.75f, 0.50f, 0.20f, 1.0f); 
                case 27 -> new ColorRGBA(0.12f, 0.42f, 0.85f, 0.72f); 
                case 28 -> new ColorRGBA(0.92f, 0.18f, 0.02f, 1.0f);  
                case 29 -> new ColorRGBA(0.85f, 0.85f, 0.88f, 1.0f); 
                case 30 -> new ColorRGBA(0.70f, 0.70f, 0.75f, 1.0f); 
                case 31 -> new ColorRGBA(0.85f, 0.2f, 0.2f, 1.0f); 
                case 32 -> new ColorRGBA(0.4f, 0.85f, 0.4f, 0.75f);
                case 33 -> new ColorRGBA(1.0f, 0.78f, 0.40f, 1.0f); // ФАКЕЛ
                default -> new ColorRGBA(0.45f, 0.45f, 0.48f, 1.0f);
            };
            blockMaterials[i] = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
            blockMaterials[i].setBoolean("UseMaterialColors", true);
            blockMaterials[i].setTexture("DiffuseMap", ProceduralTextureGenerator.createProceduralBlockTexture(color, i));
            blockMaterials[i].setColor("Ambient", ColorRGBA.White);
            blockMaterials[i].setColor("Diffuse", ColorRGBA.White);
            blockMaterials[i].setColor("Specular", new ColorRGBA(0.02f, 0.02f, 0.02f, 1.0f));
            if (i == 33) {
                // Факел сам светится. В jME 3.6 Lighting.j3md НЕТ параметров
                // "Emissive"/"EmissivePower"/"UseEmissive" — есть только GlowColor
                // (и GlowMap). Невалидный параметр валит игру при входе в мир.
                blockMaterials[i].setColor("GlowColor", new ColorRGBA(1.0f, 0.6f, 0.2f, 1.0f));
            }
            blockMaterials[i].setFloat("Shininess", 0.08f);
            
            if (i == 27 || i == 32) {
                blockMaterials[i].getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
            }
        }
    }

    private void openConsole() {
        isConsoleOpen = true;
        player.resetMovement();
        flyCam.setEnabled(false);
        typedCommand = "";
        ui.setConsoleInputText(">");
    }

    private void closeConsole() {
        isConsoleOpen = false;
        flyCam.setEnabled(true);
    }

    private void triggerDropItem() {
        Player.InventorySlot slot = player.inventory[selectedSlot];
        if (slot.blockType == 0 || slot.count <= 0) return;
        itemDropManager.spawnDroppedItemAt(player.pos.x, player.pos.y + 1.4f, player.pos.z, slot.blockType, true, cam.getDirection(), blockMaterials);
        if (!world.isCreative()) {
            slot.count--;
            if (slot.count <= 0) slot.blockType = 0;
        }
        syncHotbarArray();
        ui.updateHotbarIcon(assetManager, selectedSlot, slot.blockType);
        hand.triggerPunch();
        soundManager.drop();
    }

    private void triggerRespawn() {
        player.health = player.maxHealth;
        player.hunger = player.maxHunger;
        player.oxygen = player.maxOxygen;
        player.isDead = false;
        player.velocity.set(0, 0, 0);

        if (player.hasCustomSpawn) {
            player.pos.set(player.spawnX, player.spawnY, player.spawnZ);
        } else {
            // Ищем самую ВЫСОКУЮ поверхность вокруг (0,0) — как при старте мира
            int spawnY = -1;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int y = Chunk.SIZE_Y - 1;
                    while (y > 0 && world.getBlockAt(dx, y, dz) == 0) y--;
                    if (y > spawnY) spawnY = y;
                }
            }
            if (spawnY < 0) spawnY = 64;
            player.pos.set(0, spawnY + 2.0f, 0);
        }
        ui.showDeathScreen(false);
        flyCam.setEnabled(true);
        inputManager.setCursorVisible(false);
    }

    private void toggleFullscreen() {
        AppSettings s = getContext().getSettings();
        boolean targetFullscreen = !s.isFullscreen();
        s.setFullscreen(targetFullscreen);

        if (targetFullscreen) {
            DisplayMode mode = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode();
            s.setWidth(mode.getWidth());
            s.setHeight(mode.getHeight());
            s.setFrequency(mode.getRefreshRate());
            s.setBitsPerPixel(mode.getBitDepth());
        } else {
            s.setWidth(1280);
            s.setHeight(720);
        }
        setSettings(s);
        restart();
    }

    @Override
    public void simpleUpdate(float tpf) {
        if (!gameStarted) return;

        // ЭКРАН ЗАГРУЗКИ МИРА: пока грузимся — физику не крутим
        updateWorldLoad(tpf);

        if (isPaused) return; 

        if (tpf > 0.1f) tpf = 0.1f;

        if (player.isDead) {
            if (!prevDead) {
                soundManager.death();
                prevDead = true;
            }
            player.resetMovement();
            flyCam.setEnabled(false);
            inputManager.setCursorVisible(true);
            ui.showDeathScreen(true);
        } else {
            prevDead = false;
            ui.showDeathScreen(false);
            player.updateSurvivalStats(tpf);
        }

        environmentManager.update(tpf, this, viewPort, fpp);

        if (isInventoryOpen) {
            inventoryProgress = FastMath.interpolateLinear(tpf * 10.0f, inventoryProgress, 1.0f);
        } else {
            inventoryProgress = FastMath.interpolateLinear(tpf * 10.0f, inventoryProgress, 0.0f);
        }

        if (isConsoleOpen) {
            consoleProgress = FastMath.interpolateLinear(tpf * 12.0f, consoleProgress, 1.0f);
        } else {
            consoleProgress = FastMath.interpolateLinear(tpf * 12.0f, consoleProgress, 0.0f);
        }

        float currentW = cam.getWidth();
        float currentH = cam.getHeight();

        if (!isInventoryOpen && !isConsoleOpen && !player.isDead && !stateManager.hasState(stateManager.getState(DevMenuState.class))) {
            // Реальное удержание Space (не auto-repeat) -> непрерывное всплытие в воде
            player.swimUp = spaceHeld && player.isInWater;
            physicsAccumulator += tpf;
            while (physicsAccumulator >= PHYSICS_TIME_STEP) {
                        if (!worldLoading) player.runPhysicsStep(PHYSICS_TIME_STEP, cam, world);
                physicsAccumulator -= PHYSICS_TIME_STEP;
            }
        }

        world.update(player.pos, tpf);
        cloudManager.update(player.pos);
        mobManager.update(tpf, world, player.pos); 

        // ПОГОДА: частицы вокруг игрока (скрыты при инвентаре/паузе)
        if (!isInventoryOpen && !isPaused && !player.isDead && !worldLoading) {
            weatherManager.update(tpf, player.pos, environmentManager.getDayFactor());
            WeatherManager.Weather w = weatherManager.getCurrent();
            isRaining = (w == WeatherManager.Weather.RAIN || w == WeatherManager.Weather.SNOW);
            ui.setWeatherLabel(w.name());
        }

        achievements.update(tpf);

        // АВТО-СОХРАНЕНИЕ каждые 90 секунд
        autoSaveTimer += tpf;
        if (autoSaveTimer >= 90f) {
            autoSaveTimer = 0f;
            saveGame();
            ui.addConsoleLog("[AUTO-SAVE] World saved.", ColorRGBA.Gray);
        }

        itemDropManager.update(tpf, player, world, ui, assetManager, this);
        particleManager.update(tpf);
        particleManager.updateFireflies(tpf, player.pos, environmentManager.getDayFactor());
        if (environmentManager.getDayFactor() < 0.2f && player.pos.y > 30) {
            achievements.unlock("NIGHT_OWL", "Night Owl");
        }
        if (player.isDead && !prevDead) {
            achievements.unlock("FIRST_DEATH", "Oof");
        }
        prevDead = player.isDead;
        if (player.isInWater) achievements.unlock("SWIM", "Splish Splash");
        if (player.isInLava) achievements.unlock("LAVA", "Hot Feet");
        interactionHandler.update(tpf, cam, this);

        if (currentW != lastScreenWidth || currentH != lastScreenHeight) {
            loadingScreen.reposition(currentW, currentH);
            lastScreenWidth = currentW;
            lastScreenHeight = currentH;
            ui.repositionCrosshair(currentW, currentH);
            ui.repositionHUD(currentW, currentH, selectedSlot);
            cam.setFrustumPerspective(GameSettings.fov, currentW / currentH, 0.01f, 1000f);
        }

        cam.setFrustumPerspective(GameSettings.fov, currentW / currentH, 0.01f, 1000f);

        playerCam.update(tpf, cam, player, isInventoryOpen);

        // Звук приземления: landingShakeTime устанавливается в Player при ударе о землю.
        if (player.landingShakeTime > 0.30f && !prevLandingSound) {
            soundManager.land();
            prevLandingSound = true;
        }
        if (player.landingShakeTime <= 0.0f) {
            prevLandingSound = false;
        }

        // Звук шагов: срабатывает на каждом полном цикле bobTimer (два шага на цикл).
        if (!isInventoryOpen && !isConsoleOpen && !player.isDead && !player.isFlying
                && player.onGround && (player.velocity.x != 0 || player.velocity.z != 0)) {
            float bt = playerCam.getBobTimer();
            if (bt - prevBobTimer < 0f) { // bobTimer сбросился/перешёл через 2*PI
                soundManager.step();
            }
            prevBobTimer = bt;
        } else {
            prevBobTimer = playerCam.getBobTimer();
        }

        hand.update(tpf, cam, player, playerCam, isInventoryOpen, currentW, currentH);
        // ВИДИМОСТЬ: модель персонажа только в 3-м лице, рука — только в 1-м лице
        int camMode = playerCam.cameraMode;
        playerModel.setVisible(camMode != 0);
        hand.setVisible(camMode == 0 && !isInventoryOpen && !isConsoleOpen && !player.isDead);
        if (camMode != 0) playerModel.update(tpf, player, this);
        ui.update(tpf, currentW, currentH, isInventoryOpen, inventoryProgress, inputManager, selectedSlot, player, world);
        ui.updateConsole(tpf, currentW, currentH, consoleProgress);

        updateBiomeTextDisplay(tpf, currentW, currentH);
    }

    private void updateBiomeTextDisplay(float tpf, float currentW, float currentH) {
        if (gameStarted && !isPaused) {
            int currentBiome = world.getBiomeAt((int) player.pos.x, (int) player.pos.z);
            if (currentBiome != lastBiome) {
                lastBiome = currentBiome;
                biomeDisplayTime = 5.0f; 
                
                String name = switch (currentBiome) {
                    case 1 -> "DESERT PLAIN";
                    case 2 -> "SNOWY TUNDRA";
                    case 3 -> "MOUNTAIN PEAKS";
                    case 4 -> "REDWOOD FOREST";
                    case 5 -> "VOLCANIC WASTELAND";
                    case 6 -> "SWAMP";
                    case 7 -> "CHERRY BLOSSOM GROVE";
                    case 8 -> "AMETHYST CAVE";
                    case 9 -> "AUTUMN FOREST";
                    case 10 -> "GLACIER PLAINS";
                    case 11 -> "MYCELIUM ISLAND";
                    default -> "OAK FOREST";
                };
                biomeTextNode.setText("Entering " + name);
            }

            if (biomeDisplayTime > 0.0f) {
                biomeDisplayTime -= tpf;
                float alpha = 1.0f;
                if (biomeDisplayTime > 4.5f) { 
                    alpha = (5.0f - biomeDisplayTime) / 0.5f;
                } else if (biomeDisplayTime < 1.0f) { 
                    alpha = biomeDisplayTime;
                }
                
                biomeTextNode.setColor(new ColorRGBA(1.0f, 0.88f, 0.35f, alpha)); 
                biomeTextNode.setCullHint(com.jme3.scene.Spatial.CullHint.Inherit);
                
                float textW = biomeTextNode.getLineWidth();
                biomeTextNode.setLocalTranslation(currentW / 2 - textW / 2, currentH - 80f, 15);
            } else {
                biomeTextNode.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
            }
        }
    }

    @Override
    public void onKeyEvent(com.jme3.input.event.KeyInputEvent evt) {
        if (!isConsoleOpen || evt.isReleased()) return;

        int code = evt.getKeyCode();
        char c = evt.getKeyChar();

        if (code == KeyInput.KEY_RETURN) {
            ConsoleCommandExecutor.execute(typedCommand, this);
            closeConsole();
        } else if (code == KeyInput.KEY_ESCAPE) {
            closeConsole();
        } else if (code == KeyInput.KEY_BACK) {
            if (typedCommand.length() > 0) {
                typedCommand = typedCommand.substring(0, typedCommand.length() - 1);
            }
            ui.setConsoleInputText(">" + typedCommand);
        } else if (c != '\0' && !Character.isISOControl(c)) {
            typedCommand += c;
            ui.setConsoleInputText(">" + typedCommand);
        }
    }

    @Override public void beginInput() {}
    @Override public void endInput() {}
    @Override public void onJoyAxisEvent(JoyAxisEvent evt) {}
    @Override public void onJoyButtonEvent(JoyButtonEvent evt) {}
    @Override public void onMouseMotionEvent(MouseMotionEvent evt) {}
    @Override public void onMouseButtonEvent(MouseButtonEvent evt) {}
    @Override public void onTouchEvent(TouchEvent evt) {}
}