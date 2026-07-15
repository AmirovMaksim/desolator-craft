package com.mygame;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;

public class FirstPersonHand {
    private Node parentNode;
    private Node leftArmNode;
    private Node rightArmNode;

    private float punchTime = 0.0f;
    private boolean isPunching = false;
    private float animationTime = 0.0f;
    private boolean isMining = false;   // непрерывный swing при копании

    public void init(AssetManager assetManager, Node guiNode) {
        parentNode = new Node("HandsParentNode");

        // Текстуры кожи и одежды
        com.jme3.texture.Texture2D skinTex = ProceduralTextureGenerator.createProceduralTexture(new ColorRGBA(0.95f, 0.72f, 0.62f, 1.0f), false);
        Material skinMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        skinMat.setTexture("ColorMap", skinTex);
        skinMat.getAdditionalRenderState().setDepthTest(false);

        com.jme3.texture.Texture2D sleeveTex = ProceduralTextureGenerator.createProceduralTexture(new ColorRGBA(0.35f, 0.58f, 0.82f, 1.0f), false);
        Material sleeveMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        sleeveMat.setTexture("ColorMap", sleeveTex);
        sleeveMat.getAdditionalRenderState().setDepthTest(false);

        // --- ЛЕВАЯ РУКА (Left Arm) ---
        leftArmNode = new Node("LeftArm");
        
        // Предплечье левой руки
        Box forearmBox = new Box(0.045f, 0.045f, 0.22f);
        Geometry leftForearm = new Geometry("LeftForearm", forearmBox);
        leftForearm.setMaterial(skinMat);
        leftForearm.setQueueBucket(RenderQueue.Bucket.Gui);
        leftArmNode.attachChild(leftForearm);

        // Рукав левой руки
        Box sleeveBox = new Box(0.05f, 0.05f, 0.12f);
        Geometry leftSleeve = new Geometry("LeftSleeve", sleeveBox);
        leftSleeve.setMaterial(sleeveMat);
        leftSleeve.setLocalTranslation(0, 0, 0.12f);
        leftSleeve.setQueueBucket(RenderQueue.Bucket.Gui);
        leftArmNode.attachChild(leftSleeve);

        // Ладонь левой руки
        Box fistBox = new Box(0.042f, 0.038f, 0.05f);
        Geometry leftFist = new Geometry("LeftFist", fistBox);
        leftFist.setMaterial(skinMat);
        leftFist.setLocalTranslation(0, 0, -0.24f);
        leftFist.setQueueBucket(RenderQueue.Bucket.Gui);
        leftArmNode.attachChild(leftFist);

        parentNode.attachChild(leftArmNode);


        // --- ПРАВАЯ РУКА (Right Arm) ---
        rightArmNode = new Node("RightArm");

        // Предплечье правой руки
        Geometry rightForearm = new Geometry("RightForearm", forearmBox);
        rightForearm.setMaterial(skinMat);
        rightForearm.setQueueBucket(RenderQueue.Bucket.Gui);
        rightArmNode.attachChild(rightForearm);

        // Рукав правой руки
        Geometry rightSleeve = new Geometry("RightSleeve", sleeveBox);
        rightSleeve.setMaterial(sleeveMat);
        rightSleeve.setLocalTranslation(0, 0, 0.12f);
        rightSleeve.setQueueBucket(RenderQueue.Bucket.Gui);
        rightArmNode.attachChild(rightSleeve);

        // Ладонь правой руки
        Geometry rightFist = new Geometry("RightFist", fistBox);
        rightFist.setMaterial(skinMat);
        rightFist.setLocalTranslation(0, 0, -0.24f);
        rightFist.setQueueBucket(RenderQueue.Bucket.Gui);
        rightArmNode.attachChild(rightFist);

        // --- МОДЕЛЬ КИРКИ В ПРАВОЙ РУКЕ ---
        Node pickaxeNode = new Node("Pickaxe");

        // Золотая деревянная рукоять
        Box handleBox = new Box(0.012f, 0.012f, 0.28f);
        Geometry handle = new Geometry("PickaxeHandle", handleBox);
        Material handleMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        handleMat.setColor("Color", new ColorRGBA(0.92f, 0.65f, 0.12f, 1.0f)); 
        handleMat.getAdditionalRenderState().setDepthTest(false);
        handle.setMaterial(handleMat);
        handle.setQueueBucket(RenderQueue.Bucket.Gui);
        pickaxeNode.attachChild(handle);

        // Железное лезвие (двухсторонний обух)
        Box pickBox = new Box(0.12f, 0.016f, 0.024f);
        Geometry pick = new Geometry("PickaxeHead", pickBox);
        Material metalMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        metalMat.setColor("Color", new ColorRGBA(0.72f, 0.72f, 0.75f, 1.0f)); 
        metalMat.getAdditionalRenderState().setDepthTest(false);
        pick.setMaterial(metalMat);
        pick.setLocalTranslation(0, 0, -0.24f); 
        pick.setQueueBucket(RenderQueue.Bucket.Gui);
        pickaxeNode.attachChild(pick);

        // Позиционирование кирки относительно правой ладони
        pickaxeNode.setLocalTranslation(0f, 0.08f, -0.24f);
        pickaxeNode.setLocalRotation(new Quaternion().fromAngles(0.35f, 0, FastMath.HALF_PI));
        rightArmNode.attachChild(pickaxeNode);

        parentNode.attachChild(rightArmNode);
        parentNode.setQueueBucket(RenderQueue.Bucket.Gui);
        guiNode.attachChild(parentNode);
    }

    public void update(float tpf, Camera cam, Player player, PlayerCamera playerCam, boolean isInventoryOpen, float screenW, float screenH) {
        if (parentNode == null) return;

        animationTime += tpf;

        // Рука в ПРАВОМ НИЖНЕМ УГЛУ экрана (guiNode, screen-space).
        // Базовая точка привязки — низ правого угла.
        float baseX = screenW - 150f;
        float baseY = 30f;

        // Базовые координаты левой и правой рук (смещение относительно привязки)
        float leftBaseX = -70f;
        float leftBaseY = -10f;
        float leftBaseZ = 0f;

        float rightBaseX = 30f;
        float rightBaseY = -10f;
        float rightBaseZ = 0f;

        // 1. АНИМАЦИЯ ПОКОЯ (мягкое дыхание)
        float idleYOffset = FastMath.sin(animationTime * 1.8f) * 3f;
        float leftIdleXOffset = FastMath.cos(animationTime * 0.9f) * 1.5f;

        float leftX = leftBaseX + leftIdleXOffset;
        float leftY = leftBaseY + idleYOffset;
        float leftZ = leftBaseZ;

        float rightX = rightBaseX;
        float rightY = rightBaseY + idleYOffset;
        float rightZ = rightBaseZ;

        // 2. ДИНАМИЧЕСКИЙ СВЕЙ (качание при движении)
        boolean isMoving = (player.up || player.down || player.left || player.right) 
                && (player.velocity.x != 0 || player.velocity.z != 0);

        float leftBobX = 0f;
        float leftBobY = 0f;
        float rightBobX = 0f;
        float rightBobY = 0f;

        if (isMoving && player.onGround && !player.isFlying && !isInventoryOpen) {
            float speedMult = player.sprint ? 1.5f : 1.0f;
            float bobTimer = playerCam.getBobTimer();

            leftBobX = FastMath.sin(bobTimer * 0.5f) * 5f * speedMult;
            leftBobY = FastMath.cos(bobTimer) * 3f * speedMult;

            rightBobX = FastMath.sin(bobTimer * 0.5f + FastMath.PI) * 5f * speedMult;
            rightBobY = FastMath.cos(bobTimer + FastMath.PI) * 3f * speedMult;
        }

        Vector3f leftFinalPos = new Vector3f(baseX + leftX + leftBobX, baseY + leftY + leftBobY, leftZ);
        Vector3f rightFinalPos = new Vector3f(baseX + rightX + rightBobX, baseY + rightY + rightBobY, rightZ);

        // 3. АНИМАЦИЯ УДАРА КИРКОЙ + НЕПРЕРЫВНЫЙ SWING ПРИ КОПАНИИ
        float punchRotation = 0.0f;
        float punchZOffset = 0.0f;
        float punchYOffset = 0.0f;
        float miningSwing = 0.0f;

        if (isMining) {
            miningSwing = FastMath.sin(animationTime * 14.0f);
            punchRotation = -miningSwing * 0.9f;
            punchZOffset = -Math.abs(miningSwing) * 12f;
            punchYOffset = -Math.abs(miningSwing) * 6f;
        } else if (isPunching) {
            punchTime += tpf * 4.4f;
            if (punchTime >= 1.0f) {
                isPunching = false;
                punchTime = 0.0f;
            } else {
                float punchFactor = FastMath.sin(punchTime * FastMath.PI);
                punchRotation = -punchFactor * 1.1f;
                punchZOffset = -punchFactor * 18f;
                punchYOffset = punchFactor * -8f;
            }
        }

        rightFinalPos.x += punchZOffset;
        rightFinalPos.y += punchYOffset;

        // Применение позиций (относительно базовой точки в правом нижнем углу)
        leftArmNode.setLocalTranslation(leftFinalPos.x - baseX, leftFinalPos.y - baseY, leftZ);
        rightArmNode.setLocalTranslation(rightFinalPos.x - baseX, rightFinalPos.y - baseY, rightZ);

        // Мягкий дефолтный разворот рук
        Quaternion leftRot = new Quaternion().fromAngles(0.12f, 0.35f, -0.08f);
        leftArmNode.setLocalRotation(leftRot);

        Quaternion rightRot = new Quaternion().fromAngles(0.12f, -0.35f, 0.08f);
        if ((isMining || isPunching) && punchRotation != 0f) {
            Quaternion punchSwing = new Quaternion().fromAngles(punchRotation, 0, 0);
            rightRot.multLocal(punchSwing);
        }
        rightArmNode.setLocalRotation(rightRot);

        // Базовая позиция parentNode — правый нижний угол
        parentNode.setLocalTranslation(baseX, baseY, 0);
        parentNode.setLocalRotation(new Quaternion());
    }

    public void triggerPunch() {
        if (!isPunching) {
            isPunching = true;
            punchTime = 0.0f;
        }
        if (soundManager != null) soundManager.hit();
    }

    public void setMining(boolean mining) {
        this.isMining = mining;
    }

    private SoundManager soundManager;
    public void setSoundManager(SoundManager sm) { this.soundManager = sm; }

    public void setVisible(boolean visible) {
        if (parentNode != null) {
            parentNode.setCullHint(visible ? com.jme3.scene.Spatial.CullHint.Inherit : com.jme3.scene.Spatial.CullHint.Always);
        }
    }

    public void cleanup() {
        if (parentNode != null) {
            parentNode.removeFromParent();
            parentNode = null;
        }
    }
}