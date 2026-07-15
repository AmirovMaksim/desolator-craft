package com.mygame;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;

/**
 * Улучшенная 3D-модель игрока: голова (с глазами), туловище, 2 руки, 2 ноги.
 * Состояния анимации: idle (дыхание), walk (качание), swim (гребок), jump (в воздухе).
 */
public class PlayerModel {
    private Node modelNode;
    private Node headNode, torsoNode, leftArm, rightArm, leftLeg, rightLeg;
    private float animPhase = 0.0f;     // фаза походки/плавания
    private float breath = 0.0f;        // фаза дыхания
    private float armSwing = 0.0f;      // текущий размах рук (сглаженный)
    private float legSwing = 0.0f;
    private float armRaise = 0.0f;      // подъём рук (прыжок/плавание)
    private float lean = 0.0f;          // наклон вперёд (плавание/бег)

    // базовые высоты (для обнуления трансформаций)
    private static final float TORSO_Y = 1.05f;
    private static final float HEAD_Y  = 1.40f;
    private static final float ARM_Y   = 1.22f;
    private static final float LEG_Y   = 0.55f;

    public void init(AssetManager assetManager, Node rootNode) {
        modelNode = new Node("PlayerModel");

        Material skinMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        skinMat.setColor("Color", new ColorRGBA(0.96f, 0.74f, 0.62f, 1.0f));
        Material shirtMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        shirtMat.setColor("Color", new ColorRGBA(0.32f, 0.55f, 0.85f, 1.0f));
        Material pantsMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        pantsMat.setColor("Color", new ColorRGBA(0.22f, 0.24f, 0.32f, 1.0f));
        Material eyeMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        eyeMat.setColor("Color", new ColorRGBA(0.12f, 0.12f, 0.16f, 1.0f));

        // --- Туловище (грудь + живот) ---
        torsoNode = new Node("Torso");
        Geometry chest = new Geometry("Chest", new Box(0.26f, 0.28f, 0.15f));
        chest.setMaterial(shirtMat);
        chest.setLocalTranslation(0, 0.12f, 0);
        torsoNode.attachChild(chest);
        Geometry belly = new Geometry("Belly", new Box(0.24f, 0.18f, 0.14f));
        belly.setMaterial(shirtMat);
        belly.setLocalTranslation(0, -0.16f, 0);
        torsoNode.attachChild(belly);
        torsoNode.setLocalTranslation(0, TORSO_Y, 0);
        modelNode.attachChild(torsoNode);

        // --- Голова + глаза ---
        headNode = new Node("Head");
        Geometry head = new Geometry("Head", new Box(0.24f, 0.24f, 0.24f));
        head.setMaterial(skinMat);
        head.setLocalTranslation(0, 0, 0);
        headNode.attachChild(head);
        // глаза (спереди, по -Z смотрит модель? нет — модель смотрит по +Z от себя,
        // но yaw поворачивает; глаза кладём на +Z чтобы смотрели вперёд)
        Geometry eyeL = new Geometry("EyeL", new Box(0.05f, 0.05f, 0.02f));
        eyeL.setMaterial(eyeMat);
        eyeL.setLocalTranslation(-0.06f, 0.03f, 0.12f);
        headNode.attachChild(eyeL);
        Geometry eyeR = new Geometry("EyeR", new Box(0.05f, 0.05f, 0.02f));
        eyeR.setMaterial(eyeMat);
        eyeR.setLocalTranslation(0.06f, 0.03f, 0.12f);
        headNode.attachChild(eyeR);
        headNode.setLocalTranslation(0, HEAD_Y, 0);
        modelNode.attachChild(headNode);

        // --- Руки (плечо + кисть) ---
        leftArm = makeArm(skinMat, shirtMat);
        leftArm.setLocalTranslation(-0.34f, ARM_Y, 0);
        modelNode.attachChild(leftArm);

        rightArm = makeArm(skinMat, shirtMat);
        rightArm.setLocalTranslation(0.34f, ARM_Y, 0);
        modelNode.attachChild(rightArm);

        // --- Ноги (бедро + стопа) ---
        leftLeg = makeLeg(pantsMat, skinMat);
        leftLeg.setLocalTranslation(-0.13f, LEG_Y, 0);
        modelNode.attachChild(leftLeg);

        rightLeg = makeLeg(pantsMat, skinMat);
        rightLeg.setLocalTranslation(0.13f, LEG_Y, 0);
        modelNode.attachChild(rightLeg);

        rootNode.attachChild(modelNode);
    }

    private Node makeArm(Material skin, Material shirt) {
        Node arm = new Node("Arm");
        Geometry upper = new Geometry("Upper", new Box(0.085f, 0.20f, 0.085f));
        upper.setMaterial(shirt);
        upper.setLocalTranslation(0, -0.10f, 0);
        arm.attachChild(upper);
        Geometry hand = new Geometry("Hand", new Box(0.07f, 0.12f, 0.07f));
        hand.setMaterial(skin);
        hand.setLocalTranslation(0, -0.30f, 0);
        arm.attachChild(hand);
        // pivot в плече (верх), поэтому смещаем геометрию вниз
        return arm;
    }

    private Node makeLeg(Material pants, Material skin) {
        Node leg = new Node("Leg");
        Geometry thigh = new Geometry("Thigh", new Box(0.10f, 0.22f, 0.10f));
        thigh.setMaterial(pants);
        thigh.setLocalTranslation(0, -0.11f, 0);
        leg.attachChild(thigh);
        Geometry foot = new Geometry("Foot", new Box(0.10f, 0.16f, 0.12f));
        foot.setMaterial(skin);
        foot.setLocalTranslation(0, -0.30f, 0.01f);
        leg.attachChild(foot);
        return leg;
    }

    public void update(float tpf, Player player, Main app) {
        if (modelNode == null) return;

        modelNode.setLocalTranslation(player.pos.x, player.pos.y, player.pos.z);

        // Поворот к взгляду (только yaw)
        Vector3f dir = app.getCamera().getDirection();
        float yaw = FastMath.atan2(dir.x, dir.z);
        modelNode.setLocalRotation(new Quaternion().fromAngles(0, yaw, 0));

        boolean moving = (player.up || player.down || player.left || player.right)
                && (player.velocity.x != 0 || player.velocity.z != 0);
        float speed = FastMath.abs(player.velocity.x) + FastMath.abs(player.velocity.z);

        // --- Целевые параметры анимации по состоянию ---
        float targetArm = 0, targetLeg = 0, targetRaise = 0, targetLean = 0;
        float animSpeed = 9.0f;

        if (player.isInWater) {
            // ПЛАВАНИЕ: руки гребут (большой размах), тело наклонено, ноги машут мягко
            targetArm = 0.9f;
            targetLeg = 0.35f;
            targetRaise = 0.3f;
            targetLean = 0.35f;
            animSpeed = 7.0f;
            animPhase += tpf * animSpeed;
        } else if (!player.onGround) {
            // В ВОЗДУХЕ (прыжок/падение): руки вверх, ноги чуть согнуты
            targetArm = 0.0f;
            targetLeg = 0.2f;
            targetRaise = 1.2f;
            targetLean = 0.0f;
        } else if (moving) {
            // ХОДЬБА/БЕГ: руки и ноги противофазой
            float amp = FastMath.clamp(speed / 6.0f, 0.4f, 1.0f);
            targetArm = 0.7f * amp;
            targetLeg = 0.7f * amp;
            targetRaise = 0.0f;
            targetLean = 0.08f * amp;
            animSpeed = 9.0f * (player.sprint ? 1.6f : 1.0f);
            animPhase += tpf * animSpeed;
        } else {
            // IDLE: лёгкое дыхание, руки вниз
            targetArm = 0.05f;
            targetLeg = 0.0f;
            targetRaise = 0.0f;
            targetLean = 0.0f;
            breath += tpf * 2.0f;
        }

        // Плавная интерполяция параметров
        float k = FastMath.clamp(tpf * 10.0f, 0, 1);
        armSwing = FastMath.interpolateLinear(k, armSwing, targetArm);
        legSwing = FastMath.interpolateLinear(k, legSwing, targetLeg);
        armRaise = FastMath.interpolateLinear(k, armRaise, targetRaise);
        lean     = FastMath.interpolateLinear(k, lean, targetLean);

        float swing = FastMath.sin(animPhase) * armSwing;
        float legSw = FastMath.sin(animPhase) * legSwing;
        float breathY = FastMath.sin(breath) * 0.02f;

        // Руки: качание + подъём (прыжок/плавание)
        leftArm.setLocalRotation(new Quaternion().fromAngles(-swing + armRaise, 0, 0.05f));
        rightArm.setLocalRotation(new Quaternion().fromAngles(swing + armRaise, 0, -0.05f));
        // Ноги
        leftLeg.setLocalRotation(new Quaternion().fromAngles(-legSw, 0, 0));
        rightLeg.setLocalRotation(new Quaternion().fromAngles(legSw, 0, 0));

        // Наклон всего тела вперёд + дыхание грудью
        torsoNode.setLocalRotation(new Quaternion().fromAngles(lean, 0, 0));
        torsoNode.setLocalTranslation(0, TORSO_Y + breathY, 0);
        headNode.setLocalTranslation(0, HEAD_Y + breathY, 0);
    }

    public void setVisible(boolean visible) {
        if (modelNode != null) {
            modelNode.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        }
    }

    public void cleanup() {
        if (modelNode != null) {
            modelNode.removeFromParent();
            modelNode = null;
        }
    }
}
