package com.mygame;

import java.util.ArrayList;
import java.util.List;

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
import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

public class SettingsMenuState extends BaseAppState implements RawInputListener {

    private SimpleApplication app;
    private Node settingsNode;
    private BitmapFont font;

    private final List<MenuButton> buttons = new ArrayList<>();
    private final Runnable onCloseCallback;

    private MenuButton distBtn;
    private MenuButton fovBtn;
    private MenuButton shadowBtn;
    private MenuButton ssaoBtn;
    private MenuButton bloomBtn;
    private MenuButton cloudsBtn;
    private MenuButton particlesBtn;

    private float lastScreenWidth;
    private float lastScreenHeight;

    public SettingsMenuState(Runnable onCloseCallback) {
        this.onCloseCallback = onCloseCallback;
    }

    @Override
    protected void initialize(Application app) {
        this.app = (SimpleApplication) app;
        this.settingsNode = new Node("SettingsNode");
        this.font = app.getAssetManager().loadFont("Interface/Fonts/Default.fnt");

        this.lastScreenWidth = app.getCamera().getWidth();
        this.lastScreenHeight = app.getCamera().getHeight();

        createButtons(lastScreenWidth, lastScreenHeight);
        app.getInputManager().addRawInputListener(this);
    }

    @Override
    protected void cleanup(Application app) {
        buttons.clear();
    }

    private void createButtons(float w, float h) {
        float startY = h / 2 + 160;
        float gapY = 44;

        // Заголовок меню настроек
        BitmapText title = new BitmapText(font);
        title.setText("SETTINGS MENU");
        title.setSize(26);
        title.setColor(new ColorRGBA(0.95f, 0.78f, 0.15f, 1.0f));
        title.setLocalTranslation(w / 2 - title.getLineWidth() / 2, startY + 45, 2);
        settingsNode.attachChild(title);

        distBtn = addButton("RENDER DISTANCE: " + GameSettings.renderDistance, w / 2, startY, () -> {
            if (GameSettings.renderDistance == 4) GameSettings.renderDistance = 6;
            else if (GameSettings.renderDistance == 6) GameSettings.renderDistance = 8;
            else if (GameSettings.renderDistance == 8) GameSettings.renderDistance = 12;
            else if (GameSettings.renderDistance == 12) GameSettings.renderDistance = 16;
            else GameSettings.renderDistance = 4;
            distBtn.setText("RENDER DISTANCE: " + GameSettings.renderDistance + " CHUNKS");
        });

        fovBtn = addButton("FOV: " + (int)GameSettings.fov, w / 2, startY - gapY, () -> {
            if (GameSettings.fov == 60f) GameSettings.fov = 70f;
            else if (GameSettings.fov == 70f) GameSettings.fov = 80f;
            else if (GameSettings.fov == 80f) GameSettings.fov = 90f;
            else GameSettings.fov = 60f;
            fovBtn.setText("FOV: " + (int)GameSettings.fov + " DEG");
        });

        shadowBtn = addButton("SHADOWS: " + (GameSettings.shadowsEnabled ? "ON" : "OFF"), w / 2, startY - 2 * gapY, () -> {
            GameSettings.shadowsEnabled = !GameSettings.shadowsEnabled;
            shadowBtn.setText("SHADOWS: " + (GameSettings.shadowsEnabled ? "ON" : "OFF"));
        });

        ssaoBtn = addButton("SSAO (AMBIENT OCCLUSION): " + (GameSettings.ssaoEnabled ? "ON" : "OFF"), w / 2, startY - 3 * gapY, () -> {
            GameSettings.ssaoEnabled = !GameSettings.ssaoEnabled;
            ssaoBtn.setText("SSAO (AMBIENT OCCLUSION): " + (GameSettings.ssaoEnabled ? "ON" : "OFF"));
        });

        bloomBtn = addButton("GLOW EFFECTS (BLOOM): " + (GameSettings.bloomEnabled ? "ON" : "OFF"), w / 2, startY - 4 * gapY, () -> {
            GameSettings.bloomEnabled = !GameSettings.bloomEnabled;
            bloomBtn.setText("GLOW EFFECTS (BLOOM): " + (GameSettings.bloomEnabled ? "ON" : "OFF"));
        });

        cloudsBtn = addButton("HIGH-POLY CLOUDS: " + (GameSettings.cloudsEnabled ? "ON" : "OFF"), w / 2, startY - 5 * gapY, () -> {
            GameSettings.cloudsEnabled = !GameSettings.cloudsEnabled;
            cloudsBtn.setText("HIGH-POLY CLOUDS: " + (GameSettings.cloudsEnabled ? "ON" : "OFF"));
        });

        particlesBtn = addButton("BLOCK PARTICLES: " + (GameSettings.particlesEnabled ? "ON" : "OFF"), w / 2, startY - 6 * gapY, () -> {
            GameSettings.particlesEnabled = !GameSettings.particlesEnabled;
            particlesBtn.setText("BLOCK PARTICLES: " + (GameSettings.particlesEnabled ? "ON" : "OFF"));
        });

        addButton("SAVE & BACK", w / 2, startY - 7.5f * gapY, onCloseCallback);
    }

    private MenuButton addButton(String text, float centerX, float centerY, Runnable action) {
        float btnW = 400;
        float btnH = 36;
        
        Quad quad = new Quad(btnW, btnH);
        Geometry geom = new Geometry("S_Btn_" + text, quad);
        Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        ColorRGBA baseColor = new ColorRGBA(0.14f, 0.14f, 0.16f, 0.90f);
        mat.setColor("Color", baseColor.clone());
        mat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        geom.setMaterial(mat);

        BitmapText btnText = new BitmapText(font);
        btnText.setText(text);
        btnText.setSize(15);
        btnText.setColor(ColorRGBA.White);

        Node borderNode = new Node("Border");
        Material bMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        bMat.setColor("Color", new ColorRGBA(0.25f, 0.25f, 0.28f, 1.0f));
        Geometry top = new Geometry("T", new Quad(btnW, 2f)); top.setMaterial(bMat); top.setLocalTranslation(0, btnH - 2f, 0.5f); borderNode.attachChild(top);
        Geometry bottom = new Geometry("B", new Quad(btnW, 2f)); bottom.setMaterial(bMat); bottom.setLocalTranslation(0, 0, 0.5f); borderNode.attachChild(bottom);
        Geometry left = new Geometry("L", new Quad(2f, btnH)); left.setMaterial(bMat); left.setLocalTranslation(0, 0, 0.5f); borderNode.attachChild(left);
        Geometry right = new Geometry("R", new Quad(2f, btnH)); right.setMaterial(bMat); right.setLocalTranslation(btnW - 2f, 0, 0.5f); borderNode.attachChild(right);

        Node btnNode = new Node("BtnNode_" + text);
        btnNode.attachChild(geom);
        btnNode.attachChild(btnText);
        btnNode.attachChild(borderNode);

        geom.setLocalTranslation(-btnW / 2, -btnH / 2, 0);
        borderNode.setLocalTranslation(-btnW / 2, -btnH / 2, 0.5f);
        btnText.setLocalTranslation(-btnText.getLineWidth() / 2, btnText.getLineHeight() / 2, 1);

        btnNode.setLocalTranslation(centerX, centerY, 1);
        settingsNode.attachChild(btnNode);

        MenuButton button = new MenuButton(btnNode, geom, btnText, btnW, btnH, baseColor, action);
        buttons.add(button);
        return button;
    }

    @Override
    public void update(float tpf) {
        float currentW = app.getCamera().getWidth();
        float currentH = app.getCamera().getHeight();

        if (currentW != lastScreenWidth || currentH != lastScreenHeight) {
            lastScreenWidth = currentW;
            lastScreenHeight = currentH;
            settingsNode.detachAllChildren();
            buttons.clear();
            createButtons(currentW, currentH);
        }

        Vector2f mousePos = app.getInputManager().getCursorPosition();
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
        app.getGuiNode().attachChild(settingsNode);
        app.getInputManager().setCursorVisible(true);
    }

    @Override
    protected void onDisable() {
        settingsNode.removeFromParent();
        app.getInputManager().removeRawInputListener(this);
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

        public MenuButton(Node btnNode, Geometry geom, BitmapText textNode, float w, float h, ColorRGBA baseColor, Runnable action) {
            this.btnNode = btnNode;
            this.geom = geom;
            this.textNode = textNode;
            this.w = w;
            this.h = h;
            this.baseColor = baseColor;
            this.hoverColor = new ColorRGBA(0.30f, 0.32f, 0.38f, 0.95f);
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