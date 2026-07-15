package com.mygame;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;

public class ConsoleCommandExecutor {

    private static final ColorRGBA neonYellow = new ColorRGBA(0.82f, 1.0f, 0.08f, 1.0f);
    private static final String[] BIOME_NAMES = {"PLAINS","DESERT","TUNDRA","MOUNTAINS","REDWOOD","SWAMP","WASTES","TAIGA","MUSHROOM","MYCELIUM","GLACIER","MYCELIUM ISLAND","OCEAN","BEACH","SAVANNA","JUNGLE","VILLAGE"};

    // Имя метода изменено на "execute"
    public static void execute(String rawCmd, Main app) {
        String cmd = rawCmd.trim();
        if (cmd.isEmpty()) return;

        app.ui.addConsoleLog("[CMD] " + cmd, ColorRGBA.White);

        String[] parts = cmd.split(" ");
        String base = parts[0].toLowerCase();

        try {
        switch (base) {
            case "/help" -> {
                app.ui.addConsoleLog("Survival cmds: /heal, /god, /setspawn, /weather <rain/clear>", ColorRGBA.Gray);
                app.ui.addConsoleLog("Admin/test: /spawnmob <0-18>, /mobtypes, /biome, /tpbiome <id>, /findvillage, /class, /mobs, /tp <x> <y> <z>, /give <1-33>, /killmobs, /heal, /god, /fly, /weather, /time, /save", ColorRGBA.Gray);
                app.ui.addConsoleLog("Sys cmds: /sound, /fly, /clear, /speed <val>, /time, /weather, /killmobs, /gravity", ColorRGBA.Gray);
            }
            case "/heal" -> {
                app.player.health = app.player.maxHealth;
                app.player.hunger = app.player.maxHunger;
                app.player.oxygen = app.player.maxOxygen;
                app.ui.addConsoleLog("[SUCCESS] Vital signs restored to maximum capacities.", neonYellow);
            }
            case "/god" -> {
                app.player.isGodMode = !app.player.isGodMode;
                app.ui.addConsoleLog("[SUCCESS] God Mode status set to: " + (app.player.isGodMode ? "ENABLED" : "DISABLED"), neonYellow);
            }
            case "/setspawn" -> {
                app.player.spawnX = app.player.pos.x;
                app.player.spawnY = app.player.pos.y;
                app.player.spawnZ = app.player.pos.z;
                app.player.hasCustomSpawn = true;
                app.ui.addConsoleLog(String.format("[SUCCESS] Custom respawn coordinates anchored: %.1f / %.1f / %.1f", app.player.spawnX, app.player.spawnY, app.player.spawnZ), neonYellow);
            }
            case "/weather" -> {
                if (parts.length < 2) { 
                    app.ui.addConsoleLog("Usage: /weather <clear/rain/snow>", ColorRGBA.Red); 
                    return; 
                }
                String weatherType = parts[1].toLowerCase();
                if (weatherType.equals("rain")) {
                    app.weatherManager.setWeather(WeatherManager.Weather.RAIN);
                    app.ui.addConsoleLog("[SUCCESS] Atmospheric moisture index altered: PRECIPITATION START.", neonYellow);
                } else if (weatherType.equals("snow")) {
                    app.weatherManager.setWeather(WeatherManager.Weather.SNOW);
                    app.ui.addConsoleLog("[SUCCESS] Atmospheric temperature drop: SNOW START.", neonYellow);
                } else {
                    app.weatherManager.setWeather(WeatherManager.Weather.CLEAR);
                    app.ui.addConsoleLog("[SUCCESS] Atmospheric moisture index altered: DRY CLEAR WEATHER.", neonYellow);
                }
            }
            case "/acid" -> {
                app.playerCam.acidMode = !app.playerCam.acidMode;
                app.ui.addConsoleLog("[SUCCESS] Cognitive camera aberration matrix set to: " + (app.playerCam.acidMode ? "ENABLED" : "DISABLED"), neonYellow);
            }
            case "/dnd" -> {
                app.isTimeLocked = !app.isTimeLocked;
                app.ui.addConsoleLog("[SUCCESS] Diurnal solar trajectory lock state: " + (app.isTimeLocked ? "LOCKED" : "UNLOCKED"), neonYellow);
            }
            case "/mobs" -> {
                java.util.Random rand = new java.util.Random();
                for (int i = 0; i < 5; i++) {
                    float offset_x = (rand.nextFloat() - 0.5f) * 12f;
                    float offset_z = (rand.nextFloat() - 0.5f) * 12f;
                    Vector3f spawnPos = app.player.pos.add(offset_x, 1.0f, offset_z);
                    int rType = rand.nextInt(10); 
                    app.mobManager.spawnMobAtBiome(app.getAssetManager(), spawnPos, rType);
                }
                app.ui.addConsoleLog("[SUCCESS] Generated 5 biological entities within spatial proximity.", neonYellow);
            }
            case "/give" -> {
                if (parts.length < 2) { app.ui.addConsoleLog("Usage: /give <1-30>", ColorRGBA.Red); return; }
                try {
                    int id = Integer.parseInt(parts[1]);
                    if (id >= 1 && id <= 33) {
                        app.player.inventory[app.getSelectedSlot()].blockType = (byte) id;
                        app.player.inventory[app.getSelectedSlot()].count = 64;
                        app.syncHotbarArray();
                        app.ui.updateHotbarIcon(app.getAssetManager(), app.getSelectedSlot(), id);
                        app.ui.addConsoleLog("[SUCCESS] Deposited material asset: " + UserInterfaceManager.getBlockName(id), neonYellow);
                    } else {
                        app.ui.addConsoleLog("Block ID must be 1-30", ColorRGBA.Red);
                    }
                } catch (NumberFormatException e) { app.ui.addConsoleLog("Invalid ID format", ColorRGBA.Red); }
            }
            case "/tp" -> {
                if (parts.length < 4) { app.ui.addConsoleLog("Usage: /tp <x> <y> <z>", ColorRGBA.Red); return; }
                try {
                    float tx = Float.parseFloat(parts[1]);
                    float ty = Float.parseFloat(parts[2]);
                    float tz = Float.parseFloat(parts[3]);
                    app.player.pos.set(tx, ty, tz);
                    app.player.velocity.set(0, 0, 0);
                    app.ui.addConsoleLog(String.format("[SUCCESS] Coordinates overwritten. Transferred to: %.1f / %.1f / %.1f", tx, ty, tz), neonYellow);
                } catch (NumberFormatException e) { app.ui.addConsoleLog("Coordinates format error", ColorRGBA.Red); }
            }
            case "/sound" -> {
                GameSettings.soundEnabled = !GameSettings.soundEnabled;
                app.soundManager.setEnabled(GameSettings.soundEnabled);
                app.ui.addConsoleLog("[SUCCESS] Audio subsystem state: " + (GameSettings.soundEnabled ? "ENABLED" : "DISABLED"), neonYellow);
            }
            case "/fly" -> {
                app.player.isFlying = !app.player.isFlying;
                app.player.velocity.set(0, 0, 0);
                app.ui.addConsoleLog("[SUCCESS] Gravity calculation bypass set to: " + (app.player.isFlying ? "ENABLED" : "DISABLED"), neonYellow);
            }
            case "/save" -> {
                app.saveGame();
                app.ui.addConsoleLog("[SUCCESS] World state persisted to disk.", neonYellow);
            }
            case "/clear" -> {
                for (int i = 0; i < 36; i++) {
                    app.player.inventory[i].blockType = 0;
                    app.player.inventory[i].count = 0;
                }
                app.syncHotbarArray();
                for (int i = 0; i < 9; i++) {
                    app.ui.updateHotbarIcon(app.getAssetManager(), i, 0);
                }
                app.ui.addConsoleLog("[SUCCESS] All inventory indices successfully cleared.", neonYellow);
            }
            case "/speed" -> {
                if (parts.length < 2) { app.ui.addConsoleLog("Usage: /speed <val>"); return; }
                try {
                    float s = Float.parseFloat(parts[1]);
                    app.player.playerSpeed = s;
                    app.ui.addConsoleLog("[SUCCESS] Base linear velocity factor updated to: " + s, neonYellow);
                } catch (NumberFormatException e) { app.ui.addConsoleLog("Invalid speed format", ColorRGBA.Red); }
            }
            case "/time" -> {
                if (parts.length < 2) { app.ui.addConsoleLog("Usage: /time <day/night/noon/midnight>", ColorRGBA.Red); return; }
                String val = parts[1].toLowerCase();
                switch (val) {
                    case "day" -> { app.dayTimer = 0.5f; app.ui.addConsoleLog("[SUCCESS] Temporal coordinate set: DAY (0.5).", neonYellow); }
                    case "night" -> { app.dayTimer = 3.6f; app.ui.addConsoleLog("[SUCCESS] Temporal coordinate set: NIGHT (3.6).", neonYellow); }
                    case "noon" -> { app.dayTimer = 1.5f; app.ui.addConsoleLog("[SUCCESS] Temporal coordinate set: NOON (1.5).", neonYellow); }
                    case "midnight" -> { app.dayTimer = 4.7f; app.ui.addConsoleLog("[SUCCESS] Temporal coordinate set: MIDNIGHT (4.7).", neonYellow); }
                    default -> app.ui.addConsoleLog("Unknown time value.", ColorRGBA.Red);
                }
            }
            case "/killmobs" -> {
                app.mobManager.cleanup();
                app.mobManager.init(app.getAssetManager(), app.getRootNode());
                app.ui.addConsoleLog("[SUCCESS] Erased all current biological entity coordinates.", neonYellow);
            }
            case "/gravity" -> {
                if (parts.length < 2) { app.ui.addConsoleLog("Usage: /gravity <value>", ColorRGBA.Red); return; }
                try {
                    float g = Float.parseFloat(parts[1]);
                    app.player.gravity = -g;
                    app.ui.addConsoleLog("[SUCCESS] Spatial acceleration factor adjusted to: " + g, neonYellow);
                } catch (NumberFormatException e) { app.ui.addConsoleLog("Invalid gravity format", ColorRGBA.Red); }
            }
            case "/spawnmob" -> {
                if (parts.length < 2) { app.ui.addConsoleLog("Usage: /spawnmob <type 0-18>", ColorRGBA.Red); return; }
                try {
                    int t = Integer.parseInt(parts[1]);
                    if (t < 0 || t > 18) { app.ui.addConsoleLog("Type must be 0-18", ColorRGBA.Red); return; }
                    Vector3f sp = app.player.pos.add(app.getCamera().getDirection().mult(4.0f));
                    sp.y = app.world.getMaxHeightAt((int)Math.floor(sp.x), (int)Math.floor(sp.z)) + 2.0f;
                    app.mobManager.spawnMob(t, app.getAssetManager(), sp);
                    app.ui.addConsoleLog("[SUCCESS] Spawned mob type " + t, neonYellow);
                } catch (NumberFormatException e) { app.ui.addConsoleLog("Invalid type", ColorRGBA.Red); }
            }
            case "/mobtypes" -> {
                app.ui.addConsoleLog("0 Sheep 1 Rabbit 2 Bear 3 Golem 4 Fox 5 Slime 6 Void 7 WolfCub 8 FoxRed 9 MushCow", ColorRGBA.Gray);
                app.ui.addConsoleLog("10 Villager 11 Zombie 12 Skeleton 13 Spider 14 Chicken 15 Pig 16 Cow 17 Wolf 18 Creeper", ColorRGBA.Gray);
            }
            case "/biome" -> {
                int b = app.world.getBiomeAt((int)Math.floor(app.player.pos.x), (int)Math.floor(app.player.pos.z));
                app.ui.addConsoleLog("[BIOME] id=" + b + " " + BIOME_NAMES[b], neonYellow);
            }
            case "/tpbiome" -> {
                if (parts.length < 2) { app.ui.addConsoleLog("Usage: /tpbiome <id 0-15>", ColorRGBA.Red); return; }
                try {
                    int target = Integer.parseInt(parts[1]);
                    int px = (int)Math.floor(app.player.pos.x), pz = (int)Math.floor(app.player.pos.z);
                    boolean found = false;
                    for (int r = 1; r <= 400 && !found; r++) {
                        for (int dx = -r; dx <= r; dx++) {
                            for (int dz = -r; dz <= r; dz++) {
                                if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                                int bx = px + dx, bz = pz + dz;
                                if (app.world.getBiomeAt(bx, bz) == target) {
                                    int gy = app.world.getMaxHeightAt(bx, bz);
                                    app.player.pos.set(bx + 0.5f, gy + 2.0f, bz + 0.5f);
                                    app.player.velocity.set(0,0,0);
                                    app.ui.addConsoleLog("[SUCCESS] Teleported to biome " + target, neonYellow);
                                    found = true; break;
                                }
                            }
                            if (found) break;
                        }
                        if (found) break;
                    }
                    if (!found) app.ui.addConsoleLog("Biome " + target + " not found nearby", ColorRGBA.Red);
                } catch (NumberFormatException e) { app.ui.addConsoleLog("Invalid id", ColorRGBA.Red); }
            }
            case "/class" -> {
                PlayerClass pc = app.player.playerClass;
                app.ui.addConsoleLog("[CLASS] " + pc.displayName + " | HP:" + pc.baseHP + " MP:" + pc.baseMana
                        + " | melee x" + pc.meleeDamageMult + " ranged x" + pc.rangedMult, neonYellow);
            }
            case "/findvillage" -> {
                int px = (int)Math.floor(app.player.pos.x), pz = (int)Math.floor(app.player.pos.z);
                boolean found = false;
                for (int r = 1; r <= 600 && !found; r++) {
                    for (int dx = -r; dx <= r; dx++) {
                        for (int dz = -r; dz <= r; dz++) {
                            if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                            int bx = px + dx, bz = pz + dz;
                            if (app.world.getBiomeAt(bx, bz) == 0) { // PLAINS
                                int gy = app.world.getMaxHeightAt(bx, bz);
                                app.player.pos.set(bx + 0.5f, gy + 2.0f, bz + 0.5f);
                                app.player.velocity.set(0,0,0);
                                app.ui.addConsoleLog("[SUCCESS] Teleported to PLAINS", neonYellow);
                                found = true; break;
                            }
                        }
                        if (found) break;
                    }
                    if (found) break;
                }
                if (!found) app.ui.addConsoleLog("No plains found nearby", ColorRGBA.Red);
            }
                        default -> app.ui.addConsoleLog("Unknown command. Type /help", ColorRGBA.Red);
        }
        } catch (Throwable t) {
            app.ui.addConsoleLog("[ERROR] command failed: " + t, ColorRGBA.Red);
        }
    }
}