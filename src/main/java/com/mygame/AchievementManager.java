package com.mygame;

import java.util.HashSet;
import java.util.Set;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;

/**
 * Достижения: всплывающий toast при разблокировке. Каждое — один раз.
 */
public class AchievementManager {
    private Node achNode;
    private final Set<String> unlocked = new HashSet<>();
    private BitmapText toastText;
    private float toastTimer = 0f;
    private final float[] queue = new float[0];

    public void init(AssetManager assetManager, Node guiNode, BitmapFont font) {
        achNode = new Node("AchievementNode");
        Geometry bg = new Geometry("AchBg", new Quad(360, 44));
        Material m = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", new ColorRGBA(0.10f, 0.12f, 0.18f, 0.92f));
        m.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        bg.setMaterial(m);
        bg.setLocalTranslation(0, 0, 0);
        achNode.attachChild(bg);

        toastText = new BitmapText(font);
        toastText.setSize(16);
        toastText.setColor(new ColorRGBA(0.95f, 0.78f, 0.15f, 1.0f));
        toastText.setLocalTranslation(14, 14, 1);
        achNode.attachChild(toastText);

        achNode.setLocalTranslation(40, 120, 10);
        achNode.setCullHint(Node.CullHint.Always);
        guiNode.attachChild(achNode);
    }

    public void unlock(String id, String title) {
        if (unlocked.contains(id)) return;
        unlocked.add(id);
        toastText.setText("[ACHIEVEMENT] " + title);
        toastTimer = 3.0f;
        achNode.setCullHint(Node.CullHint.Inherit);
        // лёгкая вспышка
        achNode.setLocalScale(1.05f);
    }

    public void update(float tpf) {
        if (toastTimer > 0) {
            toastTimer -= tpf;
            float a = 1.0f;
            if (toastTimer > 2.6f) a = (3.0f - toastTimer) / 0.4f;
            else if (toastTimer < 0.6f) a = toastTimer / 0.6f;
            a = FastMath.clamp(a, 0, 1);
            toastText.setColor(new ColorRGBA(0.95f, 0.78f, 0.15f, a));
            achNode.setLocalScale(FastMath.interpolateLinear(tpf * 8f, achNode.getLocalScale().x, 1.0f));
            if (toastTimer <= 0) achNode.setCullHint(Node.CullHint.Always);
        }
    }

    public void cleanup() {
        if (achNode != null) achNode.removeFromParent();
    }
}
