package com.mygame;

import com.jme3.asset.AssetManager;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;

public class InteractionHandler {

    private Node selectionOutline;
    private float outlineScale = 0f;
    private final Vector3f targetOutlinePos = new Vector3f();
    private Material boxMat;
    private Material wireMat;
    private float pulse = 0f;

    private boolean isMining = false;
    private final Vector3f currentMiningBlock = new Vector3f();
    private float miningProgress = 0.0f;
    private float breakCooldown = 0.0f;   // ПАУЗА между сломанными блоками (кулдаун)
    private boolean leftClickHeld = false;

    private SoundManager soundManager;
    public void setSoundManager(SoundManager sm) { this.soundManager = sm; }

    private Main app;
    public void setApp(Main app) { this.app = app; }

    public void init(AssetManager assetManager, Node rootNode) {
        createSelectionOutline(assetManager, rootNode);
    }

    // ИСПРАВЛЕНО: раньше проверялось geom.getParent().getName().equals("HandNode"),
    // но реальные узлы рук называются "LeftArm"/"RightArm"/"Pickaxe" и лежат
    // под "HandsParentNode", поэтому проверка никогда не срабатывала и луч мог
    // попадать в модель руки/кирки вместо блоков мира. Теперь проверяем всю
    // цепочку родителей.
    private boolean isPartOfHand(Geometry geom) {
        com.jme3.scene.Spatial s = geom;
        while (s != null) {
            if ("HandsParentNode".equals(s.getName())) {
                return true;
            }
            s = s.getParent();
        }
        return false;
    }

    public void setLeftClickHeld(boolean held) {
        this.leftClickHeld = held;
        if (held) {
            // ЛКМ = удар по мобам перед игроком (боёвка)
            if (soundManager != null && app != null) app.attackMobs();
        }
        if (!held) {
            isMining = false;
            miningProgress = 0f;
        }
    }

    public void update(float tpf, Camera cam, Main app) {
        if (!app.isInventoryOpen && !app.player.isDead && !app.getStateManager().hasState(app.getStateManager().getState(DevMenuState.class))) {
            updateBlockSelection(tpf, cam, app.getRootNode(), app.world);
            updateMiningLogic(tpf, cam, app);
            // Передаём руке состояние копания -> непрерывный swing
            app.hand.setMining(isMining);
        } else {
            if (selectionOutline != null) selectionOutline.setLocalScale(0);
            isMining = false;
            app.hand.setMining(false);
        }
    }

    private void updateBlockSelection(float tpf, Camera cam, Node rootNode, World world) {
        CollisionResults results = new CollisionResults();
        Ray ray = new Ray(cam.getLocation(), cam.getDirection());
        rootNode.collideWith(ray, results);

        boolean hitDetected = false;
        if (results.size() > 0) {
            CollisionResult closest = null;
            for (CollisionResult r : results) {
                if (!r.getGeometry().getName().startsWith("Sel") && !isPartOfHand(r.getGeometry())) {
                    closest = r;
                    break;
                }
            }

            if (closest != null && closest.getDistance() < 6.0f) {
                Vector3f contactPoint = closest.getContactPoint();
                Vector3f contactNormal = closest.getContactNormal();

                Vector3f blockPos = contactPoint.subtract(contactNormal.mult(0.1f));
                int bx = (int) Math.floor(blockPos.x);
                int by = (int) Math.floor(blockPos.y);
                int bz = (int) Math.floor(blockPos.z);

                if (world.getBlockAt(bx, by, bz) != 0) {
                    // Блок в мире центрирован на +0.5 по каждой оси
                    // (Chunk рисует от целой координаты), поэтому
                    // центр хайлайтера ставим туда же.
                    targetOutlinePos.set(bx + 0.5f, by + 0.5f, bz + 0.5f);
                    hitDetected = true;
                }
            }
        }

        if (hitDetected) {
            selectionOutline.getLocalTranslation().interpolateLocal(targetOutlinePos, tpf * 24f);
            outlineScale = FastMath.interpolateLinear(tpf * 16f, outlineScale, 1.0f);
        } else {
            outlineScale = FastMath.interpolateLinear(tpf * 16f, outlineScale, 0.0f);
        }
        selectionOutline.setLocalScale(outlineScale);

        // Плавная «пульсация» яркости — хайлайтер заметен,
        // но не режет глаз. Только когда он видим (scale > 0).
        pulse += tpf;
        if (outlineScale > 0.02f && boxMat != null && wireMat != null) {
            float a = 0.55f + 0.45f * (0.5f + 0.5f * FastMath.sin(pulse * 3.0f));
            float boxA = 0.10f + 0.12f * (0.5f + 0.5f * FastMath.sin(pulse * 3.0f));
            // гасим всё при исчезновении
            float fade = FastMath.clamp(outlineScale, 0.0f, 1.0f);
            wireMat.setColor("Color", new ColorRGBA(0.55f, 0.85f, 1.0f, a * fade));
            boxMat.setColor("Color", new ColorRGBA(0.20f, 0.65f, 1.0f, boxA * fade));
        }
    }

    private void updateMiningLogic(float tpf, Camera cam, Main app) {
        if (!leftClickHeld) {
            isMining = false;
            miningProgress = 0f;
            breakCooldown = 0f;
            return;
        }

        CollisionResults results = new CollisionResults();
        Ray ray = new Ray(cam.getLocation(), cam.getDirection());
        app.getRootNode().collideWith(ray, results);

        if (results.size() > 0) {
            CollisionResult closest = null;
            for (CollisionResult r : results) {
                if (!r.getGeometry().getName().startsWith("Sel") && !isPartOfHand(r.getGeometry())) {
                    closest = r;
                    break;
                }
            }

            if (closest != null && closest.getDistance() < 6.0f) {
                Vector3f contactPoint = closest.getContactPoint();
                Vector3f contactNormal = closest.getContactNormal();
                Vector3f blockPos = contactPoint.subtract(contactNormal.mult(0.1f));

                int bx = (int) Math.floor(blockPos.x);
                int by = (int) Math.floor(blockPos.y);
                int bz = (int) Math.floor(blockPos.z);

                byte type = app.world.getBlockAt(bx, by, bz);
                if (type == 0) {
                    isMining = false;
                    return;
                }

                // ИСПРАВЛЕНО: Активация TNT
                if (type == 31) {
                    app.world.createExplosion(bx, by, bz, 4.5f, app);
                    app.hand.triggerPunch();
                    isMining = false;
                    miningProgress = 0.0f;
                    leftClickHeld = false;
                    return;
                }

                // ИСПРАВЛЕНО: единый кулдаун между блоками.
                // Без него при зажатой ЛКМ блоки летели "потоком".
                if (breakCooldown > 0.0f) {
                    breakCooldown -= tpf;
                    return;
                }

                Vector3f target = new Vector3f(bx, by, bz);
                if (!isMining || !currentMiningBlock.equals(target)) {
                    isMining = true;
                    currentMiningBlock.set(target);
                    miningProgress = 0.0f;
                }

                if (app.world.isCreative()) {
                    breakBlockDirectly(bx, by, bz, type, app);
                    isMining = false;
                    breakCooldown = 0.15f;   // краткая пауза и в креативе
                    return;
                }

                // Твёрдость выше, чем раньше — ломание ощутимое,
                // а не мгновенное. Камень ~1.4с, трава/дерево ~0.55с.
                float blockHardness = switch (type) {
                    case 3, 13, 19, 4, 5 -> 1.6f;
                    case 6, 12, 15, 20, 25 -> 1.1f;
                    case 1, 2, 8, 10, 23 -> 0.55f;
                    case 7, 9, 11 -> 0.9f;
                    default -> 0.7f;
                };

                byte activeTool = app.player.inventory[app.getSelectedSlot()].blockType;
                float multiplier = 1.0f;

                if (activeTool == 29) {
                    if (type == 3 || type == 13 || type == 19 || type == 4 || type == 5) multiplier = 6.0f;
                } else if (activeTool == 30) {
                    if (type == 1 || type == 2 || type == 8 || type == 10 || type == 23) multiplier = 6.0f;
                } else if (activeTool == 26) {
                    if (type == 6 || type == 12 || type == 15 || type == 20 || type == 25) multiplier = 5.0f;
                }

                miningProgress += tpf * (multiplier / blockHardness);

                if (FastMath.nextRandomFloat() > 0.75f) {
                    app.particleManager.spawnBreakParticles(bx, by, bz, type, app.blockMaterials);
                    app.hand.triggerPunch();
                }

                if (miningProgress >= 1.0f) {
                    breakBlockDirectly(bx, by, bz, type, app);
                    isMining = false;
                    miningProgress = 0.0f;
                    breakCooldown = 0.22f;   // пауза перед следующим блоком
                }
            }
        }
    }

    private void breakBlockDirectly(int bx, int by, int bz, byte type, Main app) {
        app.world.setBlockAt(bx, by, bz, (byte) 0);
        app.particleManager.spawnBreakParticles(bx, by, bz, type, app.blockMaterials);
        if (soundManager != null) soundManager.breakBlock();
        if (!app.world.isCreative()) {
            app.itemDropManager.spawnDroppedItemAt(bx, by + 0.2f, bz, type, false, Vector3f.ZERO, app.blockMaterials);
        }
        app.hand.triggerPunch();
        if (app.achievements != null) app.achievements.unlock("FIRST_MINE", "First Mine!");
    }

    public void triggerPlaceOrOpenBlock(Camera cam, Main app) {
        CollisionResults results = new CollisionResults();
        Ray ray = new Ray(cam.getLocation(), cam.getDirection());
        app.getRootNode().collideWith(ray, results);

        if (results.size() > 0) {
            CollisionResult closest = null;
            for (CollisionResult r : results) {
                if (!r.getGeometry().getName().startsWith("Sel") && !isPartOfHand(r.getGeometry())) {
                    closest = r;
                    break;
                }
            }

            if (closest != null && closest.getDistance() < 6.0f) {
                Vector3f contactPoint = closest.getContactPoint();
                Vector3f contactNormal = closest.getContactNormal();

                Vector3f clickedPos = contactPoint.subtract(contactNormal.mult(0.1f));
                int cx = (int) Math.floor(clickedPos.x);
                int cy = (int) Math.floor(clickedPos.y);
                int cz = (int) Math.floor(clickedPos.z);
                byte clickedType = app.world.getBlockAt(cx, cy, cz);

                if (clickedType == 25 && !app.player.isGodMode) {
                    app.ui.isChestOpen = true;
                    app.ui.currentChestCoords = cx + "," + cy + "," + cz;
                    app.openInventory();
                    return;
                }

                Vector3f placePos = contactPoint.add(contactNormal.mult(0.1f));
                int px = (int) Math.floor(placePos.x);
                int py = (int) Math.floor(placePos.y);
                int pz = (int) Math.floor(placePos.z);

                byte blockToPlace = app.player.inventory[app.getSelectedSlot()].blockType;
                if (blockToPlace == 0) return;

                if (blockToPlace == 24 && app.player.hunger < 20.0f) {
                    // ЕДА: сначала насыщение (saturation), потом голод.
                    // Здоровье регенится само через saturation в updateSurvivalStats.
                    app.player.hunger = Math.min(20.0f, app.player.hunger + 4.0f);
                    app.player.saturation = Math.min(app.player.maxSaturation, app.player.saturation + 8.0f);
                    app.player.speedBoostTimer = 12.0f; // эффект скорости от еды
                    if (soundManager != null) soundManager.eat();
                    consumeItemInHand(app);
                    return;
                }

                // ЯДОВИТАЯ еда (блок 14): отравление вместо насыщения
                if (blockToPlace == 14) {
                    app.player.poison = 8.0f;
                    if (soundManager != null) soundManager.eat();
                    consumeItemInHand(app);
                    return;
                }

                if (blockToPlace >= 100 && blockToPlace <= 109) {
                    app.mobManager.spawnMobAtBiome(app.getAssetManager(), new Vector3f(px, py + 0.6f, pz), blockToPlace - 100);
                    app.hand.triggerPunch();
                    if (soundManager != null) soundManager.placeBlock();
                    if (!app.world.isCreative()) consumeItemInHand(app);
                    return;
                }

                if (py >= 0 && py < Chunk.SIZE_Y) {
                    if (app.world.getBlockAt(px, py, pz) == 0 && !app.player.checkCollision(new Vector3f(px, py, pz), app.world)) {
                        app.world.setBlockAt(px, py, pz, blockToPlace); 
                        app.hand.triggerPunch();
                        if (soundManager != null) soundManager.placeBlock();
                        if (!app.world.isCreative()) consumeItemInHand(app);
                        if (app.achievements != null) app.achievements.unlock("FIRST_BUILD", "Builder!");
                    }
                }
            }
        }
    }

    private void consumeItemInHand(Main app) {
        app.player.inventory[app.getSelectedSlot()].count--;
        if (app.player.inventory[app.getSelectedSlot()].count <= 0) {
            app.player.inventory[app.getSelectedSlot()].blockType = 0;
        }
        app.syncHotbarArray();
        app.ui.updateHotbarIcon(app.getAssetManager(), app.getSelectedSlot(), app.player.inventory[app.getSelectedSlot()].blockType);
    }

    private com.jme3.scene.Mesh createWireframeCubeMesh() {
        com.jme3.scene.Mesh mesh = new com.jme3.scene.Mesh();
        mesh.setMode(com.jme3.scene.Mesh.Mode.Lines);
        float[] vertices = {
            -0.5f, -0.5f, -0.5f,  0.5f, -0.5f, -0.5f,  0.5f,  0.5f, -0.5f, -0.5f,  0.5f, -0.5f,
            -0.5f, -0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f
        };
        short[] indices = {
            0, 1,  1, 2,  2, 3,  3, 0,
            4, 5,  5, 6,  6, 7,  7, 4,
            0, 4,  1, 5,  2, 6,  3, 7
        };
        mesh.setBuffer(com.jme3.scene.VertexBuffer.Type.Position, 3, vertices);
        mesh.setBuffer(com.jme3.scene.VertexBuffer.Type.Index, 1, indices); 
        mesh.updateBound();
        mesh.setStatic();
        return mesh;
    }

    private void createSelectionOutline(AssetManager assetManager, Node rootNode) {
        selectionOutline = new Node("SelectionOutline");

        // Полупрозрачный «короб» — виден с любого угла обзора,
        // даже если wireframe-линии тонкие / не рендерятся.
        com.jme3.scene.Mesh boxMesh = new com.jme3.scene.shape.Box(0.5f, 0.5f, 0.5f);
        Geometry box = new Geometry("SelBox", boxMesh);
        boxMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        boxMat.setColor("Color", new ColorRGBA(0.20f, 0.65f, 1.0f, 0.18f));
        boxMat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        boxMat.getAdditionalRenderState().setDepthWrite(false);
        box.setMaterial(boxMat);
        box.setShadowMode(RenderQueue.ShadowMode.Off);
        selectionOutline.attachChild(box);

        // Яркий контур поверх — чёткая обводка блока.
        com.jme3.scene.Mesh wireMesh = createWireframeCubeMesh();
        Geometry wire = new Geometry("SelWire", wireMesh);
        wireMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        wireMat.setColor("Color", new ColorRGBA(0.55f, 0.85f, 1.0f, 0.95f));
        wireMat.getAdditionalRenderState().setLineWidth(4.0f);
        wire.setMaterial(wireMat);
        wire.setShadowMode(RenderQueue.ShadowMode.Off);
        selectionOutline.attachChild(wire);

        selectionOutline.setLocalScale(1.005f);
        rootNode.attachChild(selectionOutline);
        selectionOutline.setLocalScale(0);
    }
}