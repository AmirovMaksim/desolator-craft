package com.mygame;

import java.util.Random;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;

/**
 * Погода: дождь / снег / ясно. Частицы падают вокруг игрока,
 * небо затемняется, в HUD показывается индикатор.
 */
public class WeatherManager {
    public enum Weather { CLEAR, RAIN, SNOW }

    private Node weatherNode;
    private Node rainNode;
    private final Geometry[] drops = new Geometry[260];
    private final float[] vel = new float[drops.length];
    private final Random rand = new Random();
    private Weather current = Weather.CLEAR;
    private Weather target = Weather.CLEAR;
    private float transition = 1.0f; // 0 -> смена, 1 -> установилось
    private float changeTimer = 0f;
    private long seedSalt = 0;

    public void init(AssetManager assetManager, Node guiNode) {
        weatherNode = new Node("WeatherNode");
        rainNode = new Node("RainNode");
        weatherNode.attachChild(rainNode);

        Material rainMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        rainMat.setColor("Color", new ColorRGBA(0.6f, 0.7f, 0.9f, 0.55f));
        rainMat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);

        Material snowMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        snowMat.setColor("Color", new ColorRGBA(0.95f, 0.97f, 1.0f, 0.9f));

        for (int i = 0; i < drops.length; i++) {
            boolean snow = (i % 3 == 0);
            Box b = snow ? new Box(0.6f, 0.6f, 0.6f) : new Box(0.15f, 4.5f, 0.15f);
            Geometry g = new Geometry("Drop", b);
            g.setMaterial(snow ? snowMat : rainMat);
            g.setLocalTranslation(rand.nextFloat() * 400 - 200, rand.nextFloat() * 300, rand.nextFloat() * 400 - 200);
            g.setCullHint(Geometry.CullHint.Always);
            rainNode.attachChild(g);
            drops[i] = g;
            vel[i] = snow ? 40 + rand.nextFloat() * 20 : 220 + rand.nextFloat() * 80;
        }
        guiNode.attachChild(weatherNode);
    }

    public Weather getCurrent() { return current; }
    public String getName() { return current.name(); }

    public void setWeather(Weather w) {
        if (w == current) return;
        target = w;
        transition = 0f;
    }

    public void randomize(long worldSeed) {
        this.seedSalt = worldSeed;
        int r = new Random(worldSeed + System.currentTimeMillis()).nextInt(10);
        if (r < 6) setWeather(Weather.CLEAR);
        else if (r < 9) setWeather(Weather.RAIN);
        else setWeather(Weather.SNOW);
        changeTimer = 120 + rand.nextInt(180); // следующая смена через 2-5 мин
    }

    public void update(float tpf, Vector3f playerPos, float dayFactor) {
        // Авто-смена погоды по таймеру
        changeTimer -= tpf;
        if (changeTimer <= 0) randomize(seedSalt + (long) (playerPos.x + playerPos.z));

        if (transition < 1.0f) {
            transition += tpf * 0.5f;
            if (transition >= 1.0f) {
                transition = 1.0f;
                current = target;
            }
        }

        boolean snowy = (current == Weather.SNOW) || (transition > 0 && transition < 1 && target == Weather.SNOW);
        boolean show = (current != Weather.CLEAR) || (target != Weather.CLEAR && transition < 1);

        for (int i = 0; i < drops.length; i++) {
            Geometry g = drops[i];
            boolean isSnow = (i % 3 == 0);
            if (!show || (isSnow != snowy)) {
                g.setCullHint(Geometry.CullHint.Always);
                continue;
            }
            g.setCullHint(Geometry.CullHint.Inherit);
            Vector3f p = g.getLocalTranslation();
            // центрируем вокруг игрока
            float px = playerPos.x, py = playerPos.y + 30, pz = playerPos.z;
            if (p.x < px - 200) p.x = px + 200;
            if (p.x > px + 200) p.x = px - 200;
            if (p.z < pz - 200) p.z = pz + 200;
            if (p.z > pz + 200) p.z = pz - 200;
            if (p.y < py - 40) p.y = py + 120 + rand.nextFloat() * 80;
            if (isSnow) {
                p.x += FastMath.sin((p.y + i) * 0.05f) * 12 * tpf;
            }
            p.y -= vel[i] * tpf;
            g.setLocalTranslation(p);
        }
        // общая прозрачность перехода
        float alpha = (current == target) ? 1.0f : Math.abs(transition - 0.5f) * 2f;
        rainNode.setLocalScale(1, 1, 1);
        for (int i = 0; i < drops.length; i++) {
            // лёгкое мерцание при смене
            drops[i].setLocalScale(alpha, alpha, alpha);
        }
    }

    public void cleanup() {
        if (weatherNode != null) weatherNode.removeFromParent();
    }
}
