package com.mygame;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

public class Player {
    public final Vector3f pos = new Vector3f();
    public final Vector3f velocity = new Vector3f();

    public boolean left = false;
    public boolean right = false;
    public boolean up = false;
    public boolean down = false;
    public boolean jump = false;
    public boolean crouch = false;
    public boolean sprint = false;
    public boolean swimUp = false; // реальное состояние Space в воде (не auto-repeat)

    public boolean onGround = false;
    public int extraJumps = 3; // для мультипрыжка в креативе
    public float speedBoostTimer = 0f; // временное ускорение от еды
    public boolean isFlying = false;
    public long lastSpacePressTime = 0;

    public float playerSpeed = 6.0f;
    public float gravity = -20.0f;
    public float jumpForce = 7.5f;

    public float landingShakeTime = 0.0f;
    public float landingShakeIntensity = 0.0f;
    public float fallDistance = 0.0f; 

    public float health = 20.0f;
    public float maxHealth = 20.0f;
    public float hunger = 20.0f;
    public final float maxHunger = 20.0f;
    public boolean isDead = false;

    public float oxygen = 10.0f;
    public final float maxOxygen = 10.0f;
    public boolean isGodMode = false;
    public PlayerClass playerClass = PlayerClass.WARRIOR;
    public float mana = 0.0f;
    public float maxMana = 0.0f;

    /** Применяет стартовые бонусы класса (HP/MP). Вызывается при создании мира. */
    public void applyClass(PlayerClass c) {
        this.playerClass = c;
        this.maxHealth = c.baseHP;
        this.health = c.baseHP;
        this.maxMana = c.baseMana;
        this.mana = c.baseMana;
    }
    private float drownDamageTimer = 0.0f;

    // НАСЫЩЕНИЕ (saturation): еда даёт сначала его, потом голод.
    // Реген здоровья идёт только пока saturation > 0.
    public float saturation = 20.0f;
    public final float maxSaturation = 20.0f;
    // ОТРАВЛЕНИЕ (poison): периодический урон со временем.
    public float poison = 0.0f;
    private float poisonTickTimer = 0.0f;

    public float spawnX = 0.0f;
    public float spawnY = 80.0f;
    public float spawnZ = 0.0f;
    public boolean hasCustomSpawn = false;

    public static class InventorySlot {
        public byte blockType = 0;
        public int count = 0;
    }
    public final InventorySlot[] inventory = new InventorySlot[36];

    public boolean isInWater = false;
    public boolean isInLava = false;
    private float lavaDamageTimer = 0.0f;

    private SoundManager soundManager;
    public void setSoundManager(SoundManager sm) { this.soundManager = sm; }

    private float hungerTickTimer = 0.0f;
    private float regenTickTimer = 0.0f;

    public Player() {
        for (int i = 0; i < 36; i++) {
            inventory[i] = new InventorySlot();
        }
    }

    public boolean addItem(byte type, int amount) {
        if (type == 0) return false;
        
        for (int i = 0; i < 36; i++) {
            if (inventory[i].blockType == type && inventory[i].count < 64) {
                int add = Math.min(amount, 64 - inventory[i].count);
                inventory[i].count += add;
                amount -= add;
                if (amount <= 0) return true;
            }
        }
        
        for (int i = 0; i < 36; i++) {
            if (inventory[i].blockType == 0) {
                inventory[i].blockType = type;
                inventory[i].count = Math.min(amount, 64);
                amount -= inventory[i].count;
                if (amount <= 0) return true;
            }
        }
        return amount < 0; 
    }

    public void resetMovement() {
        left = right = up = down = jump = crouch = sprint = false;
        velocity.set(0, 0, 0);
    }

    public void damage(float amount) {
        if (isDead || isGodMode) return;
        health -= amount;
        if (soundManager != null) soundManager.hurt();
        if (health <= 0) {
            health = 0;
            isDead = true;
            resetMovement();
        }
    }

    public void heal(float amount) {
        if (isDead) return;
        health += amount;
        if (health > maxHealth) {
            health = maxHealth;
        }
    }

    public void updateSurvivalStats(float tpf) {
        if (isDead) return;
        if (isGodMode) {
            health = maxHealth;
            hunger = maxHunger;
            oxygen = maxOxygen;
            return;
        }

        if (isInWater) {
            oxygen -= tpf;
            if (oxygen < 0.0f) {
                oxygen = 0.0f;
                drownDamageTimer += tpf;
                if (drownDamageTimer >= 1.0f) {
                    damage(2.0f);
                    drownDamageTimer = 0.0f;
                }
            }
        } else {
            oxygen += tpf * 6.0f;
            if (oxygen > maxOxygen) {
                oxygen = maxOxygen;
            }
            drownDamageTimer = 0.0f;
        }

        if (isInLava) {
            lavaDamageTimer += tpf;
            if (lavaDamageTimer >= 0.5f) {
                damage(4.0f);
                lavaDamageTimer = 0.0f;
            }
        } else {
            lavaDamageTimer = 0.0f;
        }

        // ОТРАВЛЕНИЕ (poison): периодический урон, пока poison > 0
        if (poison > 0.0f) {
            poison -= tpf;
            poisonTickTimer += tpf;
            if (poisonTickTimer >= 1.0f) {
                damage(1.0f);
                poisonTickTimer = 0.0f;
            }
        } else {
            poisonTickTimer = 0.0f;
        }

        // ГОЛОД: сначала тратит saturation, потом hunger
        float hungerLossRate = 0.04f;
        if (sprint && (up || down || left || right)) {
            hungerLossRate = 0.22f;
        }
        if (saturation > 0.0f) {
            saturation -= hungerLossRate * tpf * 2.0f;
            if (saturation < 0.0f) saturation = 0.0f;
        } else {
            hunger -= hungerLossRate * tpf;
        }
        if (hunger < 0) hunger = 0;

        // РЕГЕН здоровья только при saturation > 0 (сыт)
        if (saturation > 0.0f && hunger >= 18.0f && health < maxHealth) {
            regenTickTimer += tpf;
            if (regenTickTimer >= 3.0f) {
                heal(1.0f);
                regenTickTimer = 0.0f;
            }
        } else {
            regenTickTimer = 0.0f;
        }

        // УРОН от голода
        if (hunger <= 0.0f) {
            hungerTickTimer += tpf;
            if (hungerTickTimer >= 2.0f) {
                damage(1.0f);
                hungerTickTimer = 0.0f;
            }
        } else {
            hungerTickTimer = 0.0f;
        }
    }

    public void runPhysicsStep(float dt, Camera cam, World world) {
        if (isDead) {
            velocity.set(0, 0, 0);
            return;
        }

        if (pos.y < -30.0f) {
            damage(20.0f);
            return;
        }

        // ИСПРАВЛЕНО: блоки занимают мировые координаты [i, i+1), поэтому индекс
        // блока для непрерывной позиции нужно получать через floor(), а не round() —
        // round() half of the time указывал на соседний блок и ломал определение воды/лавы.
        int px = (int) Math.floor(pos.x);
        int py = (int) Math.floor(pos.y + 0.9f);
        int pz = (int) Math.floor(pos.z);
        byte currentBlock = world.getBlockAt(px, py, pz);
        
        isInWater = (currentBlock == 27);
        isInLava = (currentBlock == 28);

        Vector3f camDir = cam.getDirection().clone();
        camDir.y = 0;
        camDir.normalizeLocal();

        Vector3f camLeft = cam.getLeft().clone();
        camLeft.y = 0;
        camLeft.normalizeLocal();

        Vector3f moveDir = new Vector3f();
        if (up) moveDir.addLocal(camDir);
        if (down) moveDir.subtractLocal(camDir);
        if (left) moveDir.addLocal(camLeft);
        if (right) moveDir.subtractLocal(camLeft); 
        moveDir.normalizeLocal();

        float currentSpeed = sprint ? playerSpeed * 1.55f : playerSpeed;
        if (speedBoostTimer > 0) currentSpeed *= 1.45f;

        // ЗАМЕДЛЕНИЕ ОТ ГОЛОДА: при hunger < 6 игрок заметно медленнее
        if (hunger < 6.0f) {
            currentSpeed *= 0.6f;
        }

        if (isFlying) {
            velocity.x = moveDir.x * currentSpeed * 2.0f; 
            velocity.z = moveDir.z * currentSpeed * 2.0f;
            velocity.y = 0;

            if (jump) {
                velocity.y = currentSpeed * 1.5f; 
            } else if (crouch) {
                velocity.y = -currentSpeed * 1.5f; 
            }
        } else if (isInWater || isInLava) {
            float fluidDensity = isInWater ? 0.35f : 0.15f;
            velocity.x = moveDir.x * currentSpeed * fluidDensity;
            velocity.z = moveDir.z * currentSpeed * fluidDensity;

            // ВОДА: плавучесть + непрерывное всплытие при удержании Space.
            // НЕ импульс (старый баг: auto-repeat клавиатуры сбрасывал jump между
            // повторами -> персонаж "прыгал" на месте и не мог выбраться).
            velocity.y += (gravity * 0.03f) * dt;          // почти нейтральная плавучесть
            velocity.y *= (isInLava ? 0.80f : 0.88f);

            if (swimUp) {
                // Постоянное всплытие: тянем вверх, пока держишь Space
                velocity.y += 9.0f * dt;
                if (velocity.y > 3.2f) velocity.y = 3.2f;
            } else if (crouch) {
                velocity.y = isInLava ? -2.0f : -2.8f;
            } else {
                // Парение: медленное погружение, ограниченное снизу
                if (velocity.y < -0.7f) velocity.y = -0.7f;
            }
            fallDistance = 0.0f;
        } else {
            velocity.x = moveDir.x * currentSpeed;
            velocity.z = moveDir.z * currentSpeed;
            velocity.y += gravity * dt;

            if (jump && onGround) {
                velocity.y = jumpForce;
                onGround = false;
            } else if (jump && world != null && world.isCreative() && extraJumps > 0) {
                // МУЛЬТИПРЫЖОК в креативе (до 3 прыжков в воздухе)
                velocity.y = jumpForce * 0.92f;
                extraJumps--;
            }
        }

        movePlayer(velocity.mult(dt), world);

        if (isFlying || onGround) {
            if (onGround) extraJumps = 3; // сброс мультипрыжка на земле
            if (fallDistance > 5.0f && !isInWater && !isInLava) {
                if (!isFlying && world != null && !world.isCreative()) {
                    float damageAmount = (fallDistance - 5.0f) * 1.8f;
                    damage(damageAmount);
                }
                landingShakeTime = 0.35f;
                landingShakeIntensity = Math.min(0.25f, fallDistance * 0.015f);
            }
            fallDistance = 0.0f;
        } else {
            if (velocity.y < 0.0f) {
                fallDistance += -velocity.y * dt;
            }
        }

        if (speedBoostTimer > 0) speedBoostTimer -= dt;
    }

    private void movePlayer(Vector3f offset, World world) {
        pos.x += offset.x;
        if (checkCollision(pos, world)) {
            pos.x -= offset.x;
        }

        pos.z += offset.z;
        if (checkCollision(pos, world)) {
            pos.z -= offset.z;
        }

        pos.y += offset.y;
        if (checkCollision(pos, world)) {
            pos.y -= offset.y;
            if (offset.y < 0) {
                onGround = true;
                
                // ИСПРАВЛЕНО: Проверка на БЛОК СЛИЗИ (Блок 32)
                int checkY = (int) Math.floor(pos.y - 0.1f);
                byte blockUnder = world.getBlockAtThreadSafe((int) Math.floor(pos.x), checkY, (int) Math.floor(pos.z));
                if (blockUnder == 32) {
                    velocity.y = 15.0f; // Подбрасывание
                    onGround = false;
                    fallDistance = 0.0f; // Нет урона от падения
                } else {
                    velocity.y = 0;
                }
            } else {
                velocity.y = 0;
            }
        } else {
            if (offset.y != 0) {
                Vector3f checkGroundPos = pos.subtract(0, 0.1f, 0);
                onGround = checkCollision(checkGroundPos, world);
            }
        }
    }

    public boolean checkCollision(Vector3f checkPos, World world) {
        float r = 0.3f;
        float h = 1.8f;

        float[] xs = {checkPos.x - r, checkPos.x + r};
        float[] ys = {checkPos.y, checkPos.y + h/2, checkPos.y + h};
        float[] zs = {checkPos.z - r, checkPos.z + r};

        for (float cx : xs) {
            for (float cy : ys) {
                for (float cz : zs) {
                    if (isBlockSolid(cx, cy, cz, world)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isBlockSolid(float x, float y, float z, World world) {
        // ИСПРАВЛЕНО: главная причина "прохождения сквозь блоки". Блоки в мире занимают
        // диапазон [i, i+1) (см. генерацию меша в Chunk.java), а не центрированы на целых
        // координатах. Math.round(x) даёт правильный индекс блока только когда дробная
        // часть x меньше 0.5 — во второй половине блока он ошибочно указывал на соседний
        // (часто пустой) блок, из-за чего checkCollision считал, что коллизии нет, хотя
        // игрок физически находился внутри твёрдого блока. Правильная конвертация — floor().
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        int type = world.getBlockAt(bx, by, bz);
        return type != 0 && type != 27 && type != 28 && type != 33;
    }
}