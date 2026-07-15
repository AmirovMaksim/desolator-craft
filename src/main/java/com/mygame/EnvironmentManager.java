package com.mygame;

import java.util.ArrayList;
import java.util.List;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.FogFilter;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.shadow.DirectionalLightShadowFilter;

public class EnvironmentManager {

    // ОПТИМИЗАЦИЯ: раньше эти цвета создавались заново (new ColorRGBA(...)) каждый
    // кадр внутри update(), хотя сами значения константны. Вынесены в статические
    // поля, чтобы не грузить сборщик мусора десятками аллокаций в секунду.
    private static final ColorRGBA DAY_SKY = new ColorRGBA(0.45f, 0.65f, 0.95f, 1.0f);
    private static final ColorRGBA SUNSET_SKY = new ColorRGBA(0.85f, 0.42f, 0.32f, 1.0f);
    private static final ColorRGBA NIGHT_SKY = new ColorRGBA(0.01f, 0.01f, 0.04f, 1.0f);
    private static final ColorRGBA RAIN_SKY = new ColorRGBA(0.20f, 0.22f, 0.25f, 1.0f);

    private static final ColorRGBA DAY_SUN = new ColorRGBA(1.0f, 0.96f, 0.88f, 1.0f);
    private static final ColorRGBA SUNSET_SUN = new ColorRGBA(0.92f, 0.32f, 0.12f, 1.0f);
    private static final ColorRGBA NIGHT_SUN = new ColorRGBA(0.06f, 0.08f, 0.15f, 1.0f);

    private static final ColorRGBA DAY_AMBIENT = new ColorRGBA(0.44f, 0.46f, 0.52f, 1.0f);
    private static final ColorRGBA NIGHT_AMBIENT = new ColorRGBA(0.06f, 0.08f, 0.12f, 1.0f);
    private static final ColorRGBA RAIN_AMBIENT = new ColorRGBA(0.18f, 0.18f, 0.20f, 1.0f);

    private static final ColorRGBA WATER_FOG_COLOR = new ColorRGBA(0.04f, 0.15f, 0.45f, 1.0f);
    private static final ColorRGBA LAVA_FOG_COLOR = new ColorRGBA(0.72f, 0.12f, 0.02f, 1.0f);

    // Переиспользуемые рабочие объекты вместо new ColorRGBA()/new Vector3f() каждый кадр
    private final ColorRGBA currentSky = new ColorRGBA();
    private final ColorRGBA currentSun = new ColorRGBA();
    private final ColorRGBA currentAmbient = new ColorRGBA();
    private final Vector3f finalLightDirection = new Vector3f();

    private Node cloudsNode;
    private final List<Geometry> cloudsList = new ArrayList<>();
    private Geometry sunBillboard;
    private Geometry moonBillboard;
    private Geometry starsGeometry;

    private float rainTransitionTimer = 0.0f;

    public void init(AssetManager assetManager, Node rootNode) {
        Box sunBox = new Box(6.0f, 6.0f, 6.0f);
        sunBillboard = new Geometry("SunBillboard", sunBox);
        Material sunMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        sunMat.setColor("Color", new ColorRGBA(1.0f, 0.92f, 0.50f, 1.0f)); 
        sunBillboard.setMaterial(sunMat);
        sunBillboard.setShadowMode(RenderQueue.ShadowMode.Off);
        rootNode.attachChild(sunBillboard);

        Box moonBox = new Box(5.0f, 5.0f, 5.0f);
        moonBillboard = new Geometry("MoonBillboard", moonBox);
        Material moonMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        moonMat.setColor("Color", new ColorRGBA(0.85f, 0.90f, 1.0f, 1.0f));
        moonBillboard.setMaterial(moonMat);
        moonBillboard.setShadowMode(RenderQueue.ShadowMode.Off);
        rootNode.attachChild(moonBillboard);

        createStars(assetManager, rootNode);
        createClouds(assetManager, rootNode);
    }

    /** Генерирует процедурное звёздное небо (точки на большой сфере вокруг игрока). */
    private void createStars(AssetManager assetManager, Node rootNode) {
        int starCount = 600;
        com.jme3.scene.Mesh starMesh = new com.jme3.scene.Mesh();
        float[] positions = new float[starCount * 3];
        float[] colors = new float[starCount * 4];
        java.util.Random r = new java.util.Random(1337);
        for (int i = 0; i < starCount; i++) {
            // Случайная точка на верхней полусфере радиуса 300
            float theta = (float) (r.nextFloat() * Math.PI * 2.0);
            float phi = (float) (r.nextFloat() * Math.PI * 0.5); // только над горизонтом
            float radius = 300f;
            positions[i * 3 + 0] = (float) (Math.cos(theta) * Math.sin(phi) * radius);
            positions[i * 3 + 1] = (float) (Math.cos(phi) * radius);
            positions[i * 3 + 2] = (float) (Math.sin(theta) * Math.sin(phi) * radius);
            float tw = 0.6f + r.nextFloat() * 0.4f;
            colors[i * 4 + 0] = tw;
            colors[i * 4 + 1] = tw;
            colors[i * 4 + 2] = 1.0f;
            colors[i * 4 + 3] = 1.0f;
        }
        starMesh.setBuffer(com.jme3.scene.VertexBuffer.Type.Position, 3, positions);
        starMesh.setBuffer(com.jme3.scene.VertexBuffer.Type.Color, 4, colors);
        starMesh.setMode(com.jme3.scene.Mesh.Mode.Points);
        starMesh.updateBound();
        starMesh.setStatic();

        starsGeometry = new Geometry("Stars", starMesh);
        Material starMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        starMat.setColor("Color", ColorRGBA.White);
        starMat.setBoolean("VertexColor", true); // читаем цвет из вершин меши
        starMat.getAdditionalRenderState().setFaceCullMode(com.jme3.material.RenderState.FaceCullMode.Off);
        starMat.getAdditionalRenderState().setDepthWrite(false);
        starsGeometry.setMaterial(starMat);
        starsGeometry.setShadowMode(RenderQueue.ShadowMode.Off);
        // Звёзды рисуем поверх неба, но за всем остальным
        starsGeometry.setQueueBucket(RenderQueue.Bucket.Sky);
        rootNode.attachChild(starsGeometry);
    }

    private float dayTimerCached = 0f;

    public float getDayFactor() {
        float sY = FastMath.sin(dayTimerCached);
        return FastMath.clamp((sY + 0.15f) / 1.15f, 0f, 1f);
    }

    public void update(float tpf, Main app, com.jme3.renderer.ViewPort viewPort, FilterPostProcessor fpp) {
        Player player = app.player;

        if (app.isRaining) {
            rainTransitionTimer = Math.min(1.0f, rainTransitionTimer + tpf * 0.2f);
        } else {
            rainTransitionTimer = Math.max(0.0f, rainTransitionTimer - tpf * 0.2f);
        }

        if (!app.isTimeLocked) {
            app.dayTimer += tpf * 0.015f;
        }
        dayTimerCached = app.dayTimer;

        float sunAngle = app.dayTimer;

        float sX = FastMath.cos(sunAngle) * 0.5f;
        float sY = FastMath.sin(sunAngle); 
        float sZ = FastMath.sin(sunAngle * 0.5f) * 0.3f;

        Vector3f playerPosOffset = player.pos;
        if (sunBillboard != null) {
            sunBillboard.setLocalTranslation(playerPosOffset.add(sX * 220f, sY * 220f, sZ * 220f));
        }
        if (moonBillboard != null) {
            moonBillboard.setLocalTranslation(playerPosOffset.add(-sX * 220f, -sY * 220f, -sZ * 220f));
        }

        finalLightDirection.set(0, 0, 0);
        if (sY > 0.0f) {
            finalLightDirection.set(-sX, -sY, -sZ).normalizeLocal();
        } else {
            finalLightDirection.set(sX, sY, sZ).normalizeLocal();
        }

        if (app.getSunLight() != null) {
            app.getSunLight().setDirection(finalLightDirection);
        }

        float dayWeight = FastMath.clamp((sY + 0.15f) / 1.15f, 0f, 1f); 
        float sunsetWeight = 1.0f - FastMath.abs(sY); 
        sunsetWeight = FastMath.clamp(sunsetWeight * sunsetWeight, 0f, 1f);

        ColorRGBA daySky = DAY_SKY;
        ColorRGBA sunsetSky = SUNSET_SKY;
        ColorRGBA nightSky = NIGHT_SKY;

        ColorRGBA daySun = DAY_SUN;
        ColorRGBA sunsetSun = SUNSET_SUN;
        ColorRGBA nightSun = NIGHT_SUN;

        ColorRGBA dayAmbient = DAY_AMBIENT;
        ColorRGBA nightAmbient = NIGHT_AMBIENT;

        currentSky.interpolateLocal(nightSky, daySky, dayWeight);
        currentSky.interpolateLocal(currentSky, sunsetSky, sunsetWeight * 0.55f);

        if (rainTransitionTimer > 0.0f) {
            currentSky.interpolateLocal(currentSky, RAIN_SKY, rainTransitionTimer * 0.85f);
        }

        currentSun.interpolateLocal(nightSun, daySun, dayWeight);
        currentSun.interpolateLocal(currentSun, sunsetSun, sunsetWeight * 0.80f);

        currentAmbient.interpolateLocal(nightAmbient, dayAmbient, dayWeight);
        if (rainTransitionTimer > 0.0f) {
            currentAmbient.interpolateLocal(currentAmbient, RAIN_AMBIENT, rainTransitionTimer * 0.6f);
        }

        viewPort.setBackgroundColor(currentSky);
        if (app.getSunLight() != null) app.getSunLight().setColor(currentSun);
        if (app.getAmbientLight() != null) app.getAmbientLight().setColor(currentAmbient);

        if (fpp != null) {
            FogFilter fog = fpp.getFilter(FogFilter.class);
            if (fog != null) {
                float targetFogDistance = 140.0f;
                float targetFogDensity = 0.40f;
                ColorRGBA targetFogColor = currentSky;

                if (player.isInWater) {
                    targetFogDistance = 6.0f; // ИСПРАВЛЕНО: Очень плотный туман
                    targetFogDensity = 1.8f;
                    targetFogColor = WATER_FOG_COLOR;
                } else if (player.isInLava) {
                    targetFogDistance = 4.0f;
                    targetFogDensity = 2.0f;
                    targetFogColor = LAVA_FOG_COLOR;
                } else if (rainTransitionTimer > 0.0f) {
                    targetFogDistance = FastMath.interpolateLinear(rainTransitionTimer, 140.0f, 40.0f);
                    targetFogDensity = FastMath.interpolateLinear(rainTransitionTimer, 0.40f, 0.80f);
                    targetFogColor = currentSky;
                }

                fog.setFogColor(targetFogColor);
                fog.setFogDistance(FastMath.interpolateLinear(tpf * 4.0f, fog.getFogDistance(), targetFogDistance));
                fog.setFogDensity(FastMath.interpolateLinear(tpf * 4.0f, fog.getFogDensity(), targetFogDensity));
            }

            DirectionalLightShadowFilter shadow = fpp.getFilter(DirectionalLightShadowFilter.class);
            if (shadow != null) {
                boolean shouldBeEnabled = GameSettings.shadowsEnabled && sY > 0.05f;
                if (shadow.isEnabled() != shouldBeEnabled) shadow.setEnabled(shouldBeEnabled); 
                if (shouldBeEnabled) shadow.setShadowIntensity(0.42f * (sY / 1.0f));
            }
        }

        updateClouds(tpf, player.pos);

        // --- ЗВЁЗДЫ: следуют за игроком, видны только ночью ---
        if (starsGeometry != null) {
            starsGeometry.setLocalTranslation(player.pos);
            float starAlpha = FastMath.clamp(1.0f - dayWeight * 1.4f, 0.0f, 1.0f);
            if (starAlpha <= 0.01f) {
                starsGeometry.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
            } else {
                starsGeometry.setCullHint(com.jme3.scene.Spatial.CullHint.Inherit);
                // модулируем яркость через цвет материала (без создания объектов)
                com.jme3.material.Material sm = starsGeometry.getMaterial();
                sm.setColor("Color", new ColorRGBA(1f, 1f, 1f, starAlpha));
            }
        }

        // --- СОЛНЦЕ/ЛУНА: мягкое мерцание (glow) и скрытие под горизонтом ---
        if (sunBillboard != null) {
            float sunGlow = 1.0f + FastMath.sin(app.dayTimer * 3.0f) * 0.06f;
            sunBillboard.setLocalScale(sunGlow);
            sunBillboard.setCullHint(sY > -0.05f ? com.jme3.scene.Spatial.CullHint.Inherit
                                               : com.jme3.scene.Spatial.CullHint.Always);
        }
        if (moonBillboard != null) {
            float moonGlow = 1.0f + FastMath.sin(app.dayTimer * 2.0f + 1.5f) * 0.05f;
            moonBillboard.setLocalScale(moonGlow);
            moonBillboard.setCullHint(sY < 0.05f ? com.jme3.scene.Spatial.CullHint.Inherit
                                                : com.jme3.scene.Spatial.CullHint.Always);
        }
    }

    private void createClouds(AssetManager assetManager, Node rootNode) {
        cloudsNode = new Node("CloudsNode");
        cloudsNode.setShadowMode(RenderQueue.ShadowMode.Cast);

        Material cloudMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        cloudMat.setBoolean("UseMaterialColors", true);
        cloudMat.setColor("Diffuse", new ColorRGBA(0.95f, 0.95f, 0.98f, 1.0f));
        cloudMat.setColor("Ambient", new ColorRGBA(0.70f, 0.70f, 0.75f, 1.0f));
        cloudMat.setColor("Specular", ColorRGBA.Black);
        cloudMat.setFloat("Shininess", 0.0f);

        for (int i = 0; i < 8; i++) {
            float w = 35f + (float) (Math.random() * 30);
            float h = 2.5f; 
            float d = 35f + (float) (Math.random() * 30);

            Box cloudBox = new Box(w, h, d);
            Geometry cloud = new Geometry("Cloud_" + i, cloudBox);
            cloud.setMaterial(cloudMat);

            float cx = (float) (Math.random() * 500 - 250);
            float cy = 110.0f + (float) (Math.random() * 8.0f); 
            float cz = (float) (Math.random() * 500 - 250);

            cloud.setLocalTranslation(cx, cy, cz);
            cloudsNode.attachChild(cloud);
            cloudsList.add(cloud);
        }
        rootNode.attachChild(cloudsNode);
    }

    private void updateClouds(float tpf, Vector3f playerPos) {
        if (cloudsNode != null) {
            boolean enabled = GameSettings.cloudsEnabled;
            cloudsNode.setCullHint(enabled ? com.jme3.scene.Spatial.CullHint.Inherit : com.jme3.scene.Spatial.CullHint.Always);
        }

        if (!GameSettings.cloudsEnabled) return;

        float cloudSpeed = 1.0f; 
        for (Geometry cloud : cloudsList) {
            Vector3f pos = cloud.getLocalTranslation();
            pos.x += tpf * cloudSpeed;

            if (pos.x - playerPos.x > 260f) {
                pos.x = playerPos.x - 260f;
                pos.z = playerPos.z + (float) (Math.random() * 500f - 250f);
            }
            cloud.setLocalTranslation(pos);
        }
    }

    public void clear() {
        cloudsList.clear();
    }
}