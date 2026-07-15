package com.mygame;

import java.util.ArrayList;
import java.util.List;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.InputManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

public class UserInterfaceManager {

    private AssetManager assetManager; 
    private BitmapText crosshair;
    private BitmapText debugText; 
    private Node hudNode;
    private BitmapFont guiFont;
    private Geometry hotbarBg;
    private Node hotbarBorderOuter;
    private Node hotbarBorderInner;
    private final Geometry[] hotbarSlots = new Geometry[9];
    private final Node[] hotbarSlotBorders = new Node[9];
    private final Geometry[] hotbarIcons = new Geometry[9]; 
    private final BitmapText[] hotbarTexts = new BitmapText[9];
    private Node hotbarSelectorNode;

    private Geometry healthBarBg;
    private Geometry healthBarFill;
    private Geometry manaBarBg;
    private Geometry manaBarFill;
    private BitmapText classLabel;
    private Geometry hungerBarBg;
    private Geometry hungerBarFill;

    private Geometry liquidOverlay;
    private Geometry oxygenBarBg;
    private Geometry oxygenBarFill;
    private Geometry saturationBarBg;
    private Geometry saturationBarFill;
    private BitmapText weatherLabel;

    private Node deathScreenNode;

    private BitmapText blockNameText;
    private float blockNameTimer = 0.0f;
    private int prevSelectedSlot = -1;

    private Node inventoryNode;
    private Node invHoverSelectorNode;
    private Material hoverSelectorMat;
    private Material invBgMat;
    private float currentHoverX = 0f;
    private float currentHoverY = 0f;
    private float currentHoverAlpha = 0f;
    private float currentSelectorX = 0f;

    private float screenW;
    private float screenH;

    private final List<SurvivalSlotVisual> survivalUIGrid = new ArrayList<>();
    private final List<SurvivalSlotVisual> chestUIGrid = new ArrayList<>(); 
    private int selectedSlotToMove = -1;
    private boolean isMovingFromChest = false;
    // DRAG&DROP: слот, который тащим за курсором (или -1, если ничего не тащим)
    private int dragSlot = -1;
    private boolean dragFromChest = false;
    private byte dragType = 0;
    private int dragCount = 0;
    private Geometry dragIcon = null;
    private BitmapText dragText = null;

    public boolean isChestOpen = false;
    public String currentChestCoords = "";

    private static final byte[] creativePalette = {
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30
    };

    private static class ConsoleLine {
        String text;
        ColorRGBA color;
        ConsoleLine(String text, ColorRGBA color) {
            this.text = text;
            this.color = color;
        }
    }

    private Node consoleNode;
    private Geometry consoleBg;
    private BitmapText consoleInputText;
    private final List<ConsoleLine> consoleLines = new ArrayList<>();
    private final BitmapText[] consoleLogLines = new BitmapText[6]; 

    private static class SurvivalSlotVisual {
        int slotIndex;
        float x, y, size;
        boolean isChest;
        public SurvivalSlotVisual(int idx, float x, float y, float size, boolean chest) {
            this.slotIndex = idx;
            this.x = x;
            this.y = y;
            this.size = size;
            this.isChest = chest;
        }
        public boolean contains(float mx, float my) {
            return mx >= x && mx <= x + size && my >= y && my <= y + size;
        }
    }

    public void init(AssetManager assetManager, Node guiNode, BitmapFont guiFont, float screenW, float screenH) {
        this.guiFont = guiFont;
        this.assetManager = assetManager; 
        this.screenW = screenW;
        this.screenH = screenH;
        createCrosshair(guiFont, screenW, screenH, guiNode);
        createDebugMenu(guiFont, guiNode);
        createHUD(assetManager, guiNode, screenW, screenH);
        createSurvivalInventoryUI(assetManager, guiFont, guiNode);
        createConsoleUI(assetManager, guiFont, guiNode);
        createDeathScreen(assetManager, guiFont, guiNode);
    }

    private void createCrosshair(BitmapFont guiFont, float w, float h, Node guiNode) {
        crosshair = new BitmapText(guiFont);
        crosshair.setText("+");
        crosshair.setSize(30);
        crosshair.setColor(ColorRGBA.White);
        repositionCrosshair(w, h);
        guiNode.attachChild(crosshair);
    }

    private void createDebugMenu(BitmapFont guiFont, Node guiNode) {
        debugText = new BitmapText(guiFont);
        debugText.setSize(18);
        debugText.setColor(ColorRGBA.Green);
        guiNode.attachChild(debugText);
    }

    public void repositionCrosshair(float w, float h) {
        if (crosshair != null) {
            crosshair.setLocalTranslation(
                w / 2 - crosshair.getLineWidth() / 2,
                h / 2 + crosshair.getLineHeight() / 2,
                0
            );
        }
    }

    private void createHUD(AssetManager assetManager, Node guiNode, float screenW, float screenH) {
        hudNode = new Node("HUDNode");
        float barW = 360;
        float barH = 44;

        Quad bgQuad = new Quad(barW, barH);
        hotbarBg = new Geometry("HotbarBg", bgQuad);
        Material bgMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        bgMat.setColor("Color", new ColorRGBA(0.08f, 0.08f, 0.1f, 0.72f));
        bgMat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        hotbarBg.setMaterial(bgMat);
        hudNode.attachChild(hotbarBg);

        hotbarBorderOuter = createBorder(assetManager, barW, barH, 3f, new ColorRGBA(0.04f, 0.04f, 0.05f, 0.9f), 1f);
        hudNode.attachChild(hotbarBorderOuter);

        hotbarBorderInner = createBorder(assetManager, barW - 6, barH - 6, 2f, new ColorRGBA(0.25f, 0.25f, 0.3f, 0.65f), 1.5f);
        hudNode.attachChild(hotbarBorderInner);

        float slotSize = 36;
        Quad slotQuad = new Quad(slotSize, slotSize);
        Material slotMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        slotMat.setColor("Color", new ColorRGBA(0.18f, 0.18f, 0.22f, 0.5f));
        slotMat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);

        BitmapFont font = debugText.getFont();

        for (int i = 0; i < 9; i++) {
            Geometry slot = new Geometry("Slot_" + i, slotQuad);
            slot.setMaterial(slotMat);
            hudNode.attachChild(slot);
            hotbarSlots[i] = slot;

            Node sBorder = createBorder(assetManager, slotSize, slotSize, 1.5f, new ColorRGBA(0.06f, 0.06f, 0.08f, 0.8f), 1.2f);
            hudNode.attachChild(sBorder);
            hotbarSlotBorders[i] = sBorder;

            Geometry icon = createIconGeometry(assetManager, Main.hotbarBlocks[i], 26);
            hudNode.attachChild(icon);
            hotbarIcons[i] = icon;

            BitmapText numTxt = new BitmapText(font);
            numTxt.setSize(12);
            numTxt.setColor(ColorRGBA.White);
            numTxt.setText("");
            hudNode.attachChild(numTxt);
            hotbarTexts[i] = numTxt;
        }

        hotbarSelectorNode = new Node("SelectorNode");
        Node selectorBorder = createBorder(assetManager, slotSize + 4, slotSize + 4, 3f, ColorRGBA.White, 3);
        hotbarSelectorNode.attachChild(selectorBorder);
        hudNode.attachChild(hotbarSelectorNode);

        healthBarBg = new Geometry("HealthBarBg", new Quad(130, 8));
        Material hBgMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        hBgMat.setColor("Color", new ColorRGBA(0.12f, 0.0f, 0.0f, 0.7f));
        healthBarBg.setMaterial(hBgMat);
        hudNode.attachChild(healthBarBg);

        healthBarFill = new Geometry("HealthBarFill", new Quad(1, 8));
        Material hFillMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        hFillMat.setColor("Color", new ColorRGBA(0.85f, 0.12f, 0.12f, 1.0f)); 
        healthBarFill.setMaterial(hFillMat);
        hudNode.attachChild(healthBarFill);

        manaBarBg = new Geometry("ManaBarBg", new Quad(130, 8));
        Material mBgMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mBgMat.setColor("Color", new ColorRGBA(0.0f, 0.05f, 0.18f, 0.7f));
        manaBarBg.setMaterial(mBgMat);
        hudNode.attachChild(manaBarBg);

        manaBarFill = new Geometry("ManaBarFill", new Quad(1, 8));
        Material mFillMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mFillMat.setColor("Color", new ColorRGBA(0.20f, 0.45f, 1.0f, 1.0f));
        manaBarFill.setMaterial(mFillMat);
        hudNode.attachChild(manaBarFill);

        classLabel = new BitmapText(guiFont);
        classLabel.setSize(16);
        classLabel.setColor(new ColorRGBA(0.9f, 0.85f, 0.5f, 1.0f));
        classLabel.setText("CLASS: WARRIOR");
        hudNode.attachChild(classLabel);

        hungerBarBg = new Geometry("HungerBarBg", new Quad(130, 8));
        Material huBgMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        huBgMat.setColor("Color", new ColorRGBA(0.12f, 0.06f, 0.0f, 0.7f));
        hungerBarBg.setMaterial(huBgMat);
        hudNode.attachChild(hungerBarBg);

        hungerBarFill = new Geometry("HungerBarFill", new Quad(1, 8));
        Material huFillMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        huFillMat.setColor("Color", new ColorRGBA(0.88f, 0.48f, 0.10f, 1.0f)); 
        hungerBarFill.setMaterial(huFillMat);
        hudNode.attachChild(hungerBarFill);

        oxygenBarBg = new Geometry("OxygenBarBg", new Quad(130, 8));
        Material oxBgMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        oxBgMat.setColor("Color", new ColorRGBA(0.0f, 0.12f, 0.12f, 0.7f));
        oxygenBarBg.setMaterial(oxBgMat);
        hudNode.attachChild(oxygenBarBg);

        oxygenBarFill = new Geometry("OxygenBarFill", new Quad(1, 8));
        Material oxFillMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        oxFillMat.setColor("Color", new ColorRGBA(0.08f, 0.85f, 0.85f, 1.0f));
        oxygenBarFill.setMaterial(oxFillMat);
        hudNode.attachChild(oxygenBarFill);

        // БАР НАСЫЩЕНИЯ (saturation) — зелёный, под голодом
        saturationBarBg = new Geometry("SaturationBarBg", new Quad(130, 8));
        Material satBgMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        satBgMat.setColor("Color", new ColorRGBA(0.0f, 0.10f, 0.0f, 0.7f));
        saturationBarBg.setMaterial(satBgMat);
        hudNode.attachChild(saturationBarBg);

        saturationBarFill = new Geometry("SaturationBarFill", new Quad(1, 8));
        Material satFillMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        satFillMat.setColor("Color", new ColorRGBA(0.18f, 0.78f, 0.20f, 1.0f));
        saturationBarFill.setMaterial(satFillMat);
        hudNode.attachChild(saturationBarFill);

        // ИНДИКАТОР ПОГОДЫ (нижний левый угол)
        weatherLabel = new BitmapText(debugText.getFont());
        weatherLabel.setSize(16);
        weatherLabel.setColor(new ColorRGBA(0.8f, 0.85f, 0.95f, 0.9f));
        weatherLabel.setText("WEATHER: CLEAR");
        hudNode.attachChild(weatherLabel);

        liquidOverlay = new Geometry("LiquidOverlay", new Quad(1, 1));
        Material overlayMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        overlayMat.setColor("Color", new ColorRGBA(0, 0, 0, 0));
        overlayMat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        liquidOverlay.setMaterial(overlayMat);
        hudNode.attachChild(liquidOverlay);

        biomeTextNode(guiNode);
        repositionHUD(screenW, screenH, 0);
        
        float gap = 4;
        currentSelectorX = (screenW / 2 - barW / 2) + gap - 2;

        guiNode.attachChild(hudNode);
    }

    private void biomeTextNode(Node guiNode) {
        blockNameText = new BitmapText(debugText.getFont());
        blockNameText.setSize(20);
        blockNameText.setColor(ColorRGBA.White);
        blockNameText.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
        guiNode.attachChild(blockNameText);
    }

    public void repositionHUD(float w, float h, int selectedSlot) {
        this.screenW = w;
        this.screenH = h;
        if (hudNode == null) return;

        float barW = 360;
        float barH = 44;
        float startX = w / 2 - barW / 2;
        float startY = 20;

        hotbarBg.setLocalTranslation(startX, startY, 0);
        hotbarBorderOuter.setLocalTranslation(startX, startY, 1);
        hotbarBorderInner.setLocalTranslation(startX + 3, startY + 3, 1.5f);

        float slotSize = 36;
        float gap = 4;
        for (int i = 0; i < 9; i++) {
            float sx = startX + gap + i * (slotSize + gap);
            float sy = startY + (barH - slotSize) / 2;
            hotbarSlots[i].setLocalTranslation(sx, sy, 1);
            hotbarSlotBorders[i].setLocalTranslation(sx, sy, 1.2f);
            hotbarIcons[i].setLocalTranslation(sx + 5, sy + 5, 1.5f);
            hotbarTexts[i].setLocalTranslation(sx + 18, sy + 14, 2.0f);
        }

        currentSelectorX = startX + gap + selectedSlot * (slotSize + gap) - 2;
        float selY = startY + (barH - slotSize) / 2 - 2;
        hotbarSelectorNode.setLocalTranslation(currentSelectorX, selY, 2);

        healthBarBg.setLocalTranslation(startX + 5, startY + barH + 6, 1.5f);
        healthBarFill.setLocalTranslation(startX + 5, startY + barH + 6, 1.6f);

        hungerBarBg.setLocalTranslation(startX + barW - 135, startY + barH + 6, 1.5f);
        hungerBarFill.setLocalTranslation(startX + barW - 135, startY + barH + 6, 1.6f);

        oxygenBarBg.setLocalTranslation(startX + 5, startY + barH + 18, 1.5f);
        oxygenBarFill.setLocalTranslation(startX + 5, startY + barH + 18, 1.6f);

        saturationBarBg.setLocalTranslation(startX + barW - 135, startY + barH + 18, 1.5f);
        saturationBarFill.setLocalTranslation(startX + barW - 135, startY + barH + 18, 1.6f);

        manaBarBg.setLocalTranslation(startX + 5, startY + barH + 30, 1.5f);
        manaBarFill.setLocalTranslation(startX + 5, startY + barH + 30, 1.6f);
        if (classLabel != null) classLabel.setLocalTranslation(startX + 5, startY + barH + 44, 2);

        if (weatherLabel != null) {
            weatherLabel.setLocalTranslation(20, 40, 2);
        }

        liquidOverlay.setLocalTranslation(0, 0, -0.5f); 
        liquidOverlay.setLocalScale(w, h, 1.0f);

        if (deathScreenNode != null) {
            Geometry bg = (Geometry) deathScreenNode.getChild("DeathBg");
            bg.setLocalScale(w, h, 1);
            
            BitmapText dt = (BitmapText) deathScreenNode.getChild("DeathText");
            dt.setLocalTranslation(w / 2 - dt.getLineWidth() / 2, h / 2 + 60, 2);

            BitmapText rh = (BitmapText) deathScreenNode.getChild("RespawnHint");
            rh.setLocalTranslation(w / 2 - rh.getLineWidth() / 2, h / 2 - 20, 2);
        }
    }

    public void updateHotbarIcon(AssetManager assetManager, int slotIdx, int blockType) {
        if (hotbarIcons[slotIdx] != null) {
            hudNode.detachChild(hotbarIcons[slotIdx]);
            Geometry newIcon = createIconGeometry(assetManager, blockType, 26);
            
            float startX = screenW / 2 - 360f / 2;
            float sx = startX + 4f + slotIdx * (36f + 4f);
            newIcon.setLocalTranslation(sx + 5, 20 + 4, 1.5f);
            
            hudNode.attachChild(newIcon);
            hotbarIcons[slotIdx] = newIcon;
        }
    }

    private void createSurvivalInventoryUI(AssetManager assetManager, BitmapFont font, Node guiNode) {
        inventoryNode = new Node("SurvivalInventoryNode");
        survivalUIGrid.clear();
        chestUIGrid.clear();

        float panW = 500;
        float panH = 460; 

        Quad bgQuad = new Quad(panW, panH);
        Geometry bg = new Geometry("InvBg", bgQuad);
        Material bgMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        bgMat.setColor("Color", new ColorRGBA(0.10f, 0.12f, 0.18f, 0.92f));
        bgMat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        bg.setMaterial(bgMat);
        bg.setLocalTranslation(-panW / 2, -panH / 2, 0);
        inventoryNode.attachChild(bg);
        invBgMat = bgMat;

        Node border = createBorder(assetManager, panW, panH, 3f, new ColorRGBA(0.35f, 0.55f, 0.85f, 0.9f), 1f);
        border.setLocalTranslation(-panW / 2, -panH / 2, 0);
        inventoryNode.attachChild(border);

        BitmapText title = new BitmapText(font);
        title.setName("InvTitle");
        title.setText("SURVIVAL INVENTORY");
        title.setSize(18);
        title.setColor(ColorRGBA.Yellow);
        title.setLocalTranslation(-140, panH/2 - 20, 2);
        inventoryNode.attachChild(title);

        float slotSize = 36;
        float gap = 8;
        float startX = -panW / 2 + 35;
        
        float startY = 30;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = 9 + row * 9 + col;
                float sx = startX + col * (slotSize + gap);
                float sy = startY - row * (slotSize + gap);
                createVisualSlotElement(assetManager, index, sx, sy, slotSize, false);
            }
        }

        float hotbarY = -150;
        for (int i = 0; i < 9; i++) {
            float sx = startX + i * (slotSize + gap);
            createVisualSlotElement(assetManager, i, sx, hotbarY, slotSize, false);
        }

        float chestStartY = 190;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = row * 9 + col;
                float sx = startX + col * (slotSize + gap);
                float sy = chestStartY - row * (slotSize + gap);
                createVisualSlotElement(assetManager, index, sx, sy, slotSize, true);
            }
        }

        // ЗАГОЛОВКИ СЕКЦИЙ (стиль Minecraft)
        addInvLabel(font, "CRAFTING", startX - 4, startY + slotSize + 6);
        addInvLabel(font, "INVENTORY", startX - 4, startY - 3 * (slotSize + gap) - 14);
        addInvLabel(font, "HOTBAR", startX - 4, hotbarY + slotSize + 6);
        addInvLabel(font, "CHEST", startX - 4, chestStartY + slotSize + 6);

        invHoverSelectorNode = new Node("InvHoverSelectorNode");
        Node selBorder = createBorder(assetManager, slotSize + 2, slotSize + 2, 2.5f, new ColorRGBA(1.0f, 1.0f, 1.0f, 0.0f), 3.5f);
        hoverSelectorMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        hoverSelectorMat.setColor("Color", new ColorRGBA(1.0f, 1.0f, 1.0f, 0.0f));
        hoverSelectorMat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        for (int i = 0; i < selBorder.getQuantity(); i++) {
            selBorder.getChild(i).setMaterial(hoverSelectorMat);
        }
        invHoverSelectorNode.attachChild(selBorder);
        inventoryNode.attachChild(invHoverSelectorNode);

        inventoryNode.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
        guiNode.attachChild(inventoryNode);
    }

    private void createVisualSlotElement(AssetManager assetManager, int idx, float x, float y, float size, boolean isChest) {
        // Внешняя рамка (светлая) — стиль Minecraft
        Node outerBorder = createBorder(assetManager, size + 2, size + 2, 1.5f, new ColorRGBA(0.55f, 0.58f, 0.62f, 0.9f), 1.5f);
        outerBorder.setLocalTranslation(x - 1, y - 1, 0.5f);
        inventoryNode.attachChild(outerBorder);

        // Внутренний фон слота (тёмный, с лёгкой тенью по краям)
        Quad q = new Quad(size, size);
        Geometry g = new Geometry((isChest ? "ChestSlot_" : "VisualSlot_") + idx, q);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", new ColorRGBA(0.13f, 0.14f, 0.18f, 0.85f));
        g.setMaterial(mat);
        g.setLocalTranslation(x, y, 1.0f);
        inventoryNode.attachChild(g);

        // Внутренняя тёмная окантовка (объём)
        Node innerBorder = createBorder(assetManager, size, size, 1.0f, new ColorRGBA(0.04f, 0.04f, 0.06f, 0.7f), 1.0f);
        innerBorder.setLocalTranslation(x, y, 1.4f);
        inventoryNode.attachChild(innerBorder);

        if (isChest) {
            chestUIGrid.add(new SurvivalSlotVisual(idx, x, y, size, true));
        } else {
            survivalUIGrid.add(new SurvivalSlotVisual(idx, x, y, size, false));
        }
    }

    private void addInvLabel(BitmapFont font, String text, float x, float y) {
        BitmapText lbl = new BitmapText(font);
        lbl.setText(text);
        lbl.setSize(13);
        lbl.setColor(new ColorRGBA(0.85f, 0.88f, 0.95f, 0.9f));
        lbl.setLocalTranslation(x, y, 2);
        inventoryNode.attachChild(lbl);
    }

    // DRAG&DROP: захват предмета из слота при ЛКМ-down
    public void startInventoryDrag(AssetManager assetManager, float mouseX, float mouseY, Player player, World world, int selectedSlot) {
        if (inventoryNode == null || inventoryNode.getCullHint() == com.jme3.scene.Spatial.CullHint.Always) return;
        if (dragSlot != -1) return; // уже тащим

        Vector3f invPos = inventoryNode.getLocalTranslation();
        float mx = mouseX - invPos.x;
        float my = mouseY - invPos.y;

        // Креатив: клик по палитре = мгновенно в хотбар (как раньше)
        if (player.isGodMode) {
            for (SurvivalSlotVisual slot : survivalUIGrid) {
                if (slot.contains(mx, my)) {
                    if (slot.slotIndex >= 9 && slot.slotIndex < 9 + creativePalette.length) {
                        byte type = creativePalette[slot.slotIndex - 9];
                        int activeHotbar = selectedSlot;
                        player.inventory[activeHotbar].blockType = type;
                        player.inventory[activeHotbar].count = 64;
                    }
                    return;
                }
            }
            return;
        }

        // Ищем слот-источник
        SurvivalSlotVisual src = findSlotAt(mx, my, world);
        if (src == null) return;

        Player.InventorySlot[] fromArr = (src.isChest) ? world.chestInventories.get(currentChestCoords) : player.inventory;
        if (fromArr == null || src.slotIndex >= fromArr.length) return;
        if (fromArr[src.slotIndex].blockType == 0 || fromArr[src.slotIndex].count <= 0) return;

        dragSlot = src.slotIndex;
        dragFromChest = src.isChest;
        dragType = fromArr[src.slotIndex].blockType;
        dragCount = fromArr[src.slotIndex].count;
        // убираем из исходного слота (предмет "в руке")
        fromArr[src.slotIndex].blockType = 0;
        fromArr[src.slotIndex].count = 0;

        // Создаём иконку, следующую за курсором
        createDragIcon(assetManager, dragType, dragCount, mouseX, mouseY);
    }

    // DRAG&DROP: бросок предмета при ЛКМ-up
    public void endInventoryDrag(AssetManager assetManager, float mouseX, float mouseY, Player player, World world, int selectedSlot) {
        if (dragSlot == -1) return;

        Vector3f invPos = inventoryNode.getLocalTranslation();
        float mx = mouseX - invPos.x;
        float my = mouseY - invPos.y;

        SurvivalSlotVisual dst = findSlotAt(mx, my, world);
        if (dst != null) {
            Player.InventorySlot[] toArr = dst.isChest ? world.chestInventories.get(currentChestCoords) : player.inventory;
            if (toArr != null && dst.slotIndex < toArr.length) {
                Player.InventorySlot to = toArr[dst.slotIndex];
                // MERGE при одинаковом типе, иначе SWAP (целевой <-> перетаскиваемый)
                if (to.blockType != 0 && to.blockType == dragType) {
                    int space = 64 - to.count;
                    int moved = Math.min(space, dragCount);
                    to.count += moved;
                    dragCount -= moved;
                    if (dragCount > 0) {
                        // не влезло — возвращаем остаток в исходный слот
                        Player.InventorySlot[] backArr = dragFromChest ? world.chestInventories.get(currentChestCoords) : player.inventory;
                        if (backArr != null && dragSlot < backArr.length) {
                            backArr[dragSlot].blockType = dragType;
                            backArr[dragSlot].count = dragCount;
                        }
                    }
                } else if (to.blockType == 0) {
                    // пустой слот — просто кладём
                    to.blockType = dragType;
                    to.count = dragCount;
                } else {
                    // занят другим типом — swap: целевой улетает в исходный, перетаскиваемый в целевой
                    byte tb = to.blockType; int tc = to.count;
                    to.blockType = dragType; to.count = dragCount;
                    Player.InventorySlot[] backArr = dragFromChest ? world.chestInventories.get(currentChestCoords) : player.inventory;
                    if (backArr != null && dragSlot < backArr.length) {
                        backArr[dragSlot].blockType = tb;
                        backArr[dragSlot].count = tc;
                    }
                }
            }
        } else {
            // бросок в пустоту — возвращаем в исходный слот
            Player.InventorySlot[] backArr = dragFromChest ? world.chestInventories.get(currentChestCoords) : player.inventory;
            if (backArr != null && dragSlot < backArr.length) {
                backArr[dragSlot].blockType = dragType;
                backArr[dragSlot].count = dragCount;
            }
        }

        destroyDragIcon();
        dragSlot = -1;
        dragFromChest = false;
        dragType = 0;
        dragCount = 0;
    }

    private SurvivalSlotVisual findSlotAt(float mx, float my, World world) {
        if (isChestOpen) {
            for (SurvivalSlotVisual slot : chestUIGrid) {
                if (slot.contains(mx, my)) return slot;
            }
        }
        for (SurvivalSlotVisual slot : survivalUIGrid) {
            if (slot.contains(mx, my)) return slot;
        }
        return null;
    }

    private void createDragIcon(AssetManager assetManager, byte type, int count, float mouseX, float mouseY) {
        destroyDragIcon();
        Node gui = inventoryNode.getParent();
        if (gui == null) gui = inventoryNode;

        dragIcon = createIconGeometry(assetManager, type, 28);
        dragIcon.setName("DragIcon");
        dragIcon.setLocalTranslation(mouseX, mouseY, 5);
        gui.attachChild(dragIcon);

        if (count > 1) {
            dragText = new BitmapText(assetManager.loadFont("Interface/Fonts/Default.fnt"));
            dragText.setName("DragText");
            dragText.setSize(13);
            dragText.setColor(ColorRGBA.White);
            dragText.setText(String.valueOf(count));
            dragText.setLocalTranslation(mouseX + 18, mouseY + 12, 6);
            gui.attachChild(dragText);
        }
    }

    // Отмена перетаскивания при закрытии инвентаря (возврат в исходный слот)
    public void cancelInventoryDrag(Player player, World world) {
        if (dragSlot == -1) return;
        Player.InventorySlot[] backArr = dragFromChest ? world.chestInventories.get(currentChestCoords) : player.inventory;
        if (backArr != null && dragSlot < backArr.length) {
            backArr[dragSlot].blockType = dragType;
            backArr[dragSlot].count = dragCount;
        }
        destroyDragIcon();
        dragSlot = -1;
        dragFromChest = false;
        dragType = 0;
        dragCount = 0;
    }

    private void destroyDragIcon() {
        if (dragIcon != null) { dragIcon.removeFromParent(); dragIcon = null; }
        if (dragText != null) { dragText.removeFromParent(); dragText = null; }
    }

    private void swapSlots(Player.InventorySlot from, Player.InventorySlot to) {
        byte tType = from.blockType;
        int tCount = from.count;
        from.blockType = to.blockType;
        from.count = to.count;
        to.blockType = tType;
        to.count = tCount;
    }

    private Player.InventorySlot[] createEmptyChest() {
        Player.InventorySlot[] slots = new Player.InventorySlot[27];
        for (int i = 0; i < 27; i++) {
            slots[i] = new Player.InventorySlot();
        }
        return slots;
    }

    // В метод drawSurvivalSlotContent передается параметр world
    private void drawSurvivalSlotContent(AssetManager assetManager, BitmapFont font, Player player, World world) {
        for (int i = inventoryNode.getQuantity() - 1; i >= 0; i--) {
            String name = inventoryNode.getChild(i).getName();
            if (name != null && (name.startsWith("DynIcon_") || name.startsWith("DynText_"))) {
                inventoryNode.detachChildAt(i);
            }
        }

        if (player.isGodMode) {
            BitmapText title = (BitmapText) inventoryNode.getChild("InvTitle");
            if (title != null) title.setText("CREATIVE INVENTORY (CLICK TO GRAB)");
            setChestGridVisible(false);

            for (SurvivalSlotVisual slot : survivalUIGrid) {
                if (slot.slotIndex >= 9 && slot.slotIndex < 9 + creativePalette.length) {
                    byte type = creativePalette[slot.slotIndex - 9];
                    Geometry icon = createIconGeometry(assetManager, type, 26);
                    icon.setName("DynIcon_Pal_" + slot.slotIndex);
                    icon.setLocalTranslation(slot.x + 5, slot.y + 5, 2.2f);
                    inventoryNode.attachChild(icon);
                } else if (slot.slotIndex < 9) { 
                    Player.InventorySlot pSlot = player.inventory[slot.slotIndex];
                    if (pSlot.blockType != 0) {
                        Geometry icon = createIconGeometry(assetManager, pSlot.blockType, 26);
                        icon.setName("DynIcon_" + slot.slotIndex);
                        icon.setLocalTranslation(slot.x + 5, slot.y + 5, 2.2f);
                        inventoryNode.attachChild(icon);
                    }
                }
            }
            return;
        }

        BitmapText title = (BitmapText) inventoryNode.getChild("InvTitle");
        if (title != null) title.setText(isChestOpen ? "CHEST STORAGE" : "SURVIVAL INVENTORY");
        setChestGridVisible(isChestOpen);

        for (SurvivalSlotVisual slot : survivalUIGrid) {
            Player.InventorySlot pSlot = player.inventory[slot.slotIndex];
            if (pSlot.blockType != 0 && pSlot.count > 0) {
                Geometry icon = createIconGeometry(assetManager, pSlot.blockType, 26);
                icon.setName("DynIcon_" + slot.slotIndex);
                icon.setLocalTranslation(slot.x + 5, slot.y + 5, 2.2f);
                inventoryNode.attachChild(icon);

                BitmapText txt = new BitmapText(font);
                txt.setName("DynText_" + slot.slotIndex);
                txt.setSize(12);
                txt.setColor(ColorRGBA.White);
                txt.setText(String.valueOf(pSlot.count));
                txt.setLocalTranslation(slot.x + 22, slot.y + 12, 2.5f);
                inventoryNode.attachChild(txt);
            }
        }

        if (isChestOpen) {
            Player.InventorySlot[] chestInv = world.chestInventories.computeIfAbsent(currentChestCoords, k -> createEmptyChest());
            for (SurvivalSlotVisual slot : chestUIGrid) {
                Player.InventorySlot cSlot = chestInv[slot.slotIndex];
                if (cSlot.blockType != 0 && cSlot.count > 0) {
                    Geometry icon = createIconGeometry(assetManager, cSlot.blockType, 26);
                    icon.setName("DynIcon_Chest_" + slot.slotIndex);
                    icon.setLocalTranslation(slot.x + 5, slot.y + 5, 2.2f);
                    inventoryNode.attachChild(icon);

                    BitmapText txt = new BitmapText(font);
                    txt.setName("DynText_Chest_" + slot.slotIndex);
                    txt.setSize(12);
                    txt.setColor(ColorRGBA.White);
                    txt.setText(String.valueOf(cSlot.count));
                    txt.setLocalTranslation(slot.x + 22, slot.y + 12, 2.5f);
                    inventoryNode.attachChild(txt);
                }
            }
        }
    }

    private void setChestGridVisible(boolean visible) {
        for (int i = 0; i < inventoryNode.getQuantity(); i++) {
            com.jme3.scene.Spatial s = inventoryNode.getChild(i);
            if (s.getName() != null && s.getName().startsWith("ChestSlot_")) {
                s.setCullHint(visible ? com.jme3.scene.Spatial.CullHint.Inherit : com.jme3.scene.Spatial.CullHint.Always);
            }
        }
    }

    public void update(float tpf, float screenW, float screenH, boolean isInventoryOpen, float inventoryProgress, InputManager inputManager, int selectedSlot, Player player, World world) {
        
        if (debugText != null) {
            float fps = 1.0f / tpf;
            int px = (int) Math.floor(player.pos.x);
            int py = (int) Math.floor(player.pos.y);
            int pz = (int) Math.floor(player.pos.z);
            int currentBiome = world.getBiomeAt(px, pz);
            
            String biomeName = switch (currentBiome) {
                case 1 -> "DESERT PLAIN";
                case 2 -> "SNOWY TUNDRA";
                case 3 -> "MOUNTAIN PEAKS";
                case 4 -> "REDWOOD FOREST";
                case 5 -> "VOLCANIC WASTELAND";
                case 6 -> "SWAMP";
                case 7 -> "CHERRY BLOSSOM GROVE";
                case 8 -> "AMETHYST CAVE";
                case 9 -> "AUTUMN FOREST";
                case 10 -> "GLACIER PLAINS";
                case 11 -> "MYCELIUM ISLAND";
                case 14 -> "SAVANNA";
                case 15 -> "JUNGLE";
                default -> "OAK FOREST";
            };

            String debugInfo = String.format(
                "FPS: %.1f\n" +
                "XYZ: %d / %d / %d\n" +
                "Biome: %s\n" +
                "Chunks: %d [Cache: %d]\n" +
                "Bg Tasks: %d",
                fps, px, py, pz, biomeName,
                world.getLoadedChunksCount(), world.getCachedChunksCount(), world.getActiveTasksCount()
            );
            debugText.setText(debugInfo);
            debugText.setLocalTranslation(screenW - debugText.getLineWidth() - 20, screenH - 20, 10);
        }

        if (player.isInWater) {
            liquidOverlay.getMaterial().setColor("Color", new ColorRGBA(0.08f, 0.28f, 0.75f, 0.45f));
        } else if (player.isInLava) {
            liquidOverlay.getMaterial().setColor("Color", new ColorRGBA(0.92f, 0.20f, 0.02f, 0.65f));
        } else {
            liquidOverlay.getMaterial().setColor("Color", ColorRGBA.BlackNoAlpha);
        }

        if (inventoryNode != null) {
            if (inventoryProgress < 0.01f && !isInventoryOpen) {
                inventoryNode.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
            } else {
                inventoryNode.setCullHint(com.jme3.scene.Spatial.CullHint.Inherit);
                // Плавное появление: всплытие + лёгкий zoom + fade фона.
                float t = FastMath.clamp(inventoryProgress, 0.0f, 1.0f);
                float eased = FastMath.sin(t * FastMath.HALF_PI);
                float animOffset = (1.0f - eased) * 40.0f;
                float scale = 0.94f + 0.06f * eased;
                inventoryNode.setLocalTranslation(screenW / 2, screenH / 2 - animOffset, 10);
                inventoryNode.setLocalScale(scale);
                if (invBgMat != null) {
                    invBgMat.setColor("Color", new ColorRGBA(0.10f, 0.12f, 0.18f, 0.92f * eased));
                }
            }
        }

        if (isInventoryOpen && inventoryNode != null) {
            drawSurvivalSlotContent(this.assetManager, debugText.getFont(), player, world); // Передаем world в вызов

            Vector2f mousePos = inputManager.getCursorPosition();
            Vector3f invPos = inventoryNode.getLocalTranslation();
            
            float mx = mousePos.x - invPos.x;
            float my = mousePos.y - invPos.y; 

            boolean foundHover = false;
            float targetHoverX = 0f;
            float targetHoverY = 0f;

            if (isChestOpen) {
                for (SurvivalSlotVisual slot : chestUIGrid) {
                    if (slot.contains(mx, my)) {
                        targetHoverX = slot.x;
                        targetHoverY = slot.y;
                        foundHover = true;
                        break;
                    }
                }
            }

            if (!foundHover) {
                for (SurvivalSlotVisual slot : survivalUIGrid) {
                    if (slot.contains(mx, my)) {
                        targetHoverX = slot.x;
                        targetHoverY = slot.y;
                        foundHover = true;
                        break;
                    }
                }
            }

            if (foundHover) {
                if (currentHoverAlpha < 0.05f) {
                    currentHoverX = targetHoverX - 1;
                    currentHoverY = targetHoverY - 1;
                }
                currentHoverX = FastMath.interpolateLinear(tpf * 22.0f, currentHoverX, targetHoverX - 1);
                currentHoverY = FastMath.interpolateLinear(tpf * 22.0f, currentHoverY, targetHoverY - 1);
                currentHoverAlpha = FastMath.interpolateLinear(tpf * 14.0f, currentHoverAlpha, 1.0f);
            } else {
                currentHoverAlpha = FastMath.interpolateLinear(tpf * 14.0f, currentHoverAlpha, 0.0f);
            }

            invHoverSelectorNode.setLocalTranslation(currentHoverX, currentHoverY, 3);
            hoverSelectorMat.setColor("Color", new ColorRGBA(1.0f, 1.0f, 1.0f, currentHoverAlpha * 0.85f));

            // DRAG&DROP: иконка следует за курсором
            if (dragIcon != null) {
                dragIcon.setLocalTranslation(mousePos.x, mousePos.y, 5);
                if (dragText != null) {
                    dragText.setLocalTranslation(mousePos.x + 18, mousePos.y + 12, 6);
                }
            }
        } else {
            currentHoverAlpha = 0.0f;
            if (hoverSelectorMat != null) {
                hoverSelectorMat.setColor("Color", ColorRGBA.BlackNoAlpha);
            }
        }

        if (hudNode != null && hudNode.getCullHint() != com.jme3.scene.Spatial.CullHint.Always) {
            float barW = 360;
            float startX = screenW / 2 - barW / 2;
            float gap = 4;
            float slotSize = 36;
            
            float targetX = startX + gap + selectedSlot * (slotSize + gap) - 2;
            currentSelectorX = FastMath.interpolateLinear(tpf * 18.0f, currentSelectorX, targetX);
            
            float selY = 20 + (44 - slotSize) / 2 - 2;
            hotbarSelectorNode.setLocalTranslation(currentSelectorX, selY, 2);

            for (int i = 0; i < 9; i++) {
                Player.InventorySlot pSlot = player.inventory[i];
                if (pSlot.blockType != 0 && pSlot.count > 0) {
                    hotbarTexts[i].setText(String.valueOf(pSlot.count));
                } else {
                    hotbarTexts[i].setText("");
                }
            }

            if (world.isCreative()) {
                healthBarBg.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
                healthBarFill.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
                hungerBarBg.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
                hungerBarFill.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
                oxygenBarBg.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
                oxygenBarFill.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
                saturationBarBg.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
                saturationBarFill.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
            } else {
                healthBarBg.setCullHint(com.jme3.scene.Spatial.CullHint.Inherit);
                healthBarFill.setCullHint(com.jme3.scene.Spatial.CullHint.Inherit);
                hungerBarBg.setCullHint(com.jme3.scene.Spatial.CullHint.Inherit);
                hungerBarFill.setCullHint(com.jme3.scene.Spatial.CullHint.Inherit);

                float healthPercent = player.health / player.maxHealth;
                healthPercent = FastMath.clamp(healthPercent, 0.0f, 1.0f);
                healthBarFill.setLocalScale(healthPercent * 130.0f, 1.0f, 1.0f);

                float manaPercent = (player.maxMana > 0) ? player.mana / player.maxMana : 0.0f;
                manaPercent = FastMath.clamp(manaPercent, 0.0f, 1.0f);
                manaBarFill.setLocalScale(manaPercent * 130.0f, 1.0f, 1.0f);
                manaBarBg.setCullHint(manaPercent > 0 ? com.jme3.scene.Spatial.CullHint.Inherit : com.jme3.scene.Spatial.CullHint.Always);
                manaBarFill.setCullHint(manaPercent > 0 ? com.jme3.scene.Spatial.CullHint.Inherit : com.jme3.scene.Spatial.CullHint.Always);
                if (classLabel != null) classLabel.setText("CLASS: " + (player.playerClass != null ? player.playerClass.displayName : "WARRIOR"));

                float hungerPercent = player.hunger / player.maxHunger;
                hungerPercent = FastMath.clamp(hungerPercent, 0.0f, 1.0f);
                hungerBarFill.setLocalScale(hungerPercent * 130.0f, 1.0f, 1.0f);

                if (player.oxygen < player.maxOxygen) {
                    oxygenBarBg.setCullHint(com.jme3.scene.Spatial.CullHint.Inherit);
                    oxygenBarFill.setCullHint(com.jme3.scene.Spatial.CullHint.Inherit);
                    float oxPercent = player.oxygen / player.maxOxygen;
                    oxPercent = FastMath.clamp(oxPercent, 0.0f, 1.0f);
                    oxygenBarFill.setLocalScale(oxPercent * 130.0f, 1.0f, 1.0f);
                } else {
                    oxygenBarBg.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
                    oxygenBarFill.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
                    saturationBarBg.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
                    saturationBarFill.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
                }

                // БАР НАСЫЩЕНИЯ (saturation) — всегда видим, зелёный
                float satPercent = player.saturation / player.maxSaturation;
                satPercent = FastMath.clamp(satPercent, 0.0f, 1.0f);
                saturationBarFill.setLocalScale(satPercent * 130.0f, 1.0f, 1.0f);
            }

            if (selectedSlot != prevSelectedSlot) {
                prevSelectedSlot = selectedSlot;
                byte blockType = player.inventory[selectedSlot].blockType;
                if (blockType != 0) {
                    blockNameText.setText(getBlockName(blockType));
                    blockNameTimer = 1.5f; 
                } else {
                    blockNameTimer = 0.0f;
                }
            }

            if (blockNameTimer > 0.0f) {
                blockNameTimer -= tpf;
                float alpha = 1.0f;
                if (blockNameTimer > 1.25f) { 
                    alpha = (1.5f - blockNameTimer) / 0.25f;
                } else if (blockNameTimer < 0.40f) { 
                    alpha = blockNameTimer / 0.40f;
                }
                alpha = FastMath.clamp(alpha, 0f, 1f);
                blockNameText.setColor(new ColorRGBA(1f, 1f, 1f, alpha));
                blockNameText.setCullHint(com.jme3.scene.Spatial.CullHint.Inherit);
                
                float textW = blockNameText.getLineWidth();
                blockNameText.setLocalTranslation(screenW / 2 - textW / 2, 85f, 15);
            } else {
                blockNameText.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
            }
        }
    }

    public void setWeatherLabel(String name) {
        if (weatherLabel != null) {
            weatherLabel.setText("WEATHER: " + name);
        }
    }

    public void setHudVisible(boolean visible) {
        if (hudNode != null) {
            hudNode.setCullHint(visible ? com.jme3.scene.Spatial.CullHint.Inherit : com.jme3.scene.Spatial.CullHint.Always);
        }
    }

    public void showDeathScreen(boolean show) {
        if (deathScreenNode != null) {
            deathScreenNode.setCullHint(show ? com.jme3.scene.Spatial.CullHint.Inherit : com.jme3.scene.Spatial.CullHint.Always);
        }
    }

    public void cleanup(Node guiNode) {
        if (crosshair != null) {
            crosshair.removeFromParent();
            crosshair = null;
        }
        if (debugText != null) {
            debugText.removeFromParent();
            debugText = null;
        }
        if (hudNode != null) {
            hudNode.removeFromParent();
            hudNode = null;
        }
        if (inventoryNode != null) {
            inventoryNode.removeFromParent();
            inventoryNode = null;
        }
        if (blockNameText != null) {
            blockNameText.removeFromParent();
            blockNameText = null;
        }
        if (consoleNode != null) {
            consoleNode.removeFromParent();
            consoleNode = null;
        }
        if (deathScreenNode != null) {
            deathScreenNode.removeFromParent();
            deathScreenNode = null;
        }
    }

    private Node createBorder(AssetManager assetManager, float w, float h, float thickness, ColorRGBA color, float z) {
        Node borderNode = new Node("BorderNode");
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
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

    private Geometry createIconGeometry(AssetManager assetManager, int blockType, float size) {
        Quad quad = new Quad(size, size);
        Geometry geom = new Geometry("Icon_" + blockType, quad);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        
        if (blockType == 0) { 
            mat.setColor("Color", new ColorRGBA(0f, 0f, 0f, 0f));
            mat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
            geom.setMaterial(mat);
            return geom;
        }

        ColorRGBA color = switch (blockType) {
            case 1 -> new ColorRGBA(0.25f, 0.65f, 0.25f, 1.0f);
            case 2 -> new ColorRGBA(0.42f, 0.28f, 0.15f, 1.0f);
            case 3 -> new ColorRGBA(0.45f, 0.45f, 0.48f, 1.0f);
            case 8 -> new ColorRGBA(0.85f, 0.82f, 0.55f, 1.0f);
            case 9 -> new ColorRGBA(0.18f, 0.45f, 0.12f, 1.0f);
            case 10 -> new ColorRGBA(0.95f, 0.95f, 0.98f, 1.0f);
            case 6 -> new ColorRGBA(0.38f, 0.24f, 0.12f, 1.0f);
            case 7 -> new ColorRGBA(0.12f, 0.50f, 0.12f, 1.0f);
            case 11 -> new ColorRGBA(0.08f, 0.32f, 0.18f, 1.0f);
            case 12 -> new ColorRGBA(0.42f, 0.18f, 0.12f, 1.0f); 
            case 13 -> new ColorRGBA(0.15f, 0.15f, 0.16f, 1.0f); 
            case 14 -> new ColorRGBA(0.85f, 0.78f, 0.45f, 1.0f); 
            case 15 -> new ColorRGBA(0.20f, 0.55f, 0.22f, 1.0f); 
            case 19 -> new ColorRGBA(0.68f, 0.28f, 0.98f, 1.0f); 
            case 20 -> new ColorRGBA(0.92f, 0.92f, 0.95f, 1.0f); 
            case 21 -> new ColorRGBA(0.95f, 0.52f, 0.10f, 1.0f); 
            case 22 -> new ColorRGBA(0.48f, 0.85f, 0.98f, 1.0f); 
            case 23 -> new ColorRGBA(0.48f, 0.25f, 0.52f, 1.0f); 
            case 24 -> new ColorRGBA(0.88f, 0.12f, 0.12f, 1.0f); 
            case 25 -> new ColorRGBA(0.58f, 0.32f, 0.15f, 1.0f); 
            case 26 -> new ColorRGBA(0.75f, 0.50f, 0.20f, 1.0f); 
            case 27 -> new ColorRGBA(0.12f, 0.42f, 0.85f, 0.72f); 
            case 28 -> new ColorRGBA(0.92f, 0.18f, 0.02f, 1.0f);  
            case 29 -> new ColorRGBA(0.85f, 0.85f, 0.88f, 1.0f); 
            case 30 -> new ColorRGBA(0.70f, 0.70f, 0.75f, 1.0f); 
            default -> new ColorRGBA(0.45f, 0.45f, 0.48f, 1.0f);
        };
        mat.setTexture("ColorMap", ProceduralTextureGenerator.createProceduralBlockTexture(color, blockType));
        mat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        geom.setMaterial(mat);
        return geom;
    }

    public static String getBlockName(int blockType) {
        return switch (blockType) {
            case 1 -> "GRASS BLOCK";
            case 2 -> "DIRT BLOCK";
            case 3 -> "STONE";
            case 4 -> "COAL ORE";
            case 5 -> "IRON ORE";
            case 6 -> "OAK LOG";
            case 7 -> "OAK LEAVES";
            case 8 -> "SAND";
            case 9 -> "CACTUS";
            case 10 -> "SNOW BLOCK";
            case 11 -> "PINE LEAVES";
            case 12 -> "REDWOOD LOG";
            case 13 -> "BASALT";
            case 14 -> "MAGMA BLOCK";
            case 15 -> "CHERRY LOG";
            case 16 -> "CHERRY LEAVES";
            case 19 -> "AMETHYST BLOCK";
            case 20 -> "BIRCH LOG";
            case 21 -> "ORANGE LEAVES";
            case 22 -> "PACKED ICE";
            case 23 -> "MYCELIUM BLOCK";
            case 24 -> "RED MUSHROOM (EDIBLE)"; 
            case 25 -> "CHEST STORAGE BLOCK";
            case 26 -> "WOODEN AXE (TOOL)";
            case 27 -> "WATER SOURCE";
            case 28 -> "LAVA SOURCE";
            case 29 -> "IRON PICKAXE (TOOL)";
            case 30 -> "IRON SHOVEL (TOOL)";
            case 31 -> "TNT EXPLOSIVE";
            case 32 -> "SLIME BLOCK";
            case 33 -> "TORCH (LIGHT SOURCE)";
            default -> "";
        };
    }

    private void createConsoleUI(AssetManager assetManager, BitmapFont font, Node guiNode) {
        consoleNode = new Node("ConsoleNode");
        consoleNode.setCullHint(com.jme3.scene.Spatial.CullHint.Always);

        Quad bgQuad = new Quad(1, 160);
        consoleBg = new Geometry("ConsoleBg", bgQuad);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", new ColorRGBA(0.06f, 0.06f, 0.08f, 0.90f));
        mat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        consoleBg.setMaterial(mat);
        consoleNode.attachChild(consoleBg);

        consoleInputText = new BitmapText(font);
        consoleInputText.setSize(18);
        consoleInputText.setColor(ColorRGBA.White);
        consoleInputText.setText(">");
        consoleNode.attachChild(consoleInputText);

        for (int i = 0; i < 6; i++) {
            consoleLogLines[i] = new BitmapText(font);
            consoleLogLines[i].setSize(15);
            consoleLogLines[i].setColor(ColorRGBA.LightGray);
            consoleLogLines[i].setText("");
            consoleNode.attachChild(consoleLogLines[i]);
        }

        guiNode.attachChild(consoleNode);
    }

    private void createDeathScreen(AssetManager assetManager, BitmapFont font, Node guiNode) {
        deathScreenNode = new Node("DeathScreenNode");
        deathScreenNode.setCullHint(com.jme3.scene.Spatial.CullHint.Always);

        Quad bgQuad = new Quad(1, 1);
        Geometry bg = new Geometry("DeathBg", bgQuad);
        Material bgMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        bgMat.setColor("Color", new ColorRGBA(0.45f, 0.05f, 0.05f, 0.55f)); 
        bgMat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        bg.setMaterial(bgMat);
        deathScreenNode.attachChild(bg);

        BitmapText deathText = new BitmapText(font);
        deathText.setName("DeathText");
        deathText.setText("YOU DIED!");
        deathText.setSize(52);
        deathText.setColor(ColorRGBA.White);
        deathScreenNode.attachChild(deathText);

        BitmapText respawnHint = new BitmapText(font);
        respawnHint.setName("RespawnHint");
        respawnHint.setText("Press [ R ] to Respawn");
        respawnHint.setSize(22);
        respawnHint.setColor(ColorRGBA.LightGray);
        deathScreenNode.attachChild(respawnHint);

        guiNode.attachChild(deathScreenNode);
    }

    public void setConsoleInputText(String txt) {
        if (consoleInputText != null) {
            consoleInputText.setText(txt);
        }
    }

    public void addConsoleLog(String log) {
        addConsoleLog(log, ColorRGBA.White);
    }

    public void addConsoleLog(String log, ColorRGBA color) {
        consoleLines.add(new ConsoleLine(log, color));
        if (consoleLines.size() > 50) {
            consoleLines.remove(0); 
        }
    }

    public void updateConsole(float tpf, float w, float h, float progress) {
        if (consoleNode == null) return;

        if (progress <= 0.01f) {
            consoleNode.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
            return;
        }

        consoleNode.setCullHint(com.jme3.scene.Spatial.CullHint.Inherit);

        float offset = (1.0f - progress) * -165f;
        consoleNode.setLocalTranslation(0, offset, 20);

        consoleBg.setLocalScale(w, 1.0f, 1.0f);
        consoleInputText.setLocalTranslation(15, 25, 2);

        int size = consoleLines.size();
        for (int i = 0; i < 6; i++) {
            int targetIdx = size - 6 + i;
            if (targetIdx >= 0 && targetIdx < size) {
                ConsoleLine line = consoleLines.get(targetIdx);
                consoleLogLines[i].setText(line.text);
                consoleLogLines[i].setColor(line.color);
            } else {
                consoleLogLines[i].setText("");
            }
            consoleLogLines[i].setLocalTranslation(15, 150 - (i * 18), 2);
        }
    }
}