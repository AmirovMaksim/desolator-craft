package com.mygame;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

import com.jme3.asset.AssetManager;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.audio.AudioNode;
import com.jme3.audio.AudioSource;
import com.jme3.math.Vector3f;

/**
 * Менеджер звука для DESOLATOR CRAFT.
 *
 * ВНИМАНИЕ: в проекте НЕТ внешних аудио-файлов. Все звуки генерируются
 * процедурно (как и текстуры в ProceduralTextureGenerator) в виде WAV-байтов,
 * сохраняются во временную папку и регистрируются через FileLocator, чтобы
 * jMonkeyEngine мог загрузить их как обычные AudioNode.
 *
 * Каждый звук имеет небольшую случайную вариацию высоты/громкости, чтобы
 * избежать монотонности при многократном воспроизведении (как в настоящем MC).
 */
public class SoundManager {

    // Имя локатора/папки, в которой лежат сгенерированные WAV-файлы
    private static final String SOUND_DIR_NAME = "gensounds";
    private final File soundDir;

    private AssetManager assetManager;
    private final Map<String, String> registered = new HashMap<>();
    private boolean enabled = true;

    // Ограничитель частоты: чтобы не спамить один и тот же звук каждый кадр
    private final Map<String, Float> lastPlayTime = new HashMap<>();

    // ВАЖНО: assetManager в jME создаётся только внутри simpleInitApp(), поэтому
    // передаём его лениво через init(), а не через конструктор (иначе будет null).
    public SoundManager() {
        // Кладём звуки в папку проекта (соседствует с src/), чтобы они переживали
        // перезапуски и не засоряли системную temp.
        this.soundDir = new File(System.getProperty("user.dir"), SOUND_DIR_NAME);
    }

    /** Инициализация: получает AssetManager, регистрирует локатор, генерирует звуки. */
    public void init(AssetManager assetManager) {
        this.assetManager = assetManager;
        if (!soundDir.exists()) {
            soundDir.mkdirs();
        }
        try {
            this.assetManager.registerLocator(soundDir.getAbsolutePath(), FileLocator.class);
        } catch (Exception e) {
            System.err.println("[SoundManager] Не удалось зарегистрировать локатор: " + e.getMessage());
        }
        generateAll();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    // =========================================================================
    //  ГЕНЕРАЦИЯ WAV
    // =========================================================================

    /** Частота дискретизации всех звуков (Гц). */
    private static final int SAMPLE_RATE = 22050;

    private void generateAll() {
        // step — шаги по земле (два чуть разных тембра)
        register("step", synthStep(180f, 0.06f));
        // jump — лёгкий «тпруть»
        register("jump", synthTone(420f, 0.09f, 0.22f));
        // land — приземление (ниже и глуше)
        register("land", synthTone(120f, 0.16f, 0.32f));
        // break — ломание блока (короткий треск/щелчок)
        register("break", synthNoiseBurst(0.12f, 0.35f));
        // place — установка блока (более мягкий щелчок)
        register("place", synthTone(300f, 0.08f, 0.25f));
        // hit — взмах киркой / удар (свип)
        register("hit", synthSweep(500f, 180f, 0.10f, 0.22f));
        // hurt — получение урона (низкий резкий)
        register("hurt", synthTone(220f, 0.18f, 0.38f));
        // death — смерть (падающий тон)
        register("death", synthSweep(380f, 80f, 0.7f, 0.4f));
        // pickup — подбор предмета (приятный «динь»)
        register("pickup", synthTone(880f, 0.10f, 0.25f));
        // drop — выброс предмета
        register("drop", synthTone(260f, 0.10f, 0.22f));
        // eat — поедание (glomp)
        register("eat", synthTone(160f, 0.12f, 0.28f));
    }

    /** Сохраняет волну в WAV-файл и регистрирует имя для assetManager. */
    private void register(String name, float[] samples) {
        byte[] wav = encodeWav(samples);
        File f = new File(soundDir, name + ".wav");
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(wav);
            registered.put(name, name + ".wav");
        } catch (IOException e) {
            System.err.println("[SoundManager] Ошибка записи " + name + ": " + e.getMessage());
        }
    }

    /** Упаковывает моно PCM16 в WAV-контейнер. */
    private static byte[] encodeWav(float[] samples) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int dataSize = samples.length * 2; // 16 бит = 2 байта на сэмпл

        writeStr(baos, "RIFF");
        writeInt(baos, 36 + dataSize);
        writeStr(baos, "WAVE");

        // fmt chunk
        writeStr(baos, "fmt ");
        writeInt(baos, 16);            // размер chunk
        writeShort(baos, 1);           // PCM
        writeShort(baos, 1);           // каналов: 1 (моно)
        writeInt(baos, SAMPLE_RATE);
        writeInt(baos, SAMPLE_RATE * 2); // byte rate
        writeShort(baos, 2);           // block align
        writeShort(baos, 16);          // bits per sample

        // data chunk
        writeStr(baos, "data");
        writeInt(baos, dataSize);
        ByteBuffer bb = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
        for (float s : samples) {
            int v = (int) (Math.max(-1f, Math.min(1f, s)) * 32767f);
            bb.putShort(0, (short) v);
            baos.write(bb.array()[0]);
            baos.write(bb.array()[1]);
        }
        return baos.toByteArray();
    }

    private static void writeStr(ByteArrayOutputStream o, String s) {
        for (byte b : s.getBytes()) o.write(b);
    }

    private static void writeInt(ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF);
        o.write((v >> 8) & 0xFF);
        o.write((v >> 16) & 0xFF);
        o.write((v >> 24) & 0xFF);
    }

    private static void writeShort(ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF);
        o.write((v >> 8) & 0xFF);
    }

    // =========================================================================
    //  СИНТЕЗАТОРЫ
    // =========================================================================

    private static float[] synthTone(float freq, float dur, float vol) {
        int n = (int) (SAMPLE_RATE * dur);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            float t = (float) i / SAMPLE_RATE;
            float env = (float) Math.exp(-t * (3.0 / dur)); // быстрый спад
            out[i] = (float) Math.sin(2 * Math.PI * freq * t) * env * vol;
        }
        return out;
    }

    private static float[] synthSweep(float f0, float f1, float dur, float vol) {
        int n = (int) (SAMPLE_RATE * dur);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            float t = (float) i / SAMPLE_RATE;
            float k = t / dur;
            float freq = f0 + (f1 - f0) * k;
            float env = (float) Math.sin(Math.PI * k); // всплеск посередине
            out[i] = (float) Math.sin(2 * Math.PI * freq * t) * env * vol;
        }
        return out;
    }

    private static float[] synthStep(float baseFreq, float dur) {
        // Короткий «шлёпок»: сумма низкочастотного тона + лёгкого шума.
        int n = (int) (SAMPLE_RATE * dur);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            float t = (float) i / SAMPLE_RATE;
            float env = (float) Math.exp(-t * (8.0 / dur));
            float tone = (float) Math.sin(2 * Math.PI * baseFreq * t);
            float noise = (float) (Math.random() * 2 - 1) * 0.3f;
            out[i] = (tone * 0.7f + noise) * env * 0.3f;
        }
        return out;
    }

    private static float[] synthNoiseBurst(float dur, float vol) {
        int n = (int) (SAMPLE_RATE * dur);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            float t = (float) i / SAMPLE_RATE;
            float env = (float) Math.exp(-t * (6.0 / dur));
            // фильтрованный шум для «хруста»
            float noise = (float) (Math.random() * 2 - 1);
            out[i] = noise * env * vol;
        }
        return out;
    }

    // =========================================================================
    //  ВОСПРОИЗВЕДЕНИЕ
    // =========================================================================

    /**
     * Проигрывает непозиционированный (экранный) звук.
     * @param name имя звука (без расширения)
     * @param minInterval минимальный интервал между проигрываниями (сек), 0 = без лимита
     */
    public void play(String name, float volume, float minInterval) {
        if (!enabled) return;
        if (assetManager == null) return; // не инициализирован — тихо выходим
        String asset = registered.get(name);
        if (asset == null) return;

        if (minInterval > 0f) {
            Float last = lastPlayTime.get(name);
            float now = (float) (System.nanoTime() / 1e9);
            if (last != null && (now - last) < minInterval) return;
            lastPlayTime.put(name, now);
        }

        try {
            AudioNode node = new AudioNode(assetManager, asset, false);
            node.setVolume(volume);
            node.setPitch(0.92f + (float) Math.random() * 0.16f); // лёгкая вариация
            node.setPositional(false);
            node.setReverbEnabled(false);
            node.play();
        } catch (Exception e) {
            // На случай проблем с аудио-устройством — не падаем.
            System.err.println("[SoundManager] play(" + name + ") failed: " + e.getMessage());
        }
    }

    /** Проигрывает позиционированный (3D) звук в мировых координатах. */
    public void playAt(String name, Vector3f worldPos, float volume, float minInterval) {
        if (!enabled) return;
        if (assetManager == null) return; // не инициализирован — тихо выходим
        String asset = registered.get(name);
        if (asset == null) return;

        if (minInterval > 0f) {
            Float last = lastPlayTime.get(name);
            float now = (float) (System.nanoTime() / 1e9);
            if (last != null && (now - last) < minInterval) return;
            lastPlayTime.put(name, now);
        }

        try {
            AudioNode node = new AudioNode(assetManager, asset, true);
            node.setVolume(volume);
            node.setPitch(0.92f + (float) Math.random() * 0.16f);
            node.setPositional(true);
            node.setRefDistance(4f);
            node.setMaxDistance(48f);
            node.setLocalTranslation(worldPos);
            node.setReverbEnabled(false);
            node.play();
        } catch (Exception e) {
            System.err.println("[SoundManager] playAt(" + name + ") failed: " + e.getMessage());
        }
    }

    // Короткие хелперы для основных событий
    public void step()      { play("step", 0.45f, 0.18f); }
    public void jump()      { play("jump", 0.5f, 0f); }
    public void land()      { play("land", 0.6f, 0.12f); }
    public void breakBlock(){ play("break", 0.6f, 0f); }
    public void placeBlock(){ play("place", 0.5f, 0f); }
    public void hit()       { play("hit", 0.45f, 0.05f); }
    public void hurt()      { play("hurt", 0.7f, 0.25f); }
    public void death()     { play("death", 0.8f, 0f); }
    public void pickup()    { play("pickup", 0.5f, 0.05f); }
    public void drop()      { play("drop", 0.5f, 0f); }
    public void eat()       { play("eat", 0.5f, 0f); }
}
