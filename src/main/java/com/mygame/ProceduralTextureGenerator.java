package com.mygame;

import java.nio.ByteBuffer;

import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import com.jme3.util.BufferUtils;
import com.jme3.util.MipMapGenerator;

public class ProceduralTextureGenerator {

    public static Texture2D createProceduralTexture(ColorRGBA baseColor, boolean isGrass) {
        int size = 32;
        ByteBuffer buffer = BufferUtils.createByteBuffer(size * size * 4);
        java.util.Random rand = new java.util.Random();

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float noise = 0.92f + rand.nextFloat() * 0.08f;

                ColorRGBA pixelColor = baseColor.clone();
                if (isGrass) {
                    if (rand.nextFloat() > 0.85f) {
                        pixelColor.r += 0.03f;
                        pixelColor.g += 0.04f;
                        pixelColor.b -= 0.06f;
                    }
                } else {
                    if (rand.nextFloat() > 0.85f) {
                        pixelColor.r -= 0.06f;
                        pixelColor.g -= 0.06f;
                        pixelColor.b -= 0.06f;
                    }
                }

                byte r = (byte) Math.min(255, Math.max(0, (int) (pixelColor.r * 255 * noise)));
                byte g = (byte) Math.min(255, Math.max(0, (int) (pixelColor.g * 255 * noise)));
                byte b = (byte) Math.min(255, Math.max(0, (int) (pixelColor.b * 255 * noise)));
                byte a = (byte) 255;

                buffer.put(r).put(g).put(b).put(a);
            }
        }
        buffer.flip();

        Image image = new Image(Image.Format.RGBA8, size, size, buffer);
        
        try {
            MipMapGenerator.generateMipMaps(image);
        } catch (Exception e) {
            System.err.println("Failed to generate mipmaps: " + e.getMessage());
        }

        Texture2D texture = new Texture2D(image);
        texture.setMagFilter(com.jme3.texture.Texture.MagFilter.Nearest);
        texture.setMinFilter(com.jme3.texture.Texture.MinFilter.NearestLinearMipMap);
        texture.setWrap(com.jme3.texture.Texture.WrapAxis.S, com.jme3.texture.Texture.WrapMode.Repeat);
        texture.setWrap(com.jme3.texture.Texture.WrapAxis.T, com.jme3.texture.Texture.WrapMode.Repeat);
        
        return texture;
    }

    public static Texture2D createProceduralBlockTexture(ColorRGBA baseColor, int blockType) {
        int size = 32; 
        ByteBuffer buffer = BufferUtils.createByteBuffer(size * size * 4);
        java.util.Random rand = new java.util.Random();

        boolean isNaturalTerrain = (blockType == 1  || blockType == 2  || blockType == 3  || 
                                    blockType == 8  || blockType == 10 || blockType == 13 || 
                                    blockType == 17 || blockType == 22 || blockType == 23 || 
                                    blockType == 25);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                // Более заметный шум -> узнаваемее
                float noise = 0.90f + rand.nextFloat() * 0.12f;
                ColorRGBA pixelColor = baseColor.clone();

                // Для иконок инвентаря не гасим края (были тёмными) — держим яркими
                float borderFactor = 1.0f;

                if (blockType == 17 || blockType == 25) { 
                    pixelColor.set(baseColor);
                    if (blockType == 25) { 
                        pixelColor.set(0.48f, 0.18f, 0.55f, 1.0f);
                    }
                    
                    if ((x + y) % 4 == 0) {
                        pixelColor.r += 0.03f;
                        pixelColor.g += 0.04f; 
                        pixelColor.b -= 0.03f;
                    } else if ((x - y) % 5 == 0) {
                        pixelColor.r -= 0.02f;
                        pixelColor.g -= 0.03f;
                        pixelColor.b -= 0.01f;
                    }
                    
                } else if (blockType == 1 || blockType == 23) { 
                    int wave = 12 + (int)(FastMath.sin(x * 0.5f) * 3f) + (int)(FastMath.cos(x * 1.2f) * 2f);
                    if (x % 5 == 0) wave -= 2;
                    if (x % 7 == 0) wave += 3;

                    if (y >= size - wave) {
                        pixelColor.set(baseColor);
                        if (blockType == 23) { 
                            pixelColor.set(0.48f, 0.18f, 0.55f, 1.0f);
                        }
                    } else {
                        pixelColor.set(0.35f, 0.22f, 0.12f, 1.0f);
                        if ((x + y) % 6 == 0) {
                            pixelColor.r *= 0.88f;
                            pixelColor.g *= 0.88f;
                        }
                    }

                } else if (blockType == 2) { 
                    pixelColor.set(0.35f, 0.22f, 0.12f, 1.0f);
                    if ((x * y) % 11 == 0) {
                        pixelColor.set(0.28f, 0.18f, 0.10f, 1.0f);
                    } else if ((x + y) % 8 == 0) {
                        pixelColor.set(0.42f, 0.28f, 0.16f, 1.0f);
                    }

                } else if (blockType == 3 || blockType == 13) { 
                    pixelColor.set(0.45f, 0.45f, 0.48f, 1.0f);
                    if (blockType == 13) {
                        pixelColor.set(0.18f, 0.18f, 0.22f, 1.0f);
                    }
                    
                    boolean stoneEdge = (x == 2 || y == 2 || x == size - 3 || y == size - 3);
                    boolean stoneCrack = (y == 16 && x > 4 && x < 28) || (x == 16 && y > 16) || (x == 10 && y < 16);
                    
                    if (stoneCrack) {
                        pixelColor.set(0.22f, 0.22f, 0.24f, 1.0f);
                    } else if (stoneEdge) {
                        pixelColor.set(0.51f, 0.51f, 0.54f, 1.0f);
                    }

                } else if (blockType == 4 || blockType == 5) { 
                    pixelColor.set(0.45f, 0.45f, 0.48f, 1.0f);
                    
                    float distToVein1 = Math.abs(y - (10 + (int)(FastMath.sin(x * 0.4f) * 4f)));
                    float distToVein2 = Math.abs(y - (22 + (int)(FastMath.cos(x * 0.5f) * 5f)));
                    
                    if (distToVein1 < 3.0f || distToVein2 < 2.5f) {
                        if (blockType == 4) { 
                            pixelColor.set(0.12f, 0.12f, 0.14f, 1.0f);
                            if (rand.nextFloat() > 0.7f) pixelColor.set(0.25f, 0.25f, 0.28f, 1.0f);
                        } else { 
                            pixelColor.set(0.85f, 0.52f, 0.28f, 1.0f);
                            if (rand.nextFloat() > 0.6f) pixelColor.set(0.95f, 0.68f, 0.42f, 1.0f);
                        }
                    } else {
                        if ((x * y) % 13 == 0) borderFactor *= 0.9f;
                    }

                } else if (blockType == 6 || blockType == 12 || blockType == 15 || blockType == 20) { 
                    if (blockType == 6) pixelColor.set(0.38f, 0.24f, 0.12f, 1.0f); 
                    else if (blockType == 12) pixelColor.set(0.42f, 0.18f, 0.12f, 1.0f); 
                    else if (blockType == 15) pixelColor.set(0.85f, 0.70f, 0.55f, 1.0f); 
                    else pixelColor.set(0.92f, 0.92f, 0.95f, 1.0f); 

                    int barkLine = x % 8;
                    if (barkLine == 0 || barkLine == 1) {
                        pixelColor.r *= 0.75f;
                        pixelColor.g *= 0.75f;
                        pixelColor.b *= 0.75f;
                    }
                    if (blockType == 20 && (y % 10 == 0) && x % 4 != 0) { 
                        pixelColor.set(0.15f, 0.15f, 0.18f, 1.0f);
                    }

                } else if (blockType == 18) { 
                    float cx = x - 15.5f;
                    float cy = y - 15.5f;
                    float dist = FastMath.sqrt(cx * cx + cy * cy);
                    if ((int)dist % 5 == 0 || (int)dist % 5 == 1) {
                        pixelColor.set(0.32f, 0.18f, 0.08f, 1.0f);
                    } else {
                        pixelColor.set(0.68f, 0.50f, 0.32f, 1.0f);
                    }

                } else if (blockType == 26 || blockType == 29 || blockType == 30) {
                    // ИНСТРУМЕНТЫ: деревянная рукоять (по диагонали) + металлическая головка (сверху)
                    pixelColor.set(0.20f, 0.14f, 0.10f, 1.0f); // фон/рукоять
                    boolean handle = (x >= y - 2 && x <= y + 2);
                    if (handle) {
                        pixelColor.set(0.45f, 0.30f, 0.16f, 1.0f); // рукоять
                        if ((x + y) % 5 == 0) pixelColor.r *= 0.8f;
                    }
                    boolean head = false;
                    if (blockType == 26) { // ТОПОР: головка слева-сверху
                        head = (y < 14 && x < 16 && (x + y) < 22);
                    } else if (blockType == 29) { // КИРКА: головка сверху (горизонтальная)
                        head = (y < 12 && x > 4 && x < 28);
                    } else { // ЛОПАТА: головка сверху (треугольник)
                        head = (y < 13 && Math.abs(x - 16) < (13 - y) * 0.7f);
                    }
                    if (head) {
                        pixelColor.set(0.70f, 0.72f, 0.78f, 1.0f); // сталь
                        if ((x + y) % 4 == 0) pixelColor.b += 0.1f;
                    }

                } else if (blockType == 7 || blockType == 11 || blockType == 16 || blockType == 21) { 
                    if ((x + y) % 6 == 0 || (x - y) % 7 == 0) {
                        pixelColor.r *= 0.78f;
                        pixelColor.g *= 0.78f;
                        pixelColor.b *= 0.78f;
                    } else if (rand.nextFloat() > 0.82f) {
                        pixelColor.r *= 1.15f;
                        pixelColor.g *= 1.15f;
                        pixelColor.b *= 1.15f;
                    }

                } else if (blockType == 8) { 
                    pixelColor.set(0.88f, 0.82f, 0.55f, 1.0f);
                    int waveLine = (y + (int)(FastMath.sin(x * 0.3f) * 2.5f)) % 8;
                    if (waveLine == 0) {
                        pixelColor.r -= 0.08f;
                        pixelColor.g -= 0.08f;
                    } else if (waveLine == 4) {
                        pixelColor.r += 0.04f;
                        pixelColor.g += 0.04f;
                    }

                } else if (blockType == 9) { 
                    pixelColor.set(0.14f, 0.38f, 0.14f, 1.0f);
                    if (x % 8 == 0 || x % 8 == 1) {
                        pixelColor.set(0.08f, 0.22f, 0.08f, 1.0f); 
                    } else if ((x % 8 == 4) && (y % 6 == 0)) {
                        pixelColor.set(0.98f, 0.98f, 0.95f, 1.0f); 
                    }

                } else if (blockType == 14) { 
                    pixelColor.set(0.12f, 0.06f, 0.06f, 1.0f);
                    float distToCrack = Math.abs(y - (16 + (int)(FastMath.sin(x * 0.4f) * 6f)));
                    if (distToCrack < 2.5f) {
                        pixelColor.set(0.98f, 0.32f, 0.02f, 1.0f);
                        if (rand.nextFloat() > 0.6f) {
                            pixelColor.set(0.98f, 0.72f, 0.05f, 1.0f); 
                        }
                    }

                } else if (blockType == 19) { 
                    pixelColor.set(0.58f, 0.18f, 0.88f, 1.0f);
                    boolean crystalEdge = (x == y) || (x == size - y) || (x == 16) || (y == 16);
                    if (crystalEdge) {
                        pixelColor.set(0.85f, 0.55f, 0.98f, 1.0f); 
                    } else if ((x + y) % 3 == 0) {
                        pixelColor.r *= 0.85f;
                        pixelColor.b *= 0.85f;
                    }

                } else if (blockType == 22) { 
                    pixelColor.set(0.48f, 0.82f, 0.95f, 1.0f);
                    if ((x * y) % 17 == 0 && rand.nextFloat() > 0.5f) {
                        pixelColor.set(0.82f, 0.95f, 0.98f, 1.0f);
                    }

                } else if (blockType == 24) { 
                    pixelColor.set(0.82f, 0.12f, 0.12f, 1.0f);
                    float distToSpot1 = FastMath.sqrt((x - 8)*(x - 8) + (y - 8)*(y - 8));
                    float distToSpot2 = FastMath.sqrt((x - 24)*(x - 24) + (y - 24)*(y - 24));
                    float distToSpot3 = FastMath.sqrt((x - 16)*(x - 16) + (y - 20)*(y - 20));
                    
                    if (distToSpot1 < 3.2f || distToSpot2 < 3.5f || distToSpot3 < 2.8f) {
                        pixelColor.set(0.98f, 0.98f, 0.95f, 1.0f);
                    }
                } else if (blockType == 27) { 
                    pixelColor.set(0.12f, 0.45f, 0.88f, 0.95f); // ИСПРАВЛЕНО: Плотная, непрозрачная вода сверху
                    float waves = FastMath.sin(x * 0.3f + y * 0.1f) * FastMath.cos(y * 0.3f);
                    if (waves > 0.3f) {
                        pixelColor.r += 0.05f;
                        pixelColor.g += 0.10f;
                        pixelColor.b += 0.12f;
                    }
                    borderFactor = 1.0f; 
                } else if (blockType == 28) { 
                    pixelColor.set(0.92f, 0.18f, 0.02f, 1.0f);
                    float flow = FastMath.sin(x * 0.25f - y * 0.15f);
                    if (flow > 0.4f) {
                        pixelColor.set(0.98f, 0.55f, 0.02f, 1.0f); 
                    } else if (flow < -0.4f) {
                        pixelColor.set(0.55f, 0.05f, 0.02f, 1.0f); 
                    }
                    borderFactor = 1.0f;
                } else if (blockType == 31) { // TNT ВЗРЫВЧАТКА
                    pixelColor.set(0.85f, 0.15f, 0.15f, 1.0f);
                    if (y > 10 && y < 22) {
                        pixelColor.set(0.95f, 0.95f, 0.95f, 1.0f);
                        if (x > 8 && x < 24 && (x + y) % 4 == 0) {
                            pixelColor.set(0.1f, 0.1f, 0.1f, 1.0f); 
                        }
                    }
                } else if (blockType == 17 || blockType == 32) { // ДУБОВАЯ КОРА / БАТУТ СЛИЗИ
                    if (blockType == 17) {
                        pixelColor.set(0.35f, 0.22f, 0.12f, 1.0f);
                        int barkLine = x % 8;
                        if (barkLine == 0 || barkLine == 1) {
                            pixelColor.r*=0.78f; pixelColor.g*=0.78f; pixelColor.b*=0.78f;
                        }
                    } else {
                        pixelColor.set(0.35f, 0.85f, 0.35f, 0.75f);
                        if (x > 6 && x < 26 && y > 6 && y < 26) {
                            if (x == 7 || x == 25 || y == 7 || y == 25) {
                                pixelColor.set(0.25f, 0.75f, 0.25f, 0.85f);
                            }
                        }
                    }
                }

                byte r = (byte) Math.min(255, Math.max(0, (int) (pixelColor.r * 255 * noise * borderFactor)));
                byte g = (byte) Math.min(255, Math.max(0, (int) (pixelColor.g * 255 * noise * borderFactor)));
                byte b = (byte) Math.min(255, Math.max(0, (int) (pixelColor.b * 255 * noise * borderFactor)));
                byte a = (byte) (pixelColor.a * 255);

                buffer.put(r).put(g).put(b).put(a);
            }
        }
        buffer.flip();

        Image image = new Image(Image.Format.RGBA8, size, size, buffer);
        
        try {
            MipMapGenerator.generateMipMaps(image);
        } catch (Exception e) {
            // fallback
        }

        Texture2D texture = new Texture2D(image);
        texture.setMagFilter(com.jme3.texture.Texture.MagFilter.Nearest);
        texture.setMinFilter(com.jme3.texture.Texture.MinFilter.NearestLinearMipMap);
        
        texture.setWrap(com.jme3.texture.Texture.WrapAxis.S, com.jme3.texture.Texture.WrapMode.Repeat);
        texture.setWrap(com.jme3.texture.Texture.WrapAxis.T, com.jme3.texture.Texture.WrapMode.Repeat);
        
        return texture;
    }

    public static Texture2D createProceduralMobTexture(ColorRGBA baseColor, int mobType, boolean isAccent) {
        int size = 32;
        ByteBuffer buffer = BufferUtils.createByteBuffer(size * size * 4);
        java.util.Random rand = new java.util.Random();

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float noise = 0.90f + rand.nextFloat() * 0.10f;
                ColorRGBA pixelColor = baseColor.clone();

                if (mobType == 0) { 
                    if (isAccent) { 
                        if (y == 16 && (x == 8 || x == 22)) { 
                            pixelColor.set(0.1f, 0.1f, 0.1f, 1.0f);
                        } else if (y == 8 && (x >= 12 && x <= 18)) { 
                            pixelColor.set(0.95f, 0.65f, 0.65f, 1.0f);
                        }
                    } else { 
                        if ((x + y) % 4 == 0) pixelColor.r += 0.05f;
                    }
                } else if (mobType == 1) { 
                    if (isAccent) { 
                        pixelColor.set(0.05f, 0.05f, 0.05f, 1.0f);
                    } else {
                        if (x < 3 || x > size - 4 || y < 3 || y > size - 4) {
                            pixelColor.r *= 0.80f;
                        }
                    }
                } else if (mobType == 2) { 
                    if (isAccent) { 
                        if (y == 18 && (x == 10 || x == 20)) {
                            pixelColor.set(0.05f, 0.05f, 0.05f, 1.0f);
                        }
                    }
                } else if (mobType == 3) { 
                    if (isAccent) { 
                        pixelColor.set(0.95f, 0.05f, 0.05f, 1.0f);
                    } else {
                        if ((x * y) % 12 == 0) {
                            pixelColor.r *= 0.65f;
                            pixelColor.g *= 0.65f;
                            pixelColor.b *= 0.65f;
                        }
                    }
                } else if (mobType == 4) { 
                    if (isAccent) { 
                        if (y == 6 && (x == 6 || x == 24)) { 
                            pixelColor.set(0.1f, 0.1f, 0.1f, 1.0f);
                        }
                    } else {
                        if (y % 4 == 0) pixelColor.r *= 0.92f;
                    }
                } else if (mobType == 5) { 
                    if (isAccent) { 
                        if (rand.nextFloat() > 0.70f) {
                            pixelColor.set(0.98f, 0.50f, 0.08f, 1.0f);
                        }
                    }
                }

                byte r = (byte) Math.min(255, Math.max(0, (int) (pixelColor.r * 255 * noise)));
                byte g = (byte) Math.min(255, Math.max(0, (int) (pixelColor.g * 255 * noise)));
                byte b = (byte) Math.min(255, Math.max(0, (int) (pixelColor.b * 255 * noise)));
                byte a = (byte) 255;

                buffer.put(r).put(g).put(b).put(a);
            }
        }
        buffer.flip();

        Image image = new Image(Image.Format.RGBA8, size, size, buffer);
        
        try {
            MipMapGenerator.generateMipMaps(image);
        } catch (Exception e) {
            // fallback
        }

        Texture2D texture = new Texture2D(image);
        texture.setMagFilter(com.jme3.texture.Texture.MagFilter.Nearest);
        texture.setMinFilter(com.jme3.texture.Texture.MinFilter.NearestLinearMipMap);
        
        texture.setWrap(com.jme3.texture.Texture.WrapAxis.S, com.jme3.texture.Texture.WrapMode.Repeat);
        texture.setWrap(com.jme3.texture.Texture.WrapAxis.T, com.jme3.texture.Texture.WrapMode.Repeat);
        
        return texture;
    }
}