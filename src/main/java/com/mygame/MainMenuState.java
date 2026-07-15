package com.mygame;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.zip.GZIPInputStream;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Quad;

public class MainMenuState extends BaseAppState implements RawInputListener {

    public interface WorldStartCallback {
        void onStart(String slotFile, long seed, boolean isCreative, boolean isFlat);
    }

    private enum MenuPage {
        MAIN, SLOTS, NEW_WORLD
    }

    private SimpleApplication app;
    private Node menuNode;
    private BitmapFont font;

    private final List<MenuButton> buttons = new ArrayList<>();
    private final List<PixelParticle> particles = new ArrayList<>();
    private final Random random = new Random();

    private float lastScreenWidth;
    private float lastScreenHeight;

    private final WorldStartCallback onStartGame;

    private MenuPage currentPage = MenuPage.MAIN;
    private long pendingSeed = 0;
    
    private boolean isCreative = true;
    private boolean isFlat = false;

    private MenuButton seedDisplayBtn;
    private MenuButton modeToggleBtn;
    private MenuButton typeToggleBtn;

    private BitmapText titleText;
    private BitmapText titleShadow;
    private BitmapText splashText; 
    private float titleAnimTime = 0f;

    private List<File> worldFilesList = new ArrayList<>();
    private int currentSavePage = 0;
    private static final int SAVES_PER_PAGE = 3;

    private final String[] splashes = {
        "PROCEDURAL!", "VOXEL SANDBOX!", "DESOLATING!", "JAVA POWERED!", "DYNAMIC SAVES!", "LOW POLY!"
    };
    private final String activeSplash;

    public MainMenuState(WorldStartCallback onStartGame) {
        this.onStartGame = onStartGame;
        this.activeSplash = splashes[random.nextInt(splashes.length)];
    }

    @Override
    protected void initialize(Application app) {
        this.app = (SimpleApplication) app;
        this.menuNode = new Node("MainMenuNode");
        this.font = app.getAssetManager().loadFont("Interface/Fonts/Default.fnt");

        this.lastScreenWidth = app.getCamera().getWidth();
        this.lastScreenHeight = app.getCamera().getHeight();

        for (int i = 0; i < 120; i++) {
            createPixelParticle(lastScreenWidth, lastScreenHeight);
        }

        createVoxelBackground();
        createButtons(lastScreenWidth, lastScreenHeight);

        titleShadow = new BitmapText(font);
        titleShadow.setText("DESOLATOR CRAFT");
        titleShadow.setSize(54);
        titleShadow.setColor(ColorRGBA.Black);
        menuNode.attachChild(titleShadow);

        titleText = new BitmapText(font);
        titleText.setText("DESOLATOR CRAFT");
        titleText.setSize(54);
        titleText.setColor(new ColorRGBA(0.95f, 0.78f, 0.15f, 1.0f)); 
        menuNode.attachChild(titleText);

        splashText = new BitmapText(font);
        splashText.setText(activeSplash);
        splashText.setSize(20);
        splashText.setColor(ColorRGBA.Yellow);
        menuNode.attachChild(splashText);

        app.getInputManager().addRawInputListener(this);
    }

    @Override
    protected void cleanup(Application app) {
        buttons.clear();
        particles.clear();
        if (voxelWorld != null) voxelWorld.removeFromParent();
    }


    private Node voxelWorld;

    private void createVoxelBackground() {
        voxelWorld = new Node("VoxelWorld");
        ColorRGBA[] biomeColors = {
            new ColorRGBA(0.30f, 0.70f, 0.30f, 1),
            new ColorRGBA(0.45f, 0.30f, 0.18f, 1),
            new ColorRGBA(0.50f, 0.50f, 0.54f, 1),
            new ColorRGBA(0.88f, 0.84f, 0.55f, 1),
            new ColorRGBA(0.88f, 0.72f, 0.58f, 1),
            new ColorRGBA(0.20f, 0.50f, 0.16f, 1),
            new ColorRGBA(0.50f, 0.88f, 0.98f, 1),
            new ColorRGBA(0.70f, 0.32f, 0.98f, 1),
        };
        int N = 9;
        float s = 1.2f;
        for (int x = 0; x < N; x++) {
            for (int z = 0; z < N; z++) {
                float dx = x - (N - 1) / 2f, dz = z - (N - 1) / 2f;
                float d = (float) Math.sqrt(dx * dx + dz * dz);
                int h = (int) (4 - d * 0.9f);
                if (h <= 0) continue;
                for (int y = 0; y < h; y++) {
                    ColorRGBA c = (y == h - 1) ? biomeColors[(x + z) % biomeColors.length] : biomeColors[2];
                    if (y < h - 1 && (x + z + y) % 5 == 0) c = biomeColors[1];
                    Geometry g = new Geometry("Voxel", new Box(s / 2, s / 2, s / 2));
                    Material m = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
                    m.setColor("Color", c);
                    g.setMaterial(m);
                    g.setLocalTranslation(x * s - N * s / 2, y * s, z * s - N * s / 2);
                    voxelWorld.attachChild(g);
                }
            }
        }
        // В 3D-сцену (rootNode), а не в ortho-guiNode — иначе кубы 1.2 единицы
        // превращаются в 1.2 пикселя без перспективы и остров не виден.
        voxelWorld.setLocalTranslation(0, 1.5f, 0);
        voxelWorld.rotate(0.25f, 0, 0.08f);
        app.getRootNode().attachChild(voxelWorld);
    }

    private void createPixelParticle(float width, float height) {
        // Мелкие «звёзды» — тусклые точки, а не крупные падающие квадраты
        float size = random.nextFloat() * 1.5f + 1.5f;
        Quad quad = new Quad(size, size);
        Geometry geom = new Geometry("Pixel", quad);
        
        Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        float alpha = random.nextFloat() * 0.25f + 0.08f;
        mat.setColor("Color", new ColorRGBA(0.75f, 0.78f, 0.85f, alpha));
        mat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        geom.setMaterial(mat);

        float x = random.nextFloat() * width;
        float y = random.nextFloat() * height;
        geom.setLocalTranslation(x, y, -1);

        menuNode.attachChild(geom);

        PixelParticle p = new PixelParticle();
        p.geom = geom;
        p.x = x;
        p.y = y;
        p.speed = random.nextFloat() * 8 + 4; // медленно
        p.size = size;
        particles.add(p);
    }

    private void switchPage(MenuPage newPage) {
        this.currentPage = newPage;
        for (MenuButton btn : buttons) {
            btn.btnNode.removeFromParent();
        }
        buttons.clear();
        createButtons(lastScreenWidth, lastScreenHeight);
    }

    // --- Изменено на сканирование бинарных *.dat файлов ---
    private void scanWorldFiles() {
        worldFilesList.clear();
        File dir = new File(".");
        File[] found = dir.listFiles((d, name) -> name.startsWith("save_") && name.endsWith(".dat"));
        if (found != null) {
            Arrays.sort(found, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File f : found) {
                worldFilesList.add(f);
            }
        }
    }

    private void createButtons(float screenWidth, float screenHeight) {
        float startY = screenHeight / 2;

        if (currentPage == MenuPage.MAIN) {
            addButton("PLAY GAME", screenWidth / 2, startY + 50, () -> {
                scanWorldFiles();
                currentSavePage = 0;
                switchPage(MenuPage.SLOTS);
            });

            addButton("SETTINGS", screenWidth / 2, startY - 10, () -> {
                MainMenuState mainMenu = app.getStateManager().getState(MainMenuState.class);
                app.getStateManager().detach(mainMenu); 

                app.getStateManager().attach(new SettingsMenuState(() -> {
                    app.getStateManager().detach(app.getStateManager().getState(SettingsMenuState.class));
                    app.getStateManager().attach(mainMenu); 
                }));
            });

            addButton("EXIT GAME", screenWidth / 2, startY - 70, () -> {
                app.stop();
            });
        } else if (currentPage == MenuPage.SLOTS) {
            int totalSaves = worldFilesList.size();
            int startIdx = currentSavePage * SAVES_PER_PAGE;
            int endIdx = Math.min(startIdx + SAVES_PER_PAGE, totalSaves);

            if (totalSaves == 0) {
                addButton("NO WORLDS FOUND", screenWidth / 2, startY + 90, null);
            } else {
                float currentY = startY + 90;
                for (int i = startIdx; i < endIdx; i++) {
                    File file = worldFilesList.get(i);
                    SlotSettings settings = readSlotSettings(file);
                    String name = "WORLD: " + file.getName().replace("save_", "").replace(".dat", "") 
                                + " [" + (settings.isFlat ? "FLAT" : "CLASSIC") + " / " 
                                + (settings.isCreative ? "CREATIVE" : "SURVIVAL") + "]";
                    
                    addButton(name, screenWidth / 2, currentY, () -> {
                        onStartGame.onStart(file.getName(), settings.seed, settings.isCreative, settings.isFlat);
                    });
                    currentY -= 65;
                }
            }

            addButton("CREATE NEW WORLD", screenWidth / 2, startY - 110, () -> {
                this.pendingSeed = Math.abs(random.nextLong() % 100000000L);
                switchPage(MenuPage.NEW_WORLD);
            });

            if (currentSavePage > 0) {
                addButton("<- PREV PAGE", screenWidth / 2 - 100, startY - 170, () -> {
                    currentSavePage--;
                    switchPage(MenuPage.SLOTS);
                });
            }
            if (endIdx < totalSaves) {
                addButton("NEXT PAGE ->", screenWidth / 2 + 100, startY - 170, () -> {
                    currentSavePage++;
                    switchPage(MenuPage.SLOTS);
                });
            }

            addButton("BACK", screenWidth / 2, startY - 225, () -> {
                switchPage(MenuPage.MAIN);
            });
        } else if (currentPage == MenuPage.NEW_WORLD) {
            seedDisplayBtn = addButton("SEED: " + pendingSeed, screenWidth / 2, startY + 110, this::randomizePendingSeed);

            modeToggleBtn = addButton("MODE: " + (isCreative ? "CREATIVE" : "SURVIVAL"), screenWidth / 2, startY + 50, () -> {
                isCreative = !isCreative;
                modeToggleBtn.setText("MODE: " + (isCreative ? "CREATIVE" : "SURVIVAL"));
            });

            typeToggleBtn = addButton("WORLD: " + (isFlat ? "FLAT" : "CLASSIC"), screenWidth / 2, startY - 10, () -> {
                isFlat = !isFlat;
                typeToggleBtn.setText("WORLD: " + (isFlat ? "FLAT" : "CLASSIC"));
            });

            addButton("CREATE & PLAY", screenWidth / 2, startY - 80, () -> {
                // Изменено расширение файла нового мира на *.dat
                String newFileName = "save_" + (System.currentTimeMillis() / 1000L) + ".dat";
                onStartGame.onStart(newFileName, pendingSeed, isCreative, isFlat);
            });

            addButton("BACK", screenWidth / 2, startY - 150, () -> {
                scanWorldFiles();
                switchPage(MenuPage.SLOTS);
            });
        }
    }

    // --- Чтение настроек напрямую из сжатого бинарного файла ---
    private SlotSettings readSlotSettings(File file) {
        long seed = 0;
        boolean creative = true;
        boolean flat = false;

        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis);
             GZIPInputStream gzis = new GZIPInputStream(bis);
             DataInputStream dis = new DataInputStream(gzis)) {

            int magic = dis.readInt();
            if (magic == 0x44534346) {
                int version = dis.readInt();
                seed = dis.readLong();
                creative = dis.readBoolean();
                flat = dis.readBoolean();
            }
        } catch (Exception e) {
            System.err.println("SYSTEM ERROR: Failed to read slot binary data: " + e.getMessage());
        }
        return new SlotSettings(seed, creative, flat);
    }

    private static class SlotSettings {
        long seed;
        boolean isCreative;
        boolean isFlat;
        SlotSettings(long seed, boolean creative, boolean flat) {
            this.seed = seed;
            this.isCreative = creative;
            this.isFlat = flat;
        }
    }

    private void randomizePendingSeed() {
        this.pendingSeed = Math.abs(random.nextLong() % 100000000L);
        if (seedDisplayBtn != null) {
            seedDisplayBtn.setText("SEED: " + pendingSeed);
        }
    }

    private MenuButton addButton(String text, float centerX, float centerY, Runnable action) {
        float w = 420;
        float h = 46;
        
        Quad quad = new Quad(w, h);
        Geometry geom = new Geometry("Btn_" + text, quad);
        Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        
        ColorRGBA baseColor = new ColorRGBA(0.16f, 0.17f, 0.22f, 0.80f);
        mat.setColor("Color", baseColor.clone());
        mat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        geom.setMaterial(mat);

        BitmapText btnText = new BitmapText(font);
        btnText.setText(text);
        btnText.setSize(19);
        btnText.setColor(ColorRGBA.White);

        Node border = createBorder(w, h, 2.5f, new ColorRGBA(0.30f, 0.32f, 0.40f, 1.0f), 1f);
        // Свечение при наведении (яркая рамка, скрыта по умолчанию).
        // ЦЕНТРИРУЕМ относительно кнопки (btnNode центр = 0,0), иначе
        // масштабирование в update уводит рамку в сторону ("жёлтая линия").
        Node glow = createBorder(w + 10, h + 10, 4f, new ColorRGBA(0.95f, 0.78f, 0.15f, 1.0f), 1f);
        glow.setLocalTranslation(-(w + 10) / 2f, -(h + 10) / 2f, -0.2f);
        glow.setCullHint(com.jme3.scene.Spatial.CullHint.Always);

        Node btnNode = new Node("BtnNode_" + text);
        btnNode.attachChild(glow);
        btnNode.attachChild(geom);
        btnNode.attachChild(btnText);
        btnNode.attachChild(border);

        geom.setLocalTranslation(-w / 2, -h / 2, 0);
        border.setLocalTranslation(-w / 2, -h / 2, 0.5f);
        btnText.setLocalTranslation(-btnText.getLineWidth() / 2, btnText.getLineHeight() / 2, 1);

        btnNode.setLocalTranslation(centerX, centerY, 1);
        menuNode.attachChild(btnNode);

        MenuButton button = new MenuButton(btnNode, geom, btnText, w, h, baseColor, action, glow);
        buttons.add(button);
        return button;
    }

    private Node createBorder(float w, float h, float thickness, ColorRGBA color, float z) {
        Node borderNode = new Node("BorderNode");
        Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        mat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);

        Geometry top = new Geometry("B_Top", new Quad(w, thickness));
        top.setMaterial(mat);
        top.setLocalTranslation(0, h - thickness, z);
        borderNode.attachChild(top);

        Geometry bottom = new Geometry("B_Bottom", new Quad(w, thickness));
        bottom.setMaterial(mat);
        bottom.setLocalTranslation(0, 0, z);
        borderNode.attachChild(bottom);

        Geometry left = new Geometry("B_Left", new Quad(thickness, h));
        left.setMaterial(mat);
        left.setLocalTranslation(0, 0, z);
        borderNode.attachChild(left);

        Geometry right = new Geometry("B_Right", new Quad(thickness, h));
        right.setMaterial(mat);
        right.setLocalTranslation(w - thickness, 0, z);
        borderNode.attachChild(right);

        return borderNode;
    }

    private void adaptToResolution(float newWidth, float newHeight) {
        switchPage(currentPage);
        for (PixelParticle p : particles) {
            p.x = random.nextFloat() * newWidth;
            p.y = random.nextFloat() * newHeight;
        }
    }

    @Override
    public void update(float tpf) {
        if (voxelWorld != null) {
            voxelWorld.rotate(0, tpf * 0.25f, 0);
        }
        float currentWidth = app.getCamera().getWidth();
        float currentHeight = app.getCamera().getHeight();

        if (currentWidth != lastScreenWidth || currentHeight != lastScreenHeight) {
            lastScreenWidth = currentWidth;
            lastScreenHeight = currentHeight;
            adaptToResolution(currentWidth, currentHeight);
        }

        for (PixelParticle p : particles) {
            p.y += p.speed * tpf;
            if (p.y > lastScreenHeight) {
                p.y = -p.size;
                p.x = random.nextFloat() * lastScreenWidth; 
            }
            p.geom.setLocalTranslation(p.x, p.y, -1);
        }

        titleAnimTime += tpf * 2.0f;
        float titleScale = 1.0f + FastMath.sin(titleAnimTime) * 0.03f;
        
        titleText.setLocalScale(titleScale);
        titleShadow.setLocalScale(titleScale);
        
        float titleX = lastScreenWidth / 2 - (titleText.getLineWidth() * titleScale) / 2;
        float titleY = lastScreenHeight - 120 + FastMath.cos(titleAnimTime * 1.5f) * 6.0f;
        
        titleShadow.setLocalTranslation(titleX + 3f, titleY - 3f, 2);
        titleText.setLocalTranslation(titleX, titleY, 3);

        float splashScale = 1.0f + FastMath.sin(titleAnimTime * 3.5f) * 0.12f;
        splashText.setLocalScale(splashScale);
        
        Quaternion splashRot = new Quaternion().fromAngleAxis(-0.26f, Vector3f.UNIT_Z);
        splashText.setLocalRotation(splashRot);
        
        float splashX = titleX + (titleText.getLineWidth() * titleScale) - 40f;
        float splashY = titleY - 15f;
        splashText.setLocalTranslation(splashX, splashY, 4);

        Vector2f mousePos = app.getInputManager().getCursorPosition();
        for (MenuButton btn : buttons) {
            if (btn.action == null) continue;

            boolean isHovered = btn.contains(mousePos.x, mousePos.y);
            
            ColorRGBA targetColor = isHovered ? btn.hoverColor : btn.baseColor;
            btn.currentColor.interpolateLocal(targetColor, tpf * 10f);
            btn.geom.getMaterial().setColor("Color", btn.currentColor);

            float targetScale = isHovered ? 1.05f : 1.0f;
            btn.currentScale = FastMath.interpolateLinear(tpf * 12.0f, btn.currentScale, targetScale);
            btn.btnNode.setLocalScale(btn.currentScale);

            // Свечение при наведении с пульсацией
            if (btn.glow != null) {
                if (isHovered) {
                    btn.glowPulse += tpf * 6.0f;
                    float a = 0.55f + FastMath.sin(btn.glowPulse) * 0.35f;
                    btn.glow.setCullHint(com.jme3.scene.Spatial.CullHint.Inherit);
                    // обновим прозрачность рамок glow (центрированы, масштаб не нужен)
                    for (int i = 0; i < btn.glow.getQuantity(); i++) {
                        Geometry g = (Geometry) btn.glow.getChild(i);
                        g.getMaterial().setColor("Color", new ColorRGBA(0.95f, 0.78f, 0.15f, a));
                    }
                } else {
                    btn.glow.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
                }
            }
        }
    }

    @Override
    protected void onEnable() {
        app.getGuiNode().attachChild(menuNode);
        app.getFlyByCamera().setEnabled(false);
        app.getInputManager().setCursorVisible(true);
        // Ночное небо как фон 3D-сцены (остров виден поверх)
        app.getViewPort().setBackgroundColor(new ColorRGBA(0.05f, 0.07f, 0.12f, 1.0f));
        // Камера смотрит на вращающийся воксельный остров в центре 3D-сцены
        app.getCamera().setLocation(new Vector3f(0, 3.2f, 12f));
        app.getCamera().lookAt(new Vector3f(0, 1.5f, 0), Vector3f.UNIT_Y);
    }

    @Override
    protected void onDisable() {
        menuNode.removeFromParent();
        app.getInputManager().removeRawInputListener(this);
    }

    @Override
    public void onMouseButtonEvent(MouseButtonEvent evt) {
        if (evt.isReleased() && evt.getButtonIndex() == 0) {
            for (MenuButton btn : buttons) {
                if (btn.action != null && btn.contains(evt.getX(), evt.getY())) {
                    btn.action.run();
                    break;
                }
            }
        }
    }

    @Override public void beginInput() {}
    @Override public void endInput() {}
    @Override public void onJoyAxisEvent(com.jme3.input.event.JoyAxisEvent evt) {}
    @Override public void onJoyButtonEvent(com.jme3.input.event.JoyButtonEvent evt) {}
    @Override public void onMouseMotionEvent(MouseMotionEvent evt) {}
    @Override public void onKeyEvent(com.jme3.input.event.KeyInputEvent evt) {}
    @Override public void onTouchEvent(com.jme3.input.event.TouchEvent evt) {}

    private static class MenuButton {
        Node btnNode;
        Geometry geom;
        BitmapText textNode;
        float w, h;
        ColorRGBA baseColor;
        ColorRGBA hoverColor;
        ColorRGBA currentColor;
        float currentScale = 1.0f;
        Runnable action;
        Node glow;
        float glowPulse = 0f;

        public MenuButton(Node btnNode, Geometry geom, BitmapText textNode, float w, float h, ColorRGBA baseColor, Runnable action, Node glow) {
            this.btnNode = btnNode;
            this.geom = geom;
            this.textNode = textNode;
            this.w = w;
            this.h = h;
            this.baseColor = baseColor;
            this.hoverColor = new ColorRGBA(0.35f, 0.38f, 0.45f, 0.95f);
            this.currentColor = baseColor.clone();
            this.action = action;
            this.glow = glow;
        }

        public void setText(String newText) {
            if (textNode != null) {
                textNode.setText(newText);
                textNode.setLocalTranslation(-textNode.getLineWidth() / 2, textNode.getLineHeight() / 2, 1);
            }
        }

        public boolean contains(float mx, float my) {
            float bx = btnNode.getLocalTranslation().x;
            float by = btnNode.getLocalTranslation().y;
            return mx >= bx - w / 2 && mx <= bx + w / 2 && my >= by - h / 2 && my <= by + h / 2;
        }
    }

    private static class PixelParticle {
        Geometry geom;
        float x, y;
        float speed;
        float size;
    }
}