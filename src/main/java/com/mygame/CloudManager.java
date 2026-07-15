package com.mygame;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;

/**
 * Облака: плоские белые "платформы" в небе, которые следуют за игроком
 * по XZ (кажутся бесконечными). НЕ участвуют в коллизиях — только визуал.
 */
public class CloudManager {
    private Node clouds;
    private static final float CLOUD_Y = 115f;
    private static final float SPAN = 480f;

    public void init(AssetManager assetManager, Node rootNode) {
        clouds = new Node("Clouds");
        clouds.setShadowMode(RenderQueue.ShadowMode.Off);

        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", new ColorRGBA(0.92f, 0.94f, 0.97f, 0.82f));
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);

        java.util.Random rnd = new java.util.Random(1337);
        int count = 46;
        for (int i = 0; i < count; i++) {
            float w = 18 + rnd.nextFloat() * 26;
            float d = 12 + rnd.nextFloat() * 20;
            float x = (rnd.nextFloat() - 0.5f) * SPAN;
            float z = (rnd.nextFloat() - 0.5f) * SPAN;
            Box box = new Box(w, 2.5f, d);
            Geometry g = new Geometry("Cloud" + i, box);
            g.setMaterial(mat);
            g.setLocalTranslation(x, CLOUD_Y + (rnd.nextFloat() - 0.5f) * 8f, z);
            clouds.attachChild(g);
        }
        rootNode.attachChild(clouds);
    }

    public void update(Vector3f playerPos) {
        if (clouds != null) {
            clouds.setLocalTranslation(playerPos.x, 0, playerPos.z);
        }
    }

    public void cleanup() {
        if (clouds != null) {
            clouds.removeFromParent();
            clouds = null;
        }
    }
}
