package com.mygame;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

public class PlayerCamera {
    private float currentFov = 70.0f;
    private float bobTimer = 0.0f;
    private float bobXOffset = 0.0f;
    private float bobYOffset = 0.0f;
    private float cameraTilt = 0.0f;
    // РЕЖИМЫ КАМЕРЫ: 0 = от 1-го лица, 1 = от 3-го лица (сзади)
    public int cameraMode = 0;
    // ПЛАВНЫЙ прокрут между режимами: 0 = FPS, 1 = TPS (интерполируется при F5)
    private float cameraRoll = 0.0f;
    private final Vector3f smoothCamPos = new Vector3f();
    private boolean smoothInit = false;

    public void cycleCameraMode() {
        cameraMode = (cameraMode + 1) % 2; // только 2 состояния: FPS <-> TPS
    }

    // --- Параметры режима галлюцинаций ---
    public boolean acidMode = false;
    private float acidTimer = 0.0f;

    public void update(float tpf, Camera cam, Player player, boolean isInventoryOpen) {
        float baseFov = GameSettings.fov;
        float targetFov = baseFov;
        boolean isMovingHorizontally = (player.up || player.down || player.left || player.right) 
                && (player.velocity.x != 0 || player.velocity.z != 0);

        if (player.isFlying) {
            targetFov = player.sprint ? baseFov + 18.0f : baseFov + 8.0f;
        } else if (player.sprint && isMovingHorizontally && player.onGround) {
            targetFov = baseFov + 12.0f;
        }

        // Логика кислотного режима (эффект волны)
        if (acidMode) {
            acidTimer += tpf * 3.5f;
            float wave = FastMath.sin(acidTimer) * 20.0f;
            targetFov += wave;
        }

        currentFov = FastMath.interpolateLinear(tpf * 8.0f, currentFov, targetFov);
        cam.setFrustumPerspective(currentFov, (float) cam.getWidth() / cam.getHeight(), 0.01f, 1000f);

        if (isMovingHorizontally && player.onGround && !player.isFlying && !isInventoryOpen) {
            float speedMultiplier = player.sprint ? 1.4f : 1.0f;
            bobTimer += tpf * 13.0f * speedMultiplier;
            
            float amplitudeY = player.sprint ? 0.09f : 0.05f;
            float amplitudeX = player.sprint ? 0.05f : 0.03f;
            
            bobYOffset = FastMath.sin(bobTimer) * amplitudeY;
            bobXOffset = FastMath.cos(bobTimer * 0.5f) * amplitudeX;
        } else {
            bobTimer = 0.0f;
            bobYOffset = FastMath.interpolateLinear(tpf * 10.0f, bobYOffset, 0.0f);
            bobXOffset = FastMath.interpolateLinear(tpf * 10.0f, bobXOffset, 0.0f);
        }

        float targetTilt = 0.0f;
        if (player.left && !player.right && !isInventoryOpen) {
            targetTilt = 0.015f; 
        } else if (player.right && !player.left && !isInventoryOpen) {
            targetTilt = -0.015f; 
        }

        if (acidMode) {
            targetTilt += FastMath.cos(acidTimer * 0.8f) * 0.08f;
        }

        cameraTilt = FastMath.interpolateLinear(tpf * 8.0f, cameraTilt, targetTilt);

        float shakeX = 0.0f;
        float shakeY = 0.0f;
        float shakeRoll = 0.0f;
        if (player.landingShakeTime > 0.0f) {
            player.landingShakeTime -= tpf;
            float factor = player.landingShakeTime / 0.35f; 
            float currentIntensity = player.landingShakeIntensity * factor;
            
            shakeX = (FastMath.nextRandomFloat() - 0.5f) * currentIntensity;
            shakeY = -Math.abs(FastMath.nextRandomFloat()) * currentIntensity * 1.5f; 
            shakeRoll = (FastMath.nextRandomFloat() - 0.5f) * currentIntensity * 0.4f;
        }

        Vector3f headPos = player.pos.add(0, 1.6f, 0);
        headPos.y += bobYOffset + shakeY;

        Vector3f camLeftVector = cam.getLeft().normalizeLocal();
        headPos.addLocal(camLeftVector.mult(bobXOffset + shakeX));

        // --- ПЛАВНЫЙ ПРОКРУТ между FPS (в голове) и TPS (позади) при F5 ---
        float targetRoll = (cameraMode == 1) ? 1.0f : 0.0f;
        cameraRoll = FastMath.interpolateLinear(tpf * 6.0f, cameraRoll, targetRoll);

        Vector3f dir = cam.getDirection().normalizeLocal();
        float tpsDist = 4.0f;
        // TPS-точка: позади игрока на tpsDist по направлению взгляда
        Vector3f tpsPos = headPos.add(dir.mult(-tpsDist));
        float minY = player.pos.y + 0.5f;
        if (tpsPos.y < minY) tpsPos.y = minY;

        // Интерполируем позицию камеры между FPS (headPos) и TPS (tpsPos)
        Vector3f targetPos = headPos.clone().interpolateLocal(tpsPos, cameraRoll);

        // Доп. плавное следование (чтобы TPS не дёргалась при резких движениях)
        if (cameraRoll > 0.01f) {
            if (!smoothInit) {
                smoothCamPos.set(targetPos);
                smoothInit = true;
            } else {
                float k = FastMath.clamp(tpf * 12.0f, 0, 1);
                smoothCamPos.interpolateLocal(targetPos, k);
            }
            cam.setLocation(smoothCamPos);
        } else {
            cam.setLocation(targetPos); // чистый FPS
        }

        float[] angles = new float[3];
        cam.getRotation().toAngles(angles); 
        
        float finalRoll = cameraTilt + shakeRoll;
        Quaternion cleanRotation = new Quaternion().fromAngles(angles[0], angles[1], finalRoll);
        cam.setRotation(cleanRotation);
    }

    public float getBobTimer() { return bobTimer; }
    public float getBobXOffset() { return bobXOffset; }
    public float getBobYOffset() { return bobYOffset; }
}