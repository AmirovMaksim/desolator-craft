package com.mygame;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

public class PauseMenuState extends BaseAppState implements RawInputListener {

    private SimpleApplication app;
    private Node menuNode;
    private BitmapFont font;

    private final List<MenuButton> buttons = new ArrayList<>();
    private final List<PauseParticle> particles = new ArrayList<>();
    private final Random random = new Random();
    private Geometry backgroundOverlay;

    private float lastScreenWidth;
    private float lastScreenHeight;

    private float introTimer = 0.0f;

    private final Runnable onResume;
    private final Runnable onQuit;

    public PauseMenuState(Runnable onResume, Runnable onQuit) {
        this.onResume = onResume;
        this.onQuit = onQuit;
    }

    @Override
    protected void initialize(Application app) {
        this.app = (SimpleApplication) app;
        this.menuNode = new Node("PauseMenuNode");
        this.font = app.getAssetManager().loadFont("Interface/Fonts/Default.fnt");

        this.lastScreenWidth = app.getCamera().getWidth();
        this.lastScreenHeight = app.getCamera().getHeight();

        Quad bgQuad = new Quad(1, 1);
        backgroundOverlay = new Geometry("PauseBg", bgQuad);
        Material bgMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        bgMat.setColor("Color", new ColorRGBA(0.05f, 0.02f, 0.02f, 0.72f)); 
        bgMat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        backgroundOverlay.setMaterial(bgMat);
        
        backgroundOverlay.setLocalScale(lastScreenWidth, lastScreenHeight, 1);
        menuNode.attachChild(backgroundOverlay);

        for (int i = 0; i < 40; i++) {
            createPauseParticle(lastScreenWidth, lastScreenHeight);
        }

        createButtons(lastScreenWidth, lastScreenHeight);
        app.getInputManager().addRawInputListener(this);
    }

    @Override
    protected void cleanup(Application app) {
        buttons.clear();
        particles.clear();
    }

    private void createPauseParticle(float width, float height) {
        float size = random.nextFloat() * 12 + 4;
        Quad quad = new Quad(size, size);
        Geometry geom = new Geometry("PausePixel", quad);
        
        Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        float alpha = random.nextFloat() * 0.22f + 0.08f;
        ColorRGBA color = random.nextBoolean() 
            ? new ColorRGBA(0.85f, 0.24f, 0.05f, alpha) 
            : new ColorRGBA(0.95f, 0.48f, 0.08f, alpha);

        mat.setColor("Color", color);
        mat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        geom.setMaterial(mat);

        float x = random.nextFloat() * width;
        float y = random.nextFloat() * height;
        geom.setLocalTranslation(x, y, 0.5f);
        menuNode.attachChild(geom);

        PauseParticle p = new PauseParticle();
        p.geom = geom;
        p.x = x;
        p.y = y;
        p.speed = random.nextFloat() * 45 + 15;
        p.size = size;
        particles.add(p);
    }

    private void createButtons(float screenWidth, float screenHeight) {
        float startY = screenHeight / 2 + 50;

        addButton("RESUME GAME", screenWidth / 2, startY, onResume);

        addButton("SETTINGS", screenWidth / 2, startY - 65, () -> {
            PauseMenuState pauseMenu = app.getStateManager().getState(PauseMenuState.class);
            app.getStateManager().detach(pauseMenu); 

            app.getStateManager().attach(new SettingsMenuState(() -> {
                app.getStateManager().detach(app.getStateManager().getState(SettingsMenuState.class)); 
                app.getStateManager().attach(pauseMenu); 
            }));
        });

        addButton("MAIN MENU", screenWidth / 2, startY - 130, onQuit);
    }

    private void addButton(String text, float centerX, float centerY, Runnable action) {
        float w = 320;
        float h = 55;
        
        Quad quad = new Quad(w, h);
        Geometry geom = new Geometry("PauseBtn_" + text, quad);
        Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        
        ColorRGBA baseColor = new ColorRGBA(0.2f, 0.2f, 0.24f, 0.6f);
        mat.setColor("Color", baseColor.clone());
        mat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        geom.setMaterial(mat);

        BitmapText btnText = new BitmapText(font);
        btnText.setText(text);
        btnText.setSize(22);
        btnText.setColor(ColorRGBA.White);

        menuNode.attachChild(geom);
        menuNode.attachChild(btnText);

        MenuButton button = new MenuButton(geom, btnText, w, h, baseColor, action);
        button.reposition(centerX, centerY);
        buttons.add(button);
    }

    private void adaptToResolution(float newWidth, float newHeight) {
        backgroundOverlay.setLocalScale(newWidth, newHeight, 1);
        float startY = newHeight / 2 + 50;
        
        if (buttons.size() >= 3) {
            buttons.get(0).reposition(newWidth / 2, startY);
            buttons.get(1).reposition(newWidth / 2, startY - 65);
            buttons.get(2).reposition(newWidth / 2, startY - 130);
        }
    }

    @Override
    public void update(float tpf) {
        float currentWidth = app.getCamera().getWidth();
        float currentHeight = app.getCamera().getHeight();

        if (currentWidth != lastScreenWidth || currentHeight != lastScreenHeight) {
            lastScreenWidth = currentWidth;
            lastScreenHeight = currentHeight;
            adaptToResolution(currentWidth, currentHeight);
        }

        if (introTimer < 0.20f) {
            introTimer += tpf;
            float rawScale = introTimer / 0.20f;
            if (rawScale > 1.0f) rawScale = 1.0f;
            float stepScale = ((int)(rawScale * 5.0f)) / 5.0f;
            if (stepScale < 0.2f) stepScale = 0.2f;
            menuNode.setLocalScale(stepScale);
        } else {
            menuNode.setLocalScale(1.0f);
        }

        for (PauseParticle p : particles) {
            p.y += p.speed * tpf;
            if (p.y > lastScreenHeight) {
                p.y = -p.size;
                p.x = random.nextFloat() * lastScreenWidth; 
            }
            p.geom.setLocalTranslation(p.x, p.y, 0.5f);
        }

        Vector2f mousePos = app.getInputManager().getCursorPosition();
        for (MenuButton btn : buttons) {
            boolean isHovered = btn.contains(mousePos.x, mousePos.y);
            ColorRGBA targetColor = isHovered ? btn.hoverColor : btn.baseColor;
            btn.currentColor.interpolateLocal(targetColor, tpf * 10f);
            btn.geom.getMaterial().setColor("Color", btn.currentColor);
        }
    }

    @Override
    protected void onEnable() {
        app.getGuiNode().attachChild(menuNode);
        app.getFlyByCamera().setEnabled(false);
        app.getInputManager().setCursorVisible(true);
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

    private static class PauseParticle {
        Geometry geom;
        float x, y;
        float speed;
        float size;
    }

    private static class MenuButton {
        Geometry geom;
        BitmapText text;
        float x, y, w, h;
        ColorRGBA baseColor;
        ColorRGBA hoverColor;
        ColorRGBA currentColor;
        Runnable action;

        public MenuButton(Geometry geom, BitmapText text, float w, float h, ColorRGBA baseColor, Runnable action) {
            this.geom = geom;
            this.text = text;
            this.w = w;
            this.h = h;
            this.baseColor = baseColor;
            this.hoverColor = new ColorRGBA(0.32f, 0.35f, 0.42f, 0.85f);
            this.currentColor = baseColor.clone();
            this.action = action;
        }

        public void reposition(float centerX, float centerY) {
            this.x = centerX - w / 2;
            this.y = centerY - h / 2;
            geom.setLocalTranslation(x, y, 1);

            float textX = centerX - text.getLineWidth() / 2;
            float textY = centerY + text.getLineHeight() / 2;
            text.setLocalTranslation(textX, textY, 2);
        }

        public boolean contains(float mx, float my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }
}