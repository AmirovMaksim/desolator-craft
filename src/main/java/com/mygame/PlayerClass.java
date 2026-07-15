package com.mygame;

/**
 * Классы персонажа для RPG-ветки "Мир меча и магии".
 * Каждый класс даёт стартовые бонусы: здоровье, мана, стартовый инвентарь, модификаторы.
 */
public enum PlayerClass {
    WARRIOR(0, "WARRIOR", "Воин",   30.0f, 0.0f,  1.5f, 1.0f),
    MAGE(1,    "MAGE",    "Маг",    18.0f, 100.0f, 0.8f, 1.0f),
    ARCHER(2,  "ARCHER",  "Лучник", 22.0f, 40.0f, 1.1f, 1.3f);

    public final int id;
    public final String tag;
    public final String displayName;
    public final float baseHP;
    public final float baseMana;
    public final float meleeDamageMult; // множитель урона в ближнем бою
    public final float rangedMult;      // множитель урона в дальнем бою

    PlayerClass(int id, String tag, String displayName, float baseHP, float baseMana,
                float meleeDamageMult, float rangedMult) {
        this.id = id;
        this.tag = tag;
        this.displayName = displayName;
        this.baseHP = baseHP;
        this.baseMana = baseMana;
        this.meleeDamageMult = meleeDamageMult;
        this.rangedMult = rangedMult;
    }

    public static PlayerClass fromId(int id) {
        for (PlayerClass c : values()) if (c.id == id) return c;
        return WARRIOR;
    }
}
