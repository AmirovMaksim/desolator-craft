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

    public void spawnMobAtBiome(AssetManager assetManager, Vector3f position, int biome) {
        Mob mob = new Mob(assetManager, position, biome);
        mob.ownerApp = this.app; // связываем моба с игрой для урона игроку
        mobsNode.attachChild(mob.getNode());
        mobs.add(mob);
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
        for (Mob mob : mobs) {
            mob.update(tpf, world, playerPos);
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

            node.setLocalTranslation(position);
        }

        private float getRadius() {
            return switch (mobType) {
                case 3, 8 -> 0.55f; 
                default -> 0.35f;
            };
        }

        private float getHeight() {
            return switch (mobType) {
                case 3, 8 -> 1.30f; 
                case 2 -> 0.90f; 
                default -> 0.60f;
            };
        }

        public void update(float tpf, World world, Vector3f playerPos) {
            float distToPlayer = position.distance(playerPos);
            boolean isHostile = (mobType == 3 || mobType == 5 || mobType == 6);

            if (isHostile) {
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
                case 4 -> 1.50f; 
                case 5 -> 0.50f; 
                case 8 -> 1.10f; // Медведь чуть быстрее коровы
                default -> 0.55f; 
            };

            float speed = baseSpeed;
            if (aiState == 1) {
                speed = isHostile ? baseSpeed * 1.6f : baseSpeed * 1.9f; 
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

            if (isHostile && ownerApp != null && !ownerApp.player.isDead && ownerApp.player.isGodMode == false) {
                float reach = (mobType == 3) ? 2.6f : 1.8f; // голем бьёт издалека
                if (distToPlayer < reach && attackCooldown <= 0f) {
                    float dmg = (mobType == 3) ? 6.0f : (mobType == 6 ? 4.0f : 3.0f);
                    ownerApp.player.damage(dmg);
                    ownerApp.soundManager.hurt();
                    attackCooldown = 1.0f; // 1 удар в секунду
                    // небольшой отскок игрока от моба
                    Vector3f knock = ownerApp.player.pos.subtract(position).normalizeLocal();
                    knock.y = 0.3f;
                    ownerApp.player.velocity.addLocal(knock.mult(4.0f));
                }
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