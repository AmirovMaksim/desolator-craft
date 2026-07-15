package com.mygame;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.JoyAxisEvent;
import com.jme3.input.event.JoyButtonEvent;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.input.event.TouchEvent;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

public class DevMenuState extends BaseAppState implements RawInputListener {

    private final Main mainApp;
    private Node devNode;
    private BitmapFont font;
    private Geometry backgroundOverlay;

    private final List<MenuButton> buttons = new ArrayList<>();
    private final Random random = new Random();

    private float lastScreenWidth;
    private float lastScreenHeight;

    private MenuButton btnFly;
    private MenuButton btnGod;
    private MenuButton btnAcid;
    private MenuButton btnWeather;
    private MenuButton btnTime;

    public DevMenuState(Main mainApp) {
        this.mainApp = mainApp;
    }

    @Override
    protected void initialize(Application app) {
        this.devNode = new Node("DevMenuNode");
        this.font = app.getAssetManager().loadFont("Interface/Fonts/Default.fnt");

        this.lastScreenWidth = app.getCamera().getWidth();
        this.lastScreenHeight = app.getCamera().getHeight();

        Quad bgQuad = new Quad(1, 1);
        backgroundOverlay = new Geometry("DevBg", bgQuad);
        Material bgMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        bgMat.setColor("Color", new ColorRGBA(0.05f, 0.05f, 0.07f, 0.85f)); 
        bgMat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        backgroundOverlay.setMaterial(bgMat);
        backgroundOverlay.setLocalScale(lastScreenWidth, lastScreenHeight, 1);
        devNode.attachChild(backgroundOverlay);

        createInterface(lastScreenWidth, lastScreenHeight);
        app.getInputManager().addRawInputListener(this);
    }

    @Override
    protected void cleanup(Application app) {
        buttons.clear();
    }

    private void createInterface(float w, float h) {
        float startY = h / 2 + 120;
        float centerX = w / 2;

        BitmapText title = new BitmapText(font);
        title.setText("DEVELOPER TESTING SUITE");
        title.setSize(24);
        title.setColor(new ColorRGBA(0.08f, 1.0f, 0.5f, 1.0f)); 
        title.setLocalTranslation(centerX - title.getLineWidth() / 2, startY + 50, 2);
        devNode.attachChild(title);

        float col1X = centerX - 160;

        btnFly = addButton("FLY MODE: " + (mainApp.player.isFlying ? "ON" : "OFF"), col1X, startY, () -> {
            mainApp.player.isFlying = !mainApp.player.isFlying;
            mainApp.player.velocity.set(0, 0, 0);
            btnFly.setText("FLY MODE: " + (mainApp.player.isFlying ? "ON" : "OFF"));
        });

        btnGod = addButton("GOD MODE: " + (mainApp.player.isGodMode ? "ON" : "OFF"), col1X, startY - 55, () -> {
            mainApp.player.isGodMode = !mainApp.player.isGodMode;
            btnGod.setText("GOD MODE: " + (mainApp.player.isGodMode ? "ON" : "OFF"));
        });

        btnAcid = addButton("ACID MODE: " + (mainApp.playerCam.acidMode ? "ON" : "OFF"), col1X, startY - 110, () -> {
            mainApp.playerCam.acidMode = !mainApp.playerCam.acidMode;
            btnAcid.setText("ACID MODE: " + (mainApp.playerCam.acidMode ? "ON" : "OFF"));
        });

        btnWeather = addButton("WEATHER: " + (mainApp.isRaining ? "RAIN" : "CLEAR"), col1X, startY - 165, () -> {
            mainApp.isRaining = !mainApp.isRaining;
            btnWeather.setText("WEATHER: " + (mainApp.isRaining ? "RAIN" : "CLEAR"));
        });

        float col2X = centerX + 160;

        btnTime = addButton("LOCK TIME: " + (mainApp.isTimeLocked ? "ON" : "OFF"), col2X, startY, () -> {
            mainApp.isTimeLocked = !mainApp.isTimeLocked;
            btnTime.setText("LOCK TIME: " + (mainApp.isTimeLocked ? "ON" : "OFF"));
        });

        addButton("SET TIME: DAY", col2X, startY - 55, () -> {
            mainApp.dayTimer = 1.5f; 
        });

        addButton("SET TIME: NIGHT", col2X, startY - 110, () -> {
            mainApp.dayTimer = 3.6f; 
        });

        addButton("FILL INVENTORY", col2X, startY - 165, () -> {
            for (int i = 0; i < 9; i++) {
                mainApp.player.inventory[i].blockType = (byte) (i + 1);
                mainApp.player.inventory[i].count = 64;
            }
            mainApp.syncHotbarArray();
            for (int i = 0; i < 9; i++) {
                mainApp.ui.updateHotbarIcon(mainApp.getAssetManager(), i, mainApp.player.inventory[i].blockType);
            }
        });

        addButton("SPAWN 5 RANDOM MOBS", centerX - 120, startY - 230, () -> {
            for (int i = 0; i < 5; i++) {
                float rx = mainApp.player.pos.x + (random.nextFloat() - 0.5f) * 15f;
                float rz = mainApp.player.pos.z + (random.nextFloat() - 0.5f) * 15f;
                int biomeType = random.nextInt(10);
                mainApp.mobManager.spawnMobAtBiome(mainApp.getAssetManager(), new Vector3f(rx, mainApp.player.pos.y + 4f, rz), biomeType);
            }
        });

        addButton("KILL ALL MOBS", centerX + 120, startY - 230, () -> {
            mainApp.mobManager.cleanup();
            mainApp.mobManager.init(mainApp.getAssetManager(), mainApp.getRootNode());
        });

        addButton("RESUME GAME (F4)", centerX, startY - 295, () -> {
            mainApp.toggleDevMenu();
        });
    }

    private MenuButton addButton(String text, float centerX, float centerY, Runnable action) {
        float btnW = 230;
        if (text.length() > 18) {
            btnW = 270; 
        }
        float btnH = 40;

        Quad quad = new Quad(btnW, btnH);
        Geometry geom = new Geometry("Dev_Btn_" + text, quad);
        Material mat = new Material(mainApp.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        ColorRGBA baseColor = new ColorRGBA(0.12f, 0.12f, 0.15f, 0.95f);
        mat.setColor("Color", baseColor.clone());
        mat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        geom.setMaterial(mat);

        BitmapText btnText = new BitmapText(font);
        btnText.setText(text);
        btnText.setSize(14);
        btnText.setColor(ColorRGBA.White);

        Node borderNode = new Node("Border");
        Material bMat = new Material(mainApp.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        bMat.setColor("Color", new ColorRGBA(0.25f, 0.55f, 0.40f, 1.0f)); 
        Geometry top = new Geometry("T", new Quad(btnW, 1.5f)); top.setMaterial(bMat); top.setLocalTranslation(0, btnH - 1.5f, 0.5f); borderNode.attachChild(top);
        Geometry bottom = new Geometry("B", new Quad(btnW, 1.5f)); bottom.setMaterial(bMat); bottom.setLocalTranslation(0, 0, 0.5f); borderNode.attachChild(bottom);
        Geometry left = new Geometry("L", new Quad(1.5f, btnH)); left.setMaterial(bMat); left.setLocalTranslation(0, 0, 0.5f); borderNode.attachChild(left);
        Geometry right = new Geometry("R", new Quad(1.5f, btnH)); right.setMaterial(bMat); right.setLocalTranslation(btnW - 1.5f, 0, 0.5f); borderNode.attachChild(right);

        Node btnNode = new Node("DevBtnNode_" + text);
        btnNode.attachChild(geom);
        btnNode.attachChild(btnText);
        btnNode.attachChild(borderNode);

        geom.setLocalTranslation(-btnW / 2, -btnH / 2, 0);
        borderNode.setLocalTranslation(-btnW / 2, -btnH / 2, 0.5f);
        btnText.setLocalTranslation(-btnText.getLineWidth() / 2, btnText.getLineHeight() / 2, 1);

        btnNode.setLocalTranslation(centerX, centerY, 1);
        devNode.attachChild(btnNode); // Исправлено: привязка к devNode вместо settingsNode

        MenuButton button = new MenuButton(btnNode, geom, btnText, btnW, btnH, baseColor, action);
        buttons.add(button);
        return button;
    }

    @Override
    public void update(float tpf) {
        float currentW = mainApp.getCamera().getWidth();
        float currentH = mainApp.getCamera().getHeight();

        if (currentW != lastScreenWidth || currentH != lastScreenHeight) {
            lastScreenWidth = currentW;
            lastScreenHeight = currentH;
            devNode.detachAllChildren();
            buttons.clear();
            createInterface(currentW, currentH);
        }

        Vector2f mousePos = mainApp.getInputManager().getCursorPosition();
        for (MenuButton btn : buttons) {
            boolean isHovered = btn.contains(mousePos.x, mousePos.y);
            ColorRGBA targetColor = isHovered ? btn.hoverColor : btn.baseColor;
            btn.currentColor.interpolateLocal(targetColor, tpf * 10f);
            btn.geom.getMaterial().setColor("Color", btn.currentColor);

            float targetScale = isHovered ? 1.03f : 1.0f;
            btn.currentScale = FastMath.interpolateLinear(tpf * 12.0f, btn.currentScale, targetScale);
            btn.btnNode.setLocalScale(btn.currentScale);
        }
    }

    @Override
    protected void onEnable() {
        mainApp.getGuiNode().attachChild(devNode);
        mainApp.getInputManager().setCursorVisible(true);
    }

    @Override
    protected void onDisable() {
        devNode.removeFromParent();
        mainApp.getInputManager().removeRawInputListener(this);
    }

    @Override
    public void onMouseButtonEvent(MouseButtonEvent evt) {
        if (evt.isReleased() && evt.getButtonIndex() == 0) {
            for (MenuButton btn : buttons) {
                if (btn.contains(evt.getX(), evt.getY())) {
                    btn.action.run();
                    break;
                }
            }
        }
    }

    @Override public void beginInput() {}
    @Override public void endInput() {}
    @Override public void onJoyAxisEvent(JoyAxisEvent evt) {}
    @Override public void onJoyButtonEvent(JoyButtonEvent evt) {}
    @Override public void onMouseMotionEvent(MouseMotionEvent evt) {}
    @Override public void onKeyEvent(KeyInputEvent evt) {}
    @Override public void onTouchEvent(TouchEvent evt) {}

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

        public MenuButton(Node btnNode, Geometry geom, BitmapText textNode, float w, float h, ColorRGBA baseColor, Runnable action) {
            this.btnNode = btnNode;
            this.geom = geom;
            this.textNode = textNode;
            this.w = w;
            this.h = h;
            this.baseColor = baseColor;
            this.hoverColor = new ColorRGBA(0.20f, 0.35f, 0.28f, 0.95f); 
            this.currentColor = baseColor.clone();
            this.action = action;
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
}