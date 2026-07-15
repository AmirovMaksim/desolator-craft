package com.mygame;

import java.util.ArrayList;
import java.util.List;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;

public class MobManager {

    private final List<Mob> mobs = new ArrayList<>();
    private final Node mobsNode = new Node("MobsNode");
    private Main app;

    public void init(AssetManager assetManager, Node rootNode) {
        rootNode.attachChild(mobsNode);
    }

    public void setApp(Main app) { this.app = app; }

    /** Спавн конкретного типа моба (0-18). */
    public void spawnMob(int mobType, AssetManager assetManager, Vector3f position) {
        Mob mob = new Mob(assetManager, position, mobType);
        mob.ownerApp = this.app;
        mobsNode.attachChild(mob.getNode());
        mobs.add(mob);
    }

    /** Случайный моб, подходящий под биом (включая 9 новых типов). */
    public void spawnMobAtBiome(AssetManager assetManager, Vector3f position, int biome) {
        int type;
        switch (biome) {
            case 0: type = pick(0,1,9); break;              // PLAINS: овца, кролик, мухомор-корова
            case 1: type = pick(14,15); break;             // DESERT: курица, свинья
            case 2: type = pick(2,0,16); break;            // TUNDRA: медведь, овца, корова
            case 3: type = pick(3,11,12); break;           // MOUNTAINS: голем, зомби, скелет
            case 4: type = pick(1,8,0); break;             // REDWOOD: кролик, лиса, овца
            case 5: type = pick(5,5,13); break;            // SWAMP: слайм, паук
            case 6: type = pick(6,11,18); break;           // WASTES: дух, зомби, крипер
            case 7: type = pick(2,16,17); break;           // TAIGA: медведь, корова, волк
            case 8: type = pick(8,0,14); break;            // MUSHROOM: лиса, овца, курица
            case 9: type = pick(9,0,15); break;            // MYCELIUM: мухомор-корова, овца, свинья
            case 10: type = pick(2,16,11); break;          // ICE: медведь, корова, зомби
            case 11: type = pick(9,0,15); break;           // MYCELIUM ISLAND
            case 12: type = pick(14,15,16); break;         // OCEAN: наземные у края
            case 13: type = pick(14,15); break;            // BEACH
            case 14: type = pick(15,16,17); break;         // SAVANNA: свинья, корова, волк
            case 15: type = pick(13,14,18); break;         // JUNGLE: паук, курица, крипер
            default: type = pick(0,1,14);
        }
        spawnMob(type, assetManager, position);
    }

    private int pick(int... opts) {
        return opts[(int) (Math.random() * opts.length)];
    }

    /** Наносит урон всем мобам в радиусе `radius` от `center` (атака игрока). Возвращает убитых. */
    public List<Mob> damageMobsInRadius(Vector3f center, float radius, float damage) {
        List<Mob> killed = new ArrayList<>();
        for (Mob mob : mobs) {
            if (mob.isDead()) continue;
            if (mob.position.distance(center) <= radius) {
                boolean dead = mob.damage(damage);
                if (dead) killed.add(mob);
            }
        }
        return killed;
    }

    public void removeMob(Mob mob) {
        mob.getNode().removeFromParent();
        mobs.remove(mob);
    }

    public void update(float tpf, World world, Vector3f playerPos) {
        if (world == null || playerPos == null) return; // защита от NPE при раннем вызове
        for (Mob mob : mobs) {
            try {
                mob.update(tpf, world, playerPos); // один упавший моб не роняет игру
            } catch (Throwable t) {
                System.err.println("[Mob] update упал для типа " + mob.mobType + ": " + t);
            }
        }
    }

    public void cleanup() {
        mobsNode.detachAllChildren();
        mobsNode.removeFromParent();
        mobs.clear();
    }

    public static class Mob {
        private final Node node;
        private final int mobType; 
        private final Vector3f position;
        private final Vector3f velocity = new Vector3f();
        private final Vector3f targetDirection = new Vector3f(1, 0, 0);
        private Main ownerApp; // ссылка на игру (для урона игроку)

        private float wanderTimer = 0f;
        private float animationTimer = 0f;
        private boolean onGround = false;
        private int aiState = 0; 
        private float panicTimer = 0f;
        private float angryTimer = 0f;

        // --- БОЁВКА ---
        private float health = 10.0f;
        private boolean dead = false;
        private float attackCooldown = 0f;   // кулдаун атаки игрока
        private float hurtFlash = 0f;         // визуальная вспышка при получении урона
        private Material bodyMatRef;          // ссылка на материал тела для вспышки

        private Geometry legL1, legR1, legL2, legR2; 

        public Mob(AssetManager assetManager, Vector3f startPos, int biome) {
            this.mobType = biome; 
            this.position = startPos;
            this.node = new Node("Mob");

            Material bodyMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            Material accentMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            bodyMatRef = bodyMat; // для вспышки урона

            // HP/урон зависят от типа моба
            health = switch (biome) {
                case 3 -> 30.0f;  // Голем (босс-подобный)
                case 5 -> 16.0f;  // Слайм
                case 6 -> 20.0f;  // Пустотный Дух
                case 11 -> 20.0f; // Зомби
                case 12 -> 16.0f; // Скелет
                case 13 -> 14.0f; // Паук
                case 17 -> 18.0f; // Волк
                case 18 -> 12.0f; // Крипер
                case 16 -> 14.0f; // Корова
                case 15 -> 12.0f; // Свинья
                default -> 10.0f;  // мирные/слабые
            };

            if (mobType == 0) { // Овечка
                ColorRGBA bodyColor = new ColorRGBA(0.95f, 0.95f, 0.95f, 1.0f);
                ColorRGBA faceColor = new ColorRGBA(0.48f, 0.38f, 0.32f, 1.0f);
                bodyMat.setTexture("ColorMap", ProceduralTextureGenerator.createProceduralMobTexture(bodyColor, 0, false));
                accentMat.setTexture("ColorMap", ProceduralTextureGenerator.createProceduralMobTexture(faceColor, 0, true));

                Box bodyBox = new Box(0.45f, 0.35f, 0.65f);
                Geometry body = new Geometry("SheepBody", bodyBox);
                body.setMaterial(bodyMat);
                body.setLocalTranslation(0, 0.5f, 0);
                node.attachChild(body);

                Box headBox = new Box(0.22f, 0.22f, 0.22f);
                Geometry head = new Geometry("SheepHead", headBox);
                head.setMaterial(accentMat);
                head.setLocalTranslation(0, 0.82f, 0.65f); 
                node.attachChild(head);

                Box legBox = new Box(0.1f, 0.3f, 0.1f);
                legL1 = new Geometry("LegL1", legBox); legL1.setMaterial(bodyMat); legL1.setLocalTranslation(-0.25f, 0.15f, 0.4f);
                legR1 = new Geometry("LegR1", legBox); legR1.setMaterial(bodyMat); legR1.setLocalTranslation(0.25f, 0.15f, 0.4f);
                legL2 = new Geometry("LegL2", legBox); legL2.setMaterial(bodyMat); legL2.setLocalTranslation(-0.25f, 0.15f, -0.4f);
                legR2 = new Geometry("LegR2", legBox); legR2.setMaterial(bodyMat); legR2.setLocalTranslation(0.25f, 0.15f, -0.4f);
                
                node.attachChild(legL1);
                node.attachChild(legR1);
                node.attachChild(legL2);
                node.attachChild(legR2);

            } else if (mobType == 1) { // Краб
                ColorRGBA shellColor = new ColorRGBA(0.90f, 0.28f, 0.12f, 1.0f);
                ColorRGBA eyeColor = ColorRGBA.Black;
                bodyMat.setTexture("ColorMap", ProceduralTextureGenerator.createProceduralMobTexture(shellColor, 1, false));
                accentMat.setTexture("ColorMap", ProceduralTextureGenerator.createProceduralMobTexture(eyeColor, 1, true));

                Box bodyBox = new Box(0.35f, 0.20f, 0.35f);
                Geometry body = new Geometry("CrabBody", bodyBox);
                body.setMaterial(bodyMat);
                body.setLocalTranslation(0, 0.3f, 0);
                node.attachChild(body);

                Box eyeBox = new Box(0.06f, 0.12f, 0.06f);
                Geometry eyeL = new Geometry("EyeL", eyeBox); eyeL.setMaterial(accentMat); eyeL.setLocalTranslation(-0.15f, 0.55f, 0.2f);
                Geometry eyeR = new Geometry("EyeR", eyeBox); eyeR.setMaterial(accentMat); eyeR.setLocalTranslation(0.15f, 0.55f, 0.2f);
                node.attachChild(eyeL);
                node.attachChild(eyeR);

                Box legBox = new Box(0.15f, 0.05f, 0.08f);
                legL1 = new Geometry("LegL1", legBox); legL1.setMaterial(bodyMat); legL1.setLocalTranslation(-0.45f, 0.15f, 0.15f);
                legR1 = new Geometry("LegR1", legBox); legR1.setMaterial(bodyMat); legR1.setLocalTranslation(0.45f, 0.15f, 0.15f);
                legL2 = new Geometry("LegL2", legBox); legL2.setMaterial(bodyMat); legL2.setLocalTranslation(-0.45f, 0.15f, -0.15f);
                legR2 = new Geometry("LegR2", legBox); legR2.setMaterial(bodyMat); legR2.setLocalTranslation(0.45f, 0.15f, -0.15f);

                node.attachChild(legL1);
                node.attachChild(legR1);
                node.attachChild(legL2);
                node.attachChild(legR2);

            } else if (mobType == 2) { // Пингвин
                ColorRGBA darkColor = new ColorRGBA(0.08f, 0.08f, 0.12f, 1.0f);
                ColorRGBA whiteColor = ColorRGBA.White;
                bodyMat.setTexture("ColorMap", ProceduralTextureGenerator.createProceduralMobTexture(darkColor, 2, false));
                accentMat.setTexture("ColorMap", ProceduralTextureGenerator.createProceduralMobTexture(whiteColor, 2, true));

                Box bodyBox = new Box(0.30f, 0.45f, 0.30f);
                Geometry body = new Geometry("PenguinBody", bodyBox);
                body.setMaterial(bodyMat);
                body.setLocalTranslation(0, 0.45f, 0);
                node.attachChild(body);

                Box bellyBox = new Box(0.20f, 0.30f, 0.06f);
                Geometry belly = new Geometry("Belly", bellyBox);
                belly.setMaterial(accentMat);
                belly.setLocalTranslation(0, 0.35f, 0.25f); 
                node.attachChild(belly);

                Box beakBox = new Box(0.08f, 0.06f, 0.14f);
                Geometry beak = new Geometry("Beak", beakBox);
                Material beakMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
                ColorRGBA orangeColor = new ColorRGBA(0.98f, 0.62f, 0.04f, 1.0f);
                beakMat.setTexture("ColorMap", ProceduralTextureGenerator.createProceduralMobTexture(orangeColor, 2, false));
                beak.setMaterial(beakMat);
                beak.setLocalTranslation(0, 0.68f, 0.25f);
                node.attachChild(beak);

                Box legBox = new Box(0.08f, 0.08f, 0.12f);
                legL1 = new Geometry("LegL1", legBox); legL1.setMaterial(beakMat); legL1.setLocalTranslation(-0.15f, 0.04f, 0.08f);
                legR1 = new Geometry("LegR1", legBox); legR1.setMaterial(beakMat); legR1.setLocalTranslation(0.15f, 0.04f, 0.08f);
                node.attachChild(legL1);
                node.attachChild(legR1);

            } else if (mobType == 3) { // Голем
                ColorRGBA rockColor = new ColorRGBA(0.38f, 0.38f, 0.42f, 1.0f);
                ColorRGBA redColor = new ColorRGBA(0.95f, 0.12f, 0.08f, 1.0f);
                bodyMat.setTexture("ColorMap", ProceduralTextureGenerator.createProceduralMobTexture(rockColor, 3, false));
                accentMat.setTexture("ColorMap", ProceduralTextureGenerator.createProceduralMobTexture(redColor, 3, true));

                Box bodyBox = new Box(0.55f, 0.65f, 0.55f);
                Geometry body = new Geometry("GolemBody", bodyBox);
                body.setMaterial(bodyMat);
                body.setLocalTranslation(0, 0.85f, 0);
                node.attachChild(body);

                Box eyeBox = new Box(0.32f, 0.06f, 0.06f);
                Geometry eyes = new Geometry("GolemEyes", eyeBox);
                eyes.setMaterial(accentMat);
                eyes.setLocalTranslation(0, 1.15f, 0.52f);
                node.attachChild(eyes);

                Box legBox = new Box(0.18f, 0.40f, 0.18f);
                legL1 = new Geometry("LegL1", legBox); legL1.setMaterial(bodyMat); legL1.setLocalTranslation(-0.28f, 0.20f, 0.15f);
                legR1 = new Geometry("LegR1", legBox); legR1.setMaterial(bodyMat); legR1.setLocalTranslation(0.28f, 0.20f, 0.15f);
                legL2 = new Geometry("LegL2", legBox); legL2.setMaterial(bodyMat); legL2.setLocalTranslation(-0.28f, 0.20f, -0.15f);
                legR2 = new Geometry("LegR2", legBox); legR2.setMaterial(bodyMat); legR2.setLocalTranslation(0.28f, 0.20f, -0.15f);

                node.attachChild(legL1);
                node.attachChild(legR1);
                node.attachChild(legL2);
                node.attachChild(legR2);

            } else if (mobType == 4) { // Лиса
                ColorRGBA orangeFur = new ColorRGBA(0.92f, 0.45f, 0.15f, 1.0f);
                ColorRGBA whiteFur = new ColorRGBA(0.96f, 0.96f, 0.96f, 1.0f);
                bodyMat.setTexture("ColorMap", ProceduralTextureGenerator.createProceduralMobTexture(orangeFur, 4, false));
                accentMat.setTexture("ColorMap", ProceduralTextureGenerator.createProceduralMobTexture(whiteFur, 4, true));

                Box bodyBox = new Box(0.35f, 0.28f, 0.55f);
                Geometry body = new Geometry("FoxBody", bodyBox);
                body.setMaterial(bodyMat);
                body.setLocalTranslation(0, 0.35f, 0);
                node.attachChild(body);

                Box headBox = new Box(0.20f, 0.20f, 0.20f);
                Geometry head = new Geometry("FoxHead", headBox);
                head.setMaterial(bodyMat);
                head.setLocalTranslation(0, 0.55f, 0.55f);
                node.attachChild(head);

                Box snoutBox = new Box(0.08f, 0.08f, 0.12f);
                Geometry snout = new Geometry("FoxSnout", snoutBox);
                snout.setMaterial(accentMat);
                snout.setLocalTranslation(0, 0.50f, 0.75f);
                node.attachChild(snout);

                Box legBox = new Box(0.08f, 0.22f, 0.08f);
                legL1 = new Geometry("LegL1", legBox); legL1.setMaterial(bodyMat); legL1.setLocalTranslation(-0.20f, 0.11f, 0.35f);
                legR1 = new Geometry("LegR1", legBox); legR1.setMaterial(bodyMat); legR1.setLocalTranslation(0.20f, 0.11f, 0.35f);
                legL2 = new Geometry("LegL2", legBox); legL2.setMaterial(bodyMat); legL2.setLocalTranslation(-0.20f, 0.11f, -0.35f);
                legR2 = new Geometry("LegR2", legBox); legR2.setMaterial(bodyMat); legR2.setLocalTranslation(0.20f, 0.11f, -0.35f);

                node.attachChild(legL1);
                node.attachChild(legR1);
                node.attachChild(legL2);
                node.attachChild(legR2);

            } else if (mobType == 5) { // Слайм
                ColorRGBA magmaColor = new ColorRGBA(0.45f, 0.08f, 0.04f, 1.0f);
                ColorRGBA coreColor = new ColorRGBA(0.98f, 0.75f, 0.12f, 1.0f);
                bodyMat.setTexture("ColorMap", ProceduralTextureGenerator.createProceduralMobTexture(magmaColor, 5, false));
                accentMat.setTexture("ColorMap", ProceduralTextureGenerator.createProceduralMobTexture(coreColor, 5, true));

                Box bodyBox = new Box(0.40f, 0.40f, 0.40f);
                Geometry body = new Geometry("LavaSlimeBody", bodyBox);
                body.setMaterial(bodyMat);
                body.setLocalTranslation(0, 0.40f, 0);
                node.attachChild(body);

                Box coreBox = new Box(0.24f, 0.24f, 0.24f);
                Geometry core = new Geometry("LavaSlimeCore", coreBox);
                core.setMaterial(accentMat);
                core.setLocalTranslation(0, 0.40f, 0);
                node.attachChild(core);
            } 
            // --- 4 НОВЫХ МОБА ---
            else if (mobType == 6) { // Пустотный Дух (Аметистовый биом)
                bodyMat.setColor("Color", new ColorRGBA(0.68f, 0.15f, 0.98f, 1.0f));
                accentMat.setColor("Color", new ColorRGBA(0.95f, 0.85f, 1.0f, 1.0f));

                Box bodyBox = new Box(0.25f, 0.25f, 0.25f);
                Geometry body = new Geometry("VoidSpriteBody", bodyBox);
                body.setMaterial(bodyMat);
                body.setLocalTranslation(0, 0.5f, 0);
                node.attachChild(body);

                Box crystalBox = new Box(0.08f, 0.25f, 0.08f);
                Geometry leftCryst = new Geometry("LC", crystalBox); leftCryst.setMaterial(accentMat); leftCryst.setLocalTranslation(-0.35f, 0.5f, 0);
                Geometry rightCryst = new Geometry("RC", crystalBox); rightCryst.setMaterial(accentMat); rightCryst.setLocalTranslation(0.35f, 0.5f, 0);
                node.attachChild(leftCryst);
                node.attachChild(rightCryst);

            } else if (mobType == 7) { // Ежик (Осенний биом)
                bodyMat.setColor("Color", new ColorRGBA(0.35f, 0.22f, 0.12f, 1.0f));
                accentMat.setColor("Color", new ColorRGBA(0.85f, 0.72f, 0.62f, 1.0f));

                Box bodyBox = new Box(0.22f, 0.20f, 0.35f);
                Geometry body = new Geometry("HedgehogSpikes", bodyBox);
                body.setMaterial(bodyMat);
                body.setLocalTranslation(0, 0.25f, 0);
                node.attachChild(body);

                Box headBox = new Box(0.12f, 0.10f, 0.12f);
                Geometry head = new Geometry("HedgehogHead", headBox);
                head.setMaterial(accentMat);
                head.setLocalTranslation(0, 0.22f, 0.40f);
                node.attachChild(head);

                Box footBox = new Box(0.04f, 0.05f, 0.04f);
                legL1 = new Geometry("Foot1", footBox); legL1.setMaterial(accentMat); legL1.setLocalTranslation(-0.15f, 0.03f, 0.18f);
                legR1 = new Geometry("Foot2", footBox); legR1.setMaterial(accentMat); legR1.setLocalTranslation(0.15f, 0.03f, 0.18f);
                node.attachChild(legL1);
                node.attachChild(legR1);

            } else if (mobType == 8) { // Белый медведь (Глетчер)
                bodyMat.setColor("Color", new ColorRGBA(0.98f, 0.98f, 0.98f, 1.0f));
                accentMat.setColor("Color", new ColorRGBA(0.1f, 0.1f, 0.1f, 1.0f));

                Box bodyBox = new Box(0.48f, 0.48f, 0.75f);
                Geometry body = new Geometry("BearBody", bodyBox);
                body.setMaterial(bodyMat);
                body.setLocalTranslation(0, 0.65f, 0);
                node.attachChild(body);

                Box headBox = new Box(0.28f, 0.28f, 0.28f);
                Geometry head = new Geometry("BearHead", headBox);
                head.setMaterial(bodyMat);
                head.setLocalTranslation(0, 0.88f, 0.85f);
                node.attachChild(head);

                Box noseBox = new Box(0.08f, 0.06f, 0.12f);
                Geometry nose = new Geometry("BearNose", noseBox);
                nose.setMaterial(accentMat);
                nose.setLocalTranslation(0, 0.80f, 1.15f);
                node.attachChild(nose);

                Box legBox = new Box(0.15f, 0.25f, 0.15f);
                legL1 = new Geometry("Leg1", legBox); legL1.setMaterial(bodyMat); legL1.setLocalTranslation(-0.3f, 0.18f, 0.45f);
                legR1 = new Geometry("Leg2", legBox); legR1.setMaterial(bodyMat); legR1.setLocalTranslation(0.3f, 0.18f, 0.45f);
                legL2 = new Geometry("Leg3", legBox); legL2.setMaterial(bodyMat); legL2.setLocalTranslation(-0.3f, 0.18f, -0.45f);
                legR2 = new Geometry("Leg4", legBox); legR2.setMaterial(bodyMat); legR2.setLocalTranslation(0.3f, 0.18f, -0.45f);

                node.attachChild(legL1);
                node.attachChild(legR1);
                node.attachChild(legL2);
                node.attachChild(legR2);

            } else if (mobType == 9) { // Мухоморовая корова (Мицелий)
                bodyMat.setColor("Color", new ColorRGBA(0.85f, 0.15f, 0.15f, 1.0f));
                accentMat.setColor("Color", new ColorRGBA(0.95f, 0.95f, 0.95f, 1.0f));

                Box bodyBox = new Box(0.44f, 0.38f, 0.68f);
                Geometry body = new Geometry("CowBody", bodyBox);
                body.setMaterial(bodyMat);
                body.setLocalTranslation(0, 0.55f, 0);
                node.attachChild(body);

                // Огромный гриб на спине
                Box mushroomBox = new Box(0.15f, 0.18f, 0.15f);
                Geometry shroom = new Geometry("Shroom", mushroomBox);
                shroom.setMaterial(bodyMat);
                shroom.setLocalTranslation(0, 1.05f, 0.15f);
                node.attachChild(shroom);

                Box legBox = new Box(0.11f, 0.22f, 0.11f);
                legL1 = new Geometry("Leg1", legBox); legL1.setMaterial(accentMat); legL1.setLocalTranslation(-0.25f, 0.11f, 0.45f);
                legR1 = new Geometry("Leg2", legBox); legR1.setMaterial(accentMat); legR1.setLocalTranslation(0.25f, 0.11f, 0.45f);
                legL2 = new Geometry("Leg3", legBox); legL2.setMaterial(accentMat); legL2.setLocalTranslation(-0.25f, 0.11f, -0.45f);
                legR2 = new Geometry("Leg4", legBox); legR2.setMaterial(accentMat); legR2.setLocalTranslation(0.25f, 0.11f, -0.45f);

                node.attachChild(legL1);
                node.attachChild(legR1);
                node.attachChild(legL2);
                node.attachChild(legR2);
            }
            // ================= 9 НОВЫХ МОБОВ (10-18) =================
            else if (mobType == 10) { // Житель деревни (NPC)
                bodyMat.setColor("Color", new ColorRGBA(0.42f, 0.28f, 0.20f, 1.0f));
                accentMat.setColor("Color", new ColorRGBA(0.78f, 0.62f, 0.50f, 1.0f));
                Geometry robe = new Geometry("VilRobe", new Box(0.28f, 0.55f, 0.22f));
                robe.setMaterial(bodyMat); robe.setLocalTranslation(0, 0.75f, 0); node.attachChild(robe);
                Geometry head = new Geometry("VilHead", new Box(0.22f, 0.22f, 0.22f));
                head.setMaterial(accentMat); head.setLocalTranslation(0, 1.45f, 0); node.attachChild(head);
                Geometry nose = new Geometry("VilNose", new Box(0.06f, 0.10f, 0.10f));
                nose.setMaterial(bodyMat); nose.setLocalTranslation(0, 1.42f, 0.24f); node.attachChild(nose);
                Box legBox = new Box(0.10f, 0.22f, 0.10f);
                legL1 = new Geometry("L1", legBox); legL1.setMaterial(bodyMat); legL1.setLocalTranslation(-0.13f, 0.20f, 0);
                legR1 = new Geometry("R1", legBox); legR1.setMaterial(bodyMat); legR1.setLocalTranslation(0.13f, 0.20f, 0);
                node.attachChild(legL1); node.attachChild(legR1);

            } else if (mobType == 11) { // Зомби (враждебный)
                bodyMat.setColor("Color", new ColorRGBA(0.28f, 0.52f, 0.30f, 1.0f));
                accentMat.setColor("Color", new ColorRGBA(0.20f, 0.38f, 0.55f, 1.0f));
                Geometry torso = new Geometry("ZTorso", new Box(0.28f, 0.42f, 0.20f));
                torso.setMaterial(accentMat); torso.setLocalTranslation(0, 0.75f, 0); node.attachChild(torso);
                Geometry head = new Geometry("ZHead", new Box(0.24f, 0.24f, 0.24f));
                head.setMaterial(bodyMat); head.setLocalTranslation(0, 1.35f, 0); node.attachChild(head);
                Box armBox = new Box(0.09f, 0.35f, 0.09f);
                Geometry armL = new Geometry("ZArmL", armBox); armL.setMaterial(bodyMat); armL.setLocalTranslation(-0.30f, 0.95f, 0.25f);
                Geometry armR = new Geometry("ZArmR", armBox); armR.setMaterial(bodyMat); armR.setLocalTranslation(0.30f, 0.95f, 0.25f);
                armL.rotate(-1.4f, 0, 0); armR.rotate(-1.4f, 0, 0);
                node.attachChild(armL); node.attachChild(armR);
                Box legBox = new Box(0.10f, 0.30f, 0.10f);
                legL1 = new Geometry("L1", legBox); legL1.setMaterial(accentMat); legL1.setLocalTranslation(-0.13f, 0.28f, 0);
                legR1 = new Geometry("R1", legBox); legR1.setMaterial(accentMat); legR1.setLocalTranslation(0.13f, 0.28f, 0);
                node.attachChild(legL1); node.attachChild(legR1);

            } else if (mobType == 12) { // Скелет (враждебный)
                bodyMat.setColor("Color", new ColorRGBA(0.90f, 0.90f, 0.86f, 1.0f));
                accentMat.setColor("Color", new ColorRGBA(0.30f, 0.30f, 0.30f, 1.0f));
                Geometry rib = new Geometry("SkRib", new Box(0.20f, 0.40f, 0.14f));
                rib.setMaterial(bodyMat); rib.setLocalTranslation(0, 0.78f, 0); node.attachChild(rib);
                Geometry head = new Geometry("SkHead", new Box(0.22f, 0.22f, 0.22f));
                head.setMaterial(bodyMat); head.setLocalTranslation(0, 1.32f, 0); node.attachChild(head);
                Box legBox = new Box(0.07f, 0.32f, 0.07f);
                legL1 = new Geometry("L1", legBox); legL1.setMaterial(bodyMat); legL1.setLocalTranslation(-0.10f, 0.30f, 0);
                legR1 = new Geometry("R1", legBox); legR1.setMaterial(bodyMat); legR1.setLocalTranslation(0.10f, 0.30f, 0);
                node.attachChild(legL1); node.attachChild(legR1);

            } else if (mobType == 13) { // Паук (враждебный)
                bodyMat.setColor("Color", new ColorRGBA(0.15f, 0.10f, 0.10f, 1.0f));
                accentMat.setColor("Color", new ColorRGBA(0.85f, 0.10f, 0.10f, 1.0f));
                Geometry abdomen = new Geometry("SpAbd", new Box(0.35f, 0.22f, 0.42f));
                abdomen.setMaterial(bodyMat); abdomen.setLocalTranslation(0, 0.30f, -0.15f); node.attachChild(abdomen);
                Geometry headS = new Geometry("SpHead", new Box(0.22f, 0.18f, 0.22f));
                headS.setMaterial(bodyMat); headS.setLocalTranslation(0, 0.28f, 0.35f); node.attachChild(headS);
                Geometry eyeL = new Geometry("SpEyeL", new Box(0.05f, 0.05f, 0.05f));
                eyeL.setMaterial(accentMat); eyeL.setLocalTranslation(-0.09f, 0.34f, 0.52f); node.attachChild(eyeL);
                Geometry eyeR = new Geometry("SpEyeR", new Box(0.05f, 0.05f, 0.05f));
                eyeR.setMaterial(accentMat); eyeR.setLocalTranslation(0.09f, 0.34f, 0.52f); node.attachChild(eyeR);
                Box legBox = new Box(0.30f, 0.04f, 0.04f);
                legL1 = new Geometry("L1", legBox); legL1.setMaterial(bodyMat); legL1.setLocalTranslation(-0.45f, 0.20f, 0.15f);
                legR1 = new Geometry("R1", legBox); legR1.setMaterial(bodyMat); legR1.setLocalTranslation(0.45f, 0.20f, 0.15f);
                legL2 = new Geometry("L2", legBox); legL2.setMaterial(bodyMat); legL2.setLocalTranslation(-0.45f, 0.20f, -0.25f);
                legR2 = new Geometry("R2", legBox); legR2.setMaterial(bodyMat); legR2.setLocalTranslation(0.45f, 0.20f, -0.25f);
                node.attachChild(legL1); node.attachChild(legR1); node.attachChild(legL2); node.attachChild(legR2);

            } else if (mobType == 14) { // Курица (мирная)
                bodyMat.setColor("Color", new ColorRGBA(0.96f, 0.96f, 0.96f, 1.0f));
                accentMat.setColor("Color", new ColorRGBA(0.95f, 0.62f, 0.05f, 1.0f));
                Geometry body = new Geometry("ChBody", new Box(0.20f, 0.20f, 0.26f));
                body.setMaterial(bodyMat); body.setLocalTranslation(0, 0.35f, 0); node.attachChild(body);
                Geometry head = new Geometry("ChHead", new Box(0.12f, 0.14f, 0.12f));
                head.setMaterial(bodyMat); head.setLocalTranslation(0, 0.58f, 0.15f); node.attachChild(head);
                Geometry beak = new Geometry("ChBeak", new Box(0.05f, 0.04f, 0.07f));
                beak.setMaterial(accentMat); beak.setLocalTranslation(0, 0.56f, 0.28f); node.attachChild(beak);
                Box legBox = new Box(0.03f, 0.12f, 0.03f);
                legL1 = new Geometry("L1", legBox); legL1.setMaterial(accentMat); legL1.setLocalTranslation(-0.08f, 0.12f, 0);
                legR1 = new Geometry("R1", legBox); legR1.setMaterial(accentMat); legR1.setLocalTranslation(0.08f, 0.12f, 0);
                node.attachChild(legL1); node.attachChild(legR1);

            } else if (mobType == 15) { // Свинья (мирная)
                bodyMat.setColor("Color", new ColorRGBA(0.92f, 0.60f, 0.62f, 1.0f));
                accentMat.setColor("Color", new ColorRGBA(0.80f, 0.48f, 0.50f, 1.0f));
                Geometry body = new Geometry("PgBody", new Box(0.35f, 0.28f, 0.50f));
                body.setMaterial(bodyMat); body.setLocalTranslation(0, 0.40f, 0); node.attachChild(body);
                Geometry head = new Geometry("PgHead", new Box(0.22f, 0.20f, 0.20f));
                head.setMaterial(bodyMat); head.setLocalTranslation(0, 0.45f, 0.55f); node.attachChild(head);
                Geometry snout = new Geometry("PgSnout", new Box(0.10f, 0.08f, 0.06f));
                snout.setMaterial(accentMat); snout.setLocalTranslation(0, 0.42f, 0.74f); node.attachChild(snout);
                Box legBox = new Box(0.08f, 0.16f, 0.08f);
                legL1 = new Geometry("L1", legBox); legL1.setMaterial(bodyMat); legL1.setLocalTranslation(-0.20f, 0.14f, 0.32f);
                legR1 = new Geometry("R1", legBox); legR1.setMaterial(bodyMat); legR1.setLocalTranslation(0.20f, 0.14f, 0.32f);
                legL2 = new Geometry("L2", legBox); legL2.setMaterial(bodyMat); legL2.setLocalTranslation(-0.20f, 0.14f, -0.32f);
                legR2 = new Geometry("R2", legBox); legR2.setMaterial(bodyMat); legR2.setLocalTranslation(0.20f, 0.14f, -0.32f);
                node.attachChild(legL1); node.attachChild(legR1); node.attachChild(legL2); node.attachChild(legR2);

            } else if (mobType == 16) { // Корова (мирная)
                bodyMat.setColor("Color", new ColorRGBA(0.30f, 0.22f, 0.16f, 1.0f));
                accentMat.setColor("Color", new ColorRGBA(0.95f, 0.95f, 0.92f, 1.0f));
                Geometry body = new Geometry("CoBody", new Box(0.42f, 0.36f, 0.62f));
                body.setMaterial(bodyMat); body.setLocalTranslation(0, 0.55f, 0); node.attachChild(body);
                Geometry head = new Geometry("CoHead", new Box(0.24f, 0.22f, 0.22f));
                head.setMaterial(bodyMat); head.setLocalTranslation(0, 0.62f, 0.70f); node.attachChild(head);
                Geometry muzzle = new Geometry("CoMuz", new Box(0.16f, 0.12f, 0.08f));
                muzzle.setMaterial(accentMat); muzzle.setLocalTranslation(0, 0.56f, 0.90f); node.attachChild(muzzle);
                Box legBox = new Box(0.10f, 0.22f, 0.10f);
                legL1 = new Geometry("L1", legBox); legL1.setMaterial(accentMat); legL1.setLocalTranslation(-0.24f, 0.20f, 0.42f);
                legR1 = new Geometry("R1", legBox); legR1.setMaterial(accentMat); legR1.setLocalTranslation(0.24f, 0.20f, 0.42f);
                legL2 = new Geometry("L2", legBox); legL2.setMaterial(accentMat); legL2.setLocalTranslation(-0.24f, 0.20f, -0.42f);
                legR2 = new Geometry("R2", legBox); legR2.setMaterial(accentMat); legR2.setLocalTranslation(0.24f, 0.20f, -0.42f);
                node.attachChild(legL1); node.attachChild(legR1); node.attachChild(legL2); node.attachChild(legR2);

            } else if (mobType == 17) { // Волк (нейтральный хищник)
                bodyMat.setColor("Color", new ColorRGBA(0.55f, 0.55f, 0.58f, 1.0f));
                accentMat.setColor("Color", new ColorRGBA(0.90f, 0.90f, 0.92f, 1.0f));
                Geometry body = new Geometry("WlBody", new Box(0.28f, 0.24f, 0.48f));
                body.setMaterial(bodyMat); body.setLocalTranslation(0, 0.42f, 0); node.attachChild(body);
                Geometry head = new Geometry("WlHead", new Box(0.18f, 0.18f, 0.20f));
                head.setMaterial(bodyMat); head.setLocalTranslation(0, 0.52f, 0.52f); node.attachChild(head);
                Geometry snout = new Geometry("WlSnout", new Box(0.08f, 0.08f, 0.12f));
                snout.setMaterial(accentMat); snout.setLocalTranslation(0, 0.48f, 0.70f); node.attachChild(snout);
                Box legBox = new Box(0.07f, 0.20f, 0.07f);
                legL1 = new Geometry("L1", legBox); legL1.setMaterial(accentMat); legL1.setLocalTranslation(-0.17f, 0.18f, 0.30f);
                legR1 = new Geometry("R1", legBox); legR1.setMaterial(accentMat); legR1.setLocalTranslation(0.17f, 0.18f, 0.30f);
                legL2 = new Geometry("L2", legBox); legL2.setMaterial(accentMat); legL2.setLocalTranslation(-0.17f, 0.18f, -0.30f);
                legR2 = new Geometry("R2", legBox); legR2.setMaterial(accentMat); legR2.setLocalTranslation(0.17f, 0.18f, -0.30f);
                node.attachChild(legL1); node.attachChild(legR1); node.attachChild(legL2); node.attachChild(legR2);

            } else if (mobType == 18) { // Крипер (враждебный, взрывается)
                bodyMat.setColor("Color", new ColorRGBA(0.30f, 0.78f, 0.30f, 1.0f));
                accentMat.setColor("Color", new ColorRGBA(0.10f, 0.10f, 0.10f, 1.0f));
                Geometry body = new Geometry("CrBody", new Box(0.26f, 0.56f, 0.20f));
                body.setMaterial(bodyMat); body.setLocalTranslation(0, 0.80f, 0); node.attachChild(body);
                Geometry head = new Geometry("CrHead", new Box(0.26f, 0.26f, 0.26f));
                head.setMaterial(bodyMat); head.setLocalTranslation(0, 1.50f, 0); node.attachChild(head);
                Geometry faceL = new Geometry("CrFaceL", new Box(0.06f, 0.08f, 0.03f));
                faceL.setMaterial(accentMat); faceL.setLocalTranslation(-0.10f, 1.54f, 0.26f); node.attachChild(faceL);
                Geometry faceR = new Geometry("CrFaceR", new Box(0.06f, 0.08f, 0.03f));
                faceR.setMaterial(accentMat); faceR.setLocalTranslation(0.10f, 1.54f, 0.26f); node.attachChild(faceR);
                Box legBox = new Box(0.11f, 0.14f, 0.11f);
                legL1 = new Geometry("L1", legBox); legL1.setMaterial(bodyMat); legL1.setLocalTranslation(-0.13f, 0.13f, 0.10f);
                legR1 = new Geometry("R1", legBox); legR1.setMaterial(bodyMat); legR1.setLocalTranslation(0.13f, 0.13f, 0.10f);
                legL2 = new Geometry("L2", legBox); legL2.setMaterial(bodyMat); legL2.setLocalTranslation(-0.13f, 0.13f, -0.10f);
                legR2 = new Geometry("R2", legBox); legR2.setMaterial(bodyMat); legR2.setLocalTranslation(0.13f, 0.13f, -0.10f);
                node.attachChild(legL1); node.attachChild(legR1); node.attachChild(legL2); node.attachChild(legR2);
            }

            node.setLocalTranslation(position);
        }

        private float getRadius() {
            return switch (mobType) {
                case 3, 8 -> 0.55f;
                case 13 -> 0.45f;
                case 15, 16 -> 0.40f;
                case 17 -> 0.32f;
                default -> 0.35f;
            };
        }

        private float getHeight() {
            return switch (mobType) {
                case 3, 8 -> 1.30f;
                case 2 -> 0.90f;
                case 10, 11 -> 1.70f;
                case 12, 18 -> 1.70f;
                case 17 -> 0.90f;
                default -> 0.60f;
            };
        }

        public void update(float tpf, World world, Vector3f playerPos) {
            if (world == null || playerPos == null || dead) return; // защита от NPE/двойного апдейта
            try {
            float distToPlayer = position.distance(playerPos);
            boolean isHostile = (mobType == 3 || mobType == 5 || mobType == 6
                    || mobType == 11 || mobType == 12 || mobType == 13 || mobType == 18);
            // Волк нейтрален: атакует, только если его ударили
            boolean wolfAngry = (mobType == 17 && angryTimer > 0);
            boolean effectiveHostile = isHostile || wolfAngry;

            if (effectiveHostile) {
                if (distToPlayer < 18.0f) {
                    aiState = 1; 
                    targetDirection.set(playerPos).subtractLocal(position);
                    targetDirection.y = 0;
                    targetDirection.normalizeLocal();
                } else {
                    aiState = 0; 
                }
            } else {
                if (distToPlayer < 6.0f) {
                    aiState = 1; 
                    targetDirection.set(position).subtractLocal(playerPos); 
                    targetDirection.y = 0;
                    targetDirection.normalizeLocal();
                    panicTimer = 2.0f; 
                } else {
                    if (panicTimer > 0) {
                        panicTimer -= tpf;
                    } else {
                        aiState = 0; 
                    }
                }
            }

            if (aiState == 0) {
                wanderTimer -= tpf;
                if (wanderTimer <= 0) {
                    if (Math.random() < 0.4) {
                        targetDirection.set(0, 0, 0);
                        wanderTimer = 1.0f + (float) (Math.random() * 2.0f);
                    } else {
                        float angle = (float) (Math.random() * FastMath.TWO_PI);
                        targetDirection.set(FastMath.cos(angle), 0, FastMath.sin(angle)).normalizeLocal();
                        wanderTimer = 3.0f + (float) (Math.random() * 4.0f);
                    }
                }
            }

            if (targetDirection.lengthSquared() > 0.001f) {
                Quaternion targetRot = new Quaternion().lookAt(targetDirection, Vector3f.UNIT_Y);
                node.getLocalRotation().slerp(targetRot, tpf * 6.0f);
            }

            float baseSpeed = switch (mobType) {
                case 0 -> 0.85f;
                case 1 -> 1.30f;
                case 2 -> 0.65f;
                case 10 -> 0.45f; // житель ходит медленно
                case 11 -> 1.05f; // зомби
                case 12 -> 1.25f; // скелет
                case 13 -> 1.40f; // паук быстрый
                case 14 -> 0.80f; // курица
                case 15 -> 0.85f; // свинья
                case 16 -> 0.75f; // корова
                case 17 -> 1.45f; // волк
                case 18 -> 0.95f; // крипер
                case 4 -> 1.50f; 
                case 5 -> 0.50f; 
                case 8 -> 1.10f; // Медведь чуть быстрее коровы
                default -> 0.55f; 
            };

            float speed = baseSpeed;
            if (aiState == 1) {
                speed = effectiveHostile ? baseSpeed * 1.6f : baseSpeed * 1.9f; 
            }

            velocity.x = targetDirection.x * speed;
            velocity.z = targetDirection.z * speed;
            velocity.y -= 18.0f * tpf; 

            moveMob(velocity.mult(tpf), world);

            float actualHorizSpeed = new Vector3f(velocity.x, 0, velocity.z).length();
            if (actualHorizSpeed > 0.1f) {
                animationTimer += tpf * (mobType == 1 ? 14.0f : mobType == 4 ? 13.0f : 7.0f) * (aiState == 1 ? 1.4f : 1.0f);
            } else {
                animationTimer = FastMath.interpolateLinear(tpf * 8.0f, animationTimer, 0f);
            }
            float swing = FastMath.sin(animationTimer) * 0.45f;

            if (legL1 != null && legR1 != null) {
                if (mobType == 0 || mobType == 3 || mobType == 4 || mobType == 8 || mobType == 9) { 
                    legL1.setLocalRotation(new Quaternion().fromAngleAxis(swing, Vector3f.UNIT_X));
                    legR1.setLocalRotation(new Quaternion().fromAngleAxis(-swing, Vector3f.UNIT_X));
                    if (legL2 != null) legL2.setLocalRotation(new Quaternion().fromAngleAxis(-swing, Vector3f.UNIT_X));
                    if (legR2 != null) legR2.setLocalRotation(new Quaternion().fromAngleAxis(swing, Vector3f.UNIT_X));
                } else if (mobType == 1) { 
                    legL1.setLocalRotation(new Quaternion().fromAngleAxis(swing, Vector3f.UNIT_Z));
                    legR1.setLocalRotation(new Quaternion().fromAngleAxis(-swing, Vector3f.UNIT_Z));
                    legL2.setLocalRotation(new Quaternion().fromAngleAxis(-swing, Vector3f.UNIT_Z));
                    legR2.setLocalRotation(new Quaternion().fromAngleAxis(swing, Vector3f.UNIT_Z));
                } else if (mobType == 10 || mobType == 11 || mobType == 12 || mobType == 14 || mobType == 15 || mobType == 16 || mobType == 17 || mobType == 18) { 
                    // двуногие/четвероногие новые мобы: качаем ноги вперёд-назад
                    legL1.setLocalRotation(new Quaternion().fromAngleAxis(swing, Vector3f.UNIT_X));
                    legR1.setLocalRotation(new Quaternion().fromAngleAxis(-swing, Vector3f.UNIT_X));
                    if (legL2 != null) legL2.setLocalRotation(new Quaternion().fromAngleAxis(-swing, Vector3f.UNIT_X));
                    if (legR2 != null) legR2.setLocalRotation(new Quaternion().fromAngleAxis(swing, Vector3f.UNIT_X));
                } else if (mobType == 13) { 
                    // паук: растопыриваем боковые ноги
                    legL1.setLocalRotation(new Quaternion().fromAngleAxis(swing, Vector3f.UNIT_Z));
                    legR1.setLocalRotation(new Quaternion().fromAngleAxis(-swing, Vector3f.UNIT_Z));
                    if (legL2 != null) legL2.setLocalRotation(new Quaternion().fromAngleAxis(-swing, Vector3f.UNIT_Z));
                    if (legR2 != null) legR2.setLocalRotation(new Quaternion().fromAngleAxis(swing, Vector3f.UNIT_Z));
                } else if (mobType == 2 || mobType == 7) { 
                    node.setLocalRotation(node.getLocalRotation().mult(new Quaternion().fromAngleAxis(swing * 0.12f, Vector3f.UNIT_Z)));
                    legL1.setLocalRotation(new Quaternion().fromAngleAxis(swing * 0.8f, Vector3f.UNIT_X));
                    legR1.setLocalRotation(new Quaternion().fromAngleAxis(-swing * 0.8f, Vector3f.UNIT_X));
                }
            } else if (mobType == 5) { 
                float squishY = 1.0f + FastMath.sin(animationTimer) * 0.15f;
                float squishXZ = 1.0f - FastMath.sin(animationTimer) * 0.10f;
                node.setLocalScale(squishXZ, squishY, squishXZ);
            }

            // --- БОЁВКА: урон игроку при контакте (только хостилы) ---
            if (attackCooldown > 0f) attackCooldown -= tpf;
            if (hurtFlash > 0f) {
                hurtFlash -= tpf;
                if (bodyMatRef != null) {
                    // красная вспышка при получении урона, затухает
                    float f = Math.max(0f, hurtFlash);
                    bodyMatRef.setColor("Color", new ColorRGBA(1f, 1f - f * 0.7f, 1f - f * 0.7f, 1f));
                }
            }

            if (effectiveHostile && ownerApp != null && !ownerApp.player.isDead && ownerApp.player.isGodMode == false) {
                float reach = (mobType == 3) ? 2.6f : 1.8f; // голем бьёт издалека
                if (distToPlayer < reach && attackCooldown <= 0f) {
                    if (mobType == 18) {
                        // КРИПЕР: взрывается при контакте
                        if (ownerApp.world != null) {
                            ownerApp.world.explodeAt(position.clone(), 4.0f, 30.0f);
                        }
                        this.health = 0; this.dead = true; ownerApp.mobManager.removeMob(this);
                    } else {
                        float dmg = (mobType == 3) ? 6.0f : (mobType == 6 ? 4.0f : (mobType == 11 || mobType == 12 || mobType == 13 ? 3.5f : 3.0f));
                        ownerApp.player.damage(dmg);
                        ownerApp.soundManager.hurt();
                    }
                    attackCooldown = 1.0f; // 1 удар в секунду
                    // небольшой отскок игрока от моба
                    Vector3f knock = ownerApp.player.pos.subtract(position).normalizeLocal();
                    knock.y = 0.3f;
                    ownerApp.player.velocity.addLocal(knock.mult(4.0f));
                    }
                }
            } catch (Throwable t) {
                System.err.println("[Mob] тип " + mobType + " упал в update: " + t);
            }
        }

        public boolean damage(float amount) {
            if (dead) return false;
            health -= amount;
            hurtFlash = 1.0f;
            if (health <= 0f) {
                dead = true;
                return true; // убит
            }
            return false;
        }

        public boolean isDead() { return dead; }

        private void moveMob(Vector3f offset, World world) {
            position.x += offset.x;
            if (checkCollision(position, world)) {
                position.x -= offset.x;
                if (onGround) {
                    velocity.y = 5.8f;
                    onGround = false;
                } else {
                    wanderTimer = 0f; 
                }
            }

            position.z += offset.z;
            if (checkCollision(position, world)) {
                position.z -= offset.z;
                if (onGround) {
                    velocity.y = 5.8f;
                    onGround = false;
                } else {
                    wanderTimer = 0f;
                }
            }

            position.y += offset.y;
            if (checkCollision(position, world)) {
                if (offset.y < 0) {
                    onGround = true;
                }
                position.y -= offset.y;
                velocity.y = 0;
            } else {
                if (offset.y != 0) {
                    Vector3f checkGroundPos = position.subtract(0, 0.05f, 0);
                    onGround = checkCollision(checkGroundPos, world);
                }
            }

            if (position.y < -5.0f) {
                position.y = world.getMaxHeightAt((int) Math.floor(position.x), (int) Math.floor(position.z)) + 2.0f;
                velocity.set(0, 0, 0);
            }

            node.setLocalTranslation(position);
        }

        private boolean checkCollision(Vector3f checkPos, World world) {
            float r = getRadius();
            float h = getHeight();

            float[] xs = {checkPos.x - r, checkPos.x + r};
            float[] ys = {checkPos.y, checkPos.y + h / 2.0f, checkPos.y + h};
            float[] zs = {checkPos.z - r, checkPos.z + r};

            for (float cx : xs) {
                for (float cy : ys) {
                    for (float cz : zs) {
                        // ИСПРАВЛЕНО: та же причина, что и у игрока — блоки занимают [i, i+1),
                        // индекс блока нужно получать через floor(), иначе моб иногда
                        // проваливается сквозь пол/стены.
                        int bx = (int) Math.floor(cx);
                        int by = (int) Math.floor(cy);
                        int bz = (int) Math.floor(cz);
                        if (world.getBlockAt(bx, by, bz) != 0) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        public Node getNode() {
            return node;
        }
    }
}