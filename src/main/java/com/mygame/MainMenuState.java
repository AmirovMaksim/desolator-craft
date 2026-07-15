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
import com.jme3.asset.TextureKey;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.ui.Picture;

/**
 * Главное меню на спрайтах. Фон/лого/кнопки — PNG из Interface/Menu/*.png.
 * Альфа поддерживается (Picture + BlendMode.Alpha). Координаты: ось Y экрана —
 * сверху вниз (как в GUI jME), поэтому спрайты кладём по нормированным
 * координатам вырезки из референса (1024x559).
 */
public class MainMenuState extends BaseAppState implements RawInputListener {

    public interface WorldStartCallback {
        void onStart(String slotFile, long seed, boolean isCreative, boolean isFlat, int playerClass);
    }

    private enum MenuPage { MAIN, SLOTS, NEW_WORLD }

    private SimpleApplication app;
    private Main mainApp;
    private Node menuNode;
    private BitmapFont font;

    private final List<MenuButton> buttons = new ArrayList<>();
    private Object prevHovered = null;
    private final List<Ember> embers = new ArrayList<>();
    private final Random random = new Random();

    private float lastScreenWidth;
    private float lastScreenHeight;

    private final WorldStartCallback onStartGame;

    private MenuPage currentPage = MenuPage.MAIN;
    private long pendingSeed = 0;
    private int pendingClass = 0;

    private boolean isCreative = true;
    private boolean isFlat = false;

    private MenuButton seedDisplayBtn;
    private MenuButton modeToggleBtn;
    private MenuButton classBtn;
    private MenuButton typeToggleBtn;

    private Picture bgPic;
    private final List<SpriteButton> spriteButtons = new ArrayList<>();

    private List<File> worldFilesList = new ArrayList<>();
    private int currentSavePage = 0;
    private static final int SAVES_PER_PAGE = 3;

    // нормированные координаты вырезки кнопок в референсе (x0,x1,y0,h) / (REF_W,REF_H)
    // точные границы найдены сканом (каждая кнопка — цельный кусок, без стыка)
    private static final float[][] BTN_COORDS = {
        {388, 636, 274, 22}, // НОВАЯ ИГРА
        {388, 636, 305, 49}, // ПРОДОЛЖИТЬ
        {388, 636, 359, 49}, // НАСТРОЙКИ
        {388, 636, 423, 47}, // МАСТЕРСКАЯ
        {388, 636, 475, 51}, // ВЫХОД
    };
    private static final int REF_W = 1024, REF_H = 559;

    public MainMenuState(WorldStartCallback onStartGame) {
        this.onStartGame = onStartGame;
    }

    /** Грузит PNG как Texture2D. flipY=true — корректная ориентация GUI в jME. */
    private Texture2D tex(String path) {
        Texture t = app.getAssetManager().loadTexture(new TextureKey("Interface/Menu/" + path, true));
        return (Texture2D) t;
    }

    @Override
    protected void initialize(Application app) {
        this.app = (SimpleApplication) app;
        this.mainApp = (Main) app;
        this.menuNode = new Node("MainMenuNode");
        this.font = app.getAssetManager().loadFont("Interface/Fonts/Default.fnt");

        this.lastScreenWidth = app.getCamera().getWidth();
        this.lastScreenHeight = app.getCamera().getHeight();

        // ФОН (содержит логотип из референса целиком; кнопки — отдельными спрайтами поверх)
        bgPic = new Picture("MenuBG");
        bgPic.setTexture(app.getAssetManager(), tex("background.png"), true);
        bgPic.setLocalTranslation(0, 0, -10);
        menuNode.attachChild(bgPic);

        // эмберы (атмосфера)
        for (int i = 0; i < 40; i++) createEmber(lastScreenWidth, lastScreenHeight);

        buildSpriteButtons();

        app.getInputManager().setCursorVisible(true);
        app.getViewPort().setBackgroundColor(new ColorRGBA(0.04f, 0.03f, 0.08f, 1.0f));

        this.app.getGuiNode().attachChild(menuNode);
        app.getInputManager().addRawInputListener(this);
        layoutSprites();
    }

    private void buildSpriteButtons() {
        spriteButtons.add(new SpriteButton("btn_new_game.png", () -> {
            this.pendingSeed = Math.abs(random.nextLong() % 100000000L);
            switchPage(MenuPage.NEW_WORLD);
        }));
        spriteButtons.add(new SpriteButton("btn_continue.png", () -> {
            scanWorldFiles(); currentSavePage = 0; switchPage(MenuPage.SLOTS);
        }));
        spriteButtons.add(new SpriteButton("btn_settings.png", () -> {
            MainMenuState m = app.getStateManager().getState(MainMenuState.class);
            app.getStateManager().detach(m);
            app.getStateManager().attach(new SettingsMenuState(() -> {
                app.getStateManager().detach(app.getStateManager().getState(SettingsMenuState.class));
                app.getStateManager().attach(m);
            }));
        }));
        spriteButtons.add(new SpriteButton("btn_workshop.png", () -> {
            if (mainApp.soundManager != null) mainApp.soundManager.uiClick();
        }));
        spriteButtons.add(new SpriteButton("btn_exit.png", () -> app.stop()));
    }

    private void layoutSprites() {
        float w = lastScreenWidth, h = lastScreenHeight;
        bgPic.setWidth(w); bgPic.setHeight(h);

        for (int i = 0; i < spriteButtons.size(); i++) {
            SpriteButton sb = spriteButtons.get(i);
            float[] c = BTN_COORDS[i];
            float nx0 = c[0] / REF_W, nx1 = c[1] / REF_W;
            float ny0 = c[2] / REF_H, nh = c[3] / REF_H;
            float bw = (nx1 - nx0) * w;
            float bh = nh * h;
            float cx = ((nx0 + nx1) / 2f) * w;
            float cy = h - ((ny0 + nh / 2f)) * h; // GUI Y (сверху вниз)

            if (sb.pic == null) {
                sb.pic = new Picture("SB_" + i);
                sb.pic.setTexture(app.getAssetManager(), tex(sb.texName), true);
                menuNode.attachChild(sb.pic);
            }
            sb.w = bw; sb.h = bh; sb.cx = cx; sb.cy = cy;
            sb.pic.setWidth(bw); sb.pic.setHeight(bh);
            sb.pic.setLocalTranslation(cx - bw / 2, cy - bh / 2, -7);
        }
    }

    private void createEmber(float w, float h) {
        Ember e = new Ember();
        float sz = 2f + random.nextFloat() * 3f;
        Geometry g = new Geometry("Ember", new Quad(sz, sz));
        Material m = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", new ColorRGBA(1.0f, 0.6f, 0.2f, 0.9f));
        m.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Additive);
        g.setMaterial(m);
        e.geom = g;
        e.x = random.nextFloat() * w;
        e.y = random.nextFloat() * h;
        e.baseX = e.x;
        e.size = sz;
        e.speed = 10f + random.nextFloat() * 25f;
        e.sway = 10f + random.nextFloat() * 25f;
        e.phase = random.nextFloat() * 6.28f;
        g.setLocalTranslation(e.x, e.y, -2);
        menuNode.attachChild(g);
        embers.add(e);
    }

    @Override
    protected void cleanup(Application app) {
        app.getInputManager().removeRawInputListener(this);
        menuNode.removeFromParent();
    }

    @Override
    protected void onEnable() {}

    @Override
    protected void onDisable() {
        menuNode.removeFromParent();
        app.getInputManager().removeRawInputListener(this);
    }

    private void switchPage(MenuPage newPage) {
        this.currentPage = newPage;
        boolean showSprites = (newPage == MenuPage.MAIN);
        for (SpriteButton sb : spriteButtons) {
            if (sb.pic != null) sb.pic.setCullHint(showSprites ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        }
        for (MenuButton btn : buttons) btn.btnNode.removeFromParent();
        buttons.clear();
        pageBtnCount = 0;
        createButtons(lastScreenWidth, lastScreenHeight);
    }

    private void scanWorldFiles() {
        worldFilesList.clear();
        File dir = new File(".");
        File[] found = dir.listFiles((d, name) -> name.startsWith("save_") && name.endsWith(".dat"));
        if (found != null) {
            Arrays.sort(found, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File f : found) worldFilesList.add(f);
        }
    }

    private int pageBtnCount = 0;

    private void createButtons(float screenWidth, float screenHeight) {
        if (currentPage != MenuPage.MAIN) {
            float startY = screenHeight / 2 - 20;
            if (currentPage == MenuPage.SLOTS) {
                int totalSaves = worldFilesList.size();
                int startIdx = currentSavePage * SAVES_PER_PAGE;
                int endIdx = Math.min(startIdx + SAVES_PER_PAGE, totalSaves);
                if (totalSaves == 0) {
                    addButton("НЕТ СОХРАНЕНИЙ", screenWidth / 2, startY + 80, new ColorRGBA(0.4f, 0.42f, 0.46f, 0.9f), null);
                } else {
                    float currentY = startY + 80;
                    for (int i = startIdx; i < endIdx; i++) {
                        File file = worldFilesList.get(i);
                        SlotSettings settings = readSlotSettings(file);
                        String name = file.getName().replace("save_", "").replace(".dat", "")
                                    + " [" + (settings.isFlat ? "FLAT" : "CLASSIC") + "/" + (settings.isCreative ? "CR" : "SUR") + "]";
                        addButton(name, screenWidth / 2, currentY, new ColorRGBA(0.3f, 0.5f, 0.6f, 0.9f), () -> {
                            onStartGame.onStart(file.getName(), settings.seed, settings.isCreative, settings.isFlat, 0);
                        });
                        currentY -= 65;
                    }
                }
                addButton("СОЗДАТЬ МИР", screenWidth / 2, startY - 110, new ColorRGBA(0.85f, 0.45f, 0.12f, 0.9f), () -> {
                    this.pendingSeed = Math.abs(random.nextLong() % 100000000L);
                    switchPage(MenuPage.NEW_WORLD);
                });
                if (currentSavePage > 0)
                    addButton("<- НАЗАД", screenWidth / 2 - 130, startY - 180, new ColorRGBA(0.4f, 0.42f, 0.46f, 0.9f), () -> { currentSavePage--; switchPage(MenuPage.SLOTS); });
                if (endIdx < totalSaves)
                    addButton("ВПЕРЁД ->", screenWidth / 2 + 130, startY - 180, new ColorRGBA(0.4f, 0.42f, 0.46f, 0.9f), () -> { currentSavePage++; switchPage(MenuPage.SLOTS); });
                addButton("В МЕНЮ", screenWidth / 2, startY - 235, new ColorRGBA(0.4f, 0.42f, 0.46f, 0.9f), () -> switchPage(MenuPage.MAIN));
            } else if (currentPage == MenuPage.NEW_WORLD) {
                seedDisplayBtn = addButton("SEED: " + pendingSeed, screenWidth / 2, startY + 130, new ColorRGBA(0.4f, 0.42f, 0.46f, 0.9f), this::randomizePendingSeed);
                modeToggleBtn = addButton("РЕЖИМ: " + (isCreative ? "КРЕАТИВ" : "ВЫЖИВАНИЕ"), screenWidth / 2, startY + 75, new ColorRGBA(0.4f, 0.42f, 0.46f, 0.9f), () -> {
                    isCreative = !isCreative; modeToggleBtn.setText("РЕЖИМ: " + (isCreative ? "КРЕАТИВ" : "ВЫЖИВАНИЕ"));
                });
                typeToggleBtn = addButton("МИР: " + (isFlat ? "ПЛОСКИЙ" : "КЛАССИЧЕСКИЙ"), screenWidth / 2, startY + 20, new ColorRGBA(0.4f, 0.42f, 0.46f, 0.9f), () -> {
                    isFlat = !isFlat; typeToggleBtn.setText("МИР: " + (isFlat ? "ПЛОСКИЙ" : "КЛАССИЧЕСКИЙ"));
                });
                classBtn = addButton("КЛАСС: ВОИН", screenWidth / 2, startY - 35, new ColorRGBA(0.4f, 0.42f, 0.46f, 0.9f), null);
                String[] names = {"ВОИН", "МАГ", "ЛУЧНИК"};
                for (int ci = 0; ci < 3; ci++) {
                    final int cid = ci;
                    float bx = screenWidth / 2 - 300 + ci * 150;
                    addButton("> " + names[ci], bx, startY - 90, new ColorRGBA(0.5f, 0.4f, 0.6f, 0.9f), () -> {
                        pendingClass = cid; classBtn.setText("КЛАСС: " + names[cid]);
                    });
                }
                addButton("СОЗДАТЬ И ИГРАТЬ", screenWidth / 2, startY - 140, new ColorRGBA(0.85f, 0.45f, 0.12f, 0.9f), () -> {
                    String newFileName = "save_" + (System.currentTimeMillis() / 1000L) + ".dat";
                    onStartGame.onStart(newFileName, pendingSeed, isCreative, isFlat, pendingClass);
                });
                addButton("В МЕНЮ", screenWidth / 2, startY - 200, new ColorRGBA(0.4f, 0.42f, 0.46f, 0.9f), () -> { scanWorldFiles(); switchPage(MenuPage.MAIN); });
            }
        }
    }

    private SlotSettings readSlotSettings(File file) {
        long seed = 0; boolean creative = true; boolean flat = false;
        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis);
             GZIPInputStream gzis = new GZIPInputStream(bis);
             DataInputStream dis = new DataInputStream(gzis)) {
            int magic = dis.readInt();
            if (magic == 0x44534346) {
                dis.readInt();
                seed = dis.readLong();
                creative = dis.readBoolean();
                flat = dis.readBoolean();
            }
        } catch (Exception e) { System.err.println("SYSTEM ERROR: " + e.getMessage()); }
        return new SlotSettings(seed, creative, flat);
    }

    private static class SlotSettings {
        long seed; boolean isCreative; boolean isFlat;
        SlotSettings(long seed, boolean creative, boolean flat) { this.seed = seed; this.isCreative = creative; this.isFlat = flat; }
    }

    private void randomizePendingSeed() {
        this.pendingSeed = Math.abs(random.nextLong() % 100000000L);
        if (seedDisplayBtn != null) seedDisplayBtn.setText("SEED: " + pendingSeed);
    }

    private MenuButton addButton(String text, float centerX, float centerY, ColorRGBA tint, Runnable action) {
        float w = 460, h = 58; float order = pageBtnCount++;
        BitmapText btnShadow = new BitmapText(font); btnShadow.setText(text); btnShadow.setSize(24); btnShadow.setColor(new ColorRGBA(0,0,0,0.6f));
        BitmapText btnText = new BitmapText(font); btnText.setText(text); btnText.setSize(24); btnText.setColor(new ColorRGBA(0.96f,0.96f,0.98f,1));
        Node btnNode = new Node("BtnNode_" + text);
        btnNode.attachChild(btnShadow); btnNode.attachChild(btnText);
        btnShadow.setLocalTranslation(-btnShadow.getLineWidth() / 2 + 2, btnShadow.getLineHeight() / 2 - 2, 1);
        btnText.setLocalTranslation(-btnText.getLineWidth() / 2, btnText.getLineHeight() / 2, 1.1f);
        btnNode.setLocalTranslation(centerX, centerY, 1);
        menuNode.attachChild(btnNode);
        MenuButton button = new MenuButton(btnNode, btnText, btnShadow, w, h, tint, action);
        button.appearDelay = order * 0.06f; button.baseY = centerY;
        button.btnNode.setLocalTranslation(centerX, centerY + button.slideOffset, 1);
        buttons.add(button);
        return button;
    }

    private float bgAnimTime = 0f;

    @Override
    public void update(float tpf) {
        bgAnimTime += tpf;
        float currentWidth = app.getCamera().getWidth();
        float currentHeight = app.getCamera().getHeight();
        if (currentWidth != lastScreenWidth || currentHeight != lastScreenHeight) {
            lastScreenWidth = currentWidth; lastScreenHeight = currentHeight;
            layoutSprites();
        }

        float H = app.getCamera().getHeight();
        float W = app.getCamera().getWidth();
        for (Ember e : embers) {
            e.y += e.speed * tpf; e.phase += tpf * 1.5f;
            if (e.y > H + 10) { e.y = -10; e.baseX = random.nextFloat() * W; }
            float x = e.baseX + FastMath.sin(e.phase) * e.sway;
            float flick = 0.5f + 0.5f * FastMath.sin(e.phase * 2.3f);
            e.geom.setLocalTranslation(x, e.y, -2);
            ColorRGBA c = (ColorRGBA) e.geom.getMaterial().getParam("Color").getValue();
            e.geom.getMaterial().setColor("Color", new ColorRGBA(c.r, c.g, c.b, c.a * flick));
        }

        Vector2f mousePos = app.getInputManager().getCursorPosition();

        if (currentPage == MenuPage.MAIN) {
            for (SpriteButton sb : spriteButtons) {
                boolean isHovered = mousePos.x >= sb.cx - sb.w / 2 && mousePos.x <= sb.cx + sb.w / 2
                                 && mousePos.y >= sb.cy - sb.h / 2 && mousePos.y <= sb.cy + sb.h / 2;
                if (isHovered && prevHovered != sb && mainApp.soundManager != null) mainApp.soundManager.uiHover();
                float target = isHovered ? 1.07f : 1.0f;
                sb.scale = FastMath.interpolateLinear(tpf * 12f, sb.scale, target);
                float cw = sb.w * sb.scale, ch = sb.h * sb.scale;
                if (sb.pic != null) {
                    sb.pic.setWidth(cw); sb.pic.setHeight(ch);
                    sb.pic.setLocalTranslation(sb.cx - cw / 2f, sb.cy - ch / 2f, -7);
                }
                if (isHovered) prevHovered = sb;
            }
        }

        for (MenuButton btn : buttons) {
            if (btn.action == null) continue;
            if (btn.appearT < 1.0f) {
                float t = btn.appearDelay;
                if (t <= 0f) btn.appearT = FastMath.clamp(btn.appearT + tpf * 3.0f, 0, 1);
                else if (bgAnimTime >= t) btn.appearT = FastMath.clamp(btn.appearT + tpf * 3.0f, 0, 1);
            }
            float e = 1.0f - (1.0f - btn.appearT) * (1.0f - btn.appearT);
            btn.btnNode.setLocalTranslation(btn.btnNode.getLocalTranslation().x, btn.baseY + btn.slideOffset * (1.0f - e), 1);
            boolean isHovered = btn.contains(mousePos.x, mousePos.y);
            if (isHovered && prevHovered != btn && mainApp.soundManager != null) mainApp.soundManager.uiHover();
            if (isHovered) prevHovered = btn;
        }
    }

    @Override
    public void onMouseButtonEvent(MouseButtonEvent evt) {
        if (evt.isReleased() && evt.getButtonIndex() == 0) {
            Vector2f mp = new Vector2f(evt.getX(), evt.getY());
            if (currentPage == MenuPage.MAIN) {
                for (SpriteButton sb : spriteButtons) {
                    if (mp.x >= sb.cx - sb.w / 2 && mp.x <= sb.cx + sb.w / 2 && mp.y >= sb.cy - sb.h / 2 && mp.y <= sb.cy + sb.h / 2) {
                        if (mainApp.soundManager != null) mainApp.soundManager.uiClick();
                        if (sb.action != null) sb.action.run();
                        return;
                    }
                }
            }
            for (MenuButton btn : buttons) {
                if (btn.action != null && btn.contains(mp.x, mp.y)) {
                    if (mainApp.soundManager != null) mainApp.soundManager.uiClick();
                    btn.action.run();
                    return;
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
        Node btnNode; BitmapText textNode; BitmapText shadowNode;
        float w, h; ColorRGBA tint;
        float currentScale = 1.0f; Runnable action;
        float appearDelay = 0f; float appearT = 0f; float baseY = 0f; float slideOffset = 30f;
        public MenuButton(Node btnNode, BitmapText textNode, BitmapText shadowNode,
                          float w, float h, ColorRGBA tint, Runnable action) {
            this.btnNode = btnNode; this.textNode = textNode; this.shadowNode = shadowNode;
            this.w = w; this.h = h; this.tint = tint;
            this.action = action;
        }
        public void setText(String t) {
            if (textNode != null) { textNode.setText(t); textNode.setLocalTranslation(-textNode.getLineWidth()/2, textNode.getLineHeight()/2, 1.1f); }
            if (shadowNode != null) { shadowNode.setText(t); shadowNode.setLocalTranslation(-shadowNode.getLineWidth()/2 + 2, shadowNode.getLineHeight()/2 - 2, 1); }
        }
        public boolean contains(float mx, float my) {
            float bx = btnNode.getLocalTranslation().x, by = btnNode.getLocalTranslation().y;
            return mx >= bx - w/2 && mx <= bx + w/2 && my >= by - h/2 && my <= by + h/2;
        }
    }

    private static class SpriteButton {
        String texName; Runnable action;
        Picture pic; float cx, cy, w, h, scale = 1.0f;
        SpriteButton(String texName, Runnable action) { this.texName = texName; this.action = action; }
    }

    private static class Ember {
        Geometry geom; float x, y, baseX, size, speed, sway, phase;
    }
}
