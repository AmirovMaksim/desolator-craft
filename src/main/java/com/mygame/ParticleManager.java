package com.mygame;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.jme3.material.Material;
import com.jme3.math.Vector3f; // Добавленный импорт
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;

public class ParticleManager {

    private static class MiningParticle {
        Geometry geom;
        Vector3f pos = new Vector3f();
        Vector3f vel = new Vector3f();
        float life = 0.5f;
        float rotationSpeed;
    }

    private final List<MiningParticle> activeParticles = new ArrayList<>();
    private Node particlesNode;
    private Material fireflyMat;
    private final List<Geometry> fireflies = new ArrayList<>();
    private float fireflyTimer = 0f;

    public void init(Node rootNode, com.jme3.asset.AssetManager assetManager) {
        particlesNode = new Node("ParticlesNode");
        rootNode.attachChild(particlesNode);
        fireflyMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        fireflyMat.setColor("Color", new com.jme3.math.ColorRGBA(0.8f, 1.0f, 0.3f, 0.9f));
        fireflyMat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
    }

    public void spawnBreakParticles(float bx, float by, float bz, byte blockType, Material[] blockMaterials) {
        if (!GameSettings.particlesEnabled) return;
        if (blockType == 0) return;
        
        Box partBox = new Box(0.04f, 0.04f, 0.04f);
        java.util.Random r = new java.util.Random();

        for (int i = 0; i < 8; i++) {
            MiningParticle p = new MiningParticle();
            p.geom = new Geometry("Part", partBox);
            p.geom.setMaterial(blockMaterials[blockType]);
            p.geom.setQueueBucket(RenderQueue.Bucket.Opaque);
            
            p.pos.set(bx + (r.nextFloat() - 0.5f) * 0.6f, 
                      by + (r.nextFloat() - 0.5f) * 0.6f, 
                      bz + (r.nextFloat() - 0.5f) * 0.6f);
            
            p.vel.set((r.nextFloat() - 0.5f) * 3.5f, 
                      r.nextFloat() * 4.0f + 1.5f, 
                      (r.nextFloat() - 0.5f) * 3.5f);

            p.rotationSpeed = r.nextFloat() * 12.0f - 6.0f;
            p.geom.setLocalTranslation(p.pos);
            particlesNode.attachChild(p.geom);
            activeParticles.add(p);
        }
    }

    public void update(float tpf) {
        Iterator<MiningParticle> iterator = activeParticles.iterator();
        while (iterator.hasNext()) {
            MiningParticle p = iterator.next();
            p.life -= tpf;
            if (p.life <= 0.0f) {
                p.geom.removeFromParent();
                iterator.remove();
            } else {
                p.vel.y -= 14.0f * tpf; 
                p.pos.addLocal(p.vel.mult(tpf));
                p.geom.setLocalTranslation(p.pos);
                p.geom.rotate(p.rotationSpeed * tpf, p.rotationSpeed * 0.5f * tpf, 0);

                float scale = p.life / 0.5f;
                p.geom.setLocalScale(scale);
            }
        }
    }

    public void updateFireflies(float tpf, Vector3f playerPos, float dayFactor) {
        // Светлячки только ночью (dayFactor < 0.35) и не под землёй
        boolean night = dayFactor < 0.35f && playerPos.y > 30;
        fireflyTimer += tpf;
        if (night && fireflies.size() < 60 && fireflyTimer > 0.15f) {
            fireflyTimer = 0f;
            Geometry g = new Geometry("Firefly", new Box(0.12f, 0.12f, 0.12f));
            g.setMaterial(fireflyMat);
            g.setLocalTranslation(
                playerPos.x + ((float) Math.random() - 0.5f) * 40f,
                playerPos.y + 1 + (float) Math.random() * 8f,
                playerPos.z + ((float) Math.random() - 0.5f) * 40f);
            g.setCullHint(Geometry.CullHint.Inherit);
            particlesNode.attachChild(g);
            fireflies.add(g);
        }
        // мерцание + медленный дрейф, удаление днём
        for (int i = fireflies.size() - 1; i >= 0; i--) {
            Geometry g = fireflies.get(i);
            float phase = (float) (System.nanoTime() * 0.0000007 + i);
            float a = 0.4f + (float) (Math.sin(phase) * 0.5f + 0.5f) * 0.6f;
            g.getMaterial().setColor("Color", new com.jme3.math.ColorRGBA(0.8f, 1.0f, 0.3f, a));
            Vector3f p = g.getLocalTranslation();
            p.x += Math.sin(phase * 0.7f) * tpf * 0.6f;
            p.y += Math.cos(phase * 0.5f) * tpf * 0.4f;
            g.setLocalTranslation(p);
            if (!night) {
                g.removeFromParent();
                fireflies.remove(i);
            }
        }
    }

    public void clear() {
        if (particlesNode != null) {
            particlesNode.detachAllChildren();
        }
        activeParticles.clear();
    }
}