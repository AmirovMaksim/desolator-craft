package com.mygame;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

/**
 * Экран загрузки мира (как в Minecraft): тёмный фон, заголовок,
 * прогресс-бар, процент и случайный совет. Показывается, пока
 * критические чанки вокруг точки спавна не загрузятся.
 */
public class WorldLoadingScreen {
    private Node root;
    private Node guiNodeRef;
    private BitmapText titleText;
    private BitmapText percentText;
    private BitmapText tipText;
    private Geometry barBg;
    private Geometry barFill;
    private Material fillMat;
    private final String[] tips = {
        "Двойной Space — полёт в креативе",
        "ЛКМ — копать/атаковать, ПКМ — ставить/есть",
        "E — инвентарь, F5 — вид от 3-го лица",
        "Колесо/цифры — выбор слота, название блока всплывает сверху",
        "Команды: /weather, /save, /tp, /heal, /give",
        "Берегись мобов в темноте!",
        "Торчи (блок 33) дают свет ночью",
        "Авто-сохранение каждые 90 секунд"
    };
    private final java.util.Random rnd = new java.util.Random();

    public void init(AssetManager assetManager, Node guiNode, BitmapFont font, float w, float h) {
        this.guiNodeRef = guiNode;
        root = new Node("WorldLoadingScreen");

        Quad bgQuad = new Quad(w, h);
        Geometry bg = new Geometry("LoadBg", bgQuad);
        Material bgMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        bgMat.setColor("Color", new ColorRGBA(0.04f, 0.05f, 0.08f, 1.0f));
        bg.setMaterial(bgMat);
        bg.setLocalTranslation(0, 0, 0);
        root.attachChild(bg);

        titleText = new BitmapText(font);
        titleText.setText("ЗАГРУЗКА МИРА");
        titleText.setSize(40);
        titleText.setColor(new ColorRGBA(0.95f, 0.78f, 0.15f, 1.0f));
        titleText.setLocalTranslation(w / 2 - titleText.getLineWidth() / 2, h / 2 + 70, 1);
        root.attachChild(titleText);

        float barW = 420, barH = 22;
        float barX = w / 2 - barW / 2;
        float barY = h / 2 - barH / 2;

        barBg = new Geometry("LoadBarBg", new Quad(barW, barH));
        Material barBgMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        barBgMat.setColor("Color", new ColorRGBA(0.18f, 0.18f, 0.22f, 1.0f));
        barBg.setMaterial(barBgMat);
        barBg.setLocalTranslation(barX, barY, 1);
        root.attachChild(barBg);

        barFill = new Geometry("LoadBarFill", new Quad(barW, barH));
        fillMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        fillMat.setColor("Color", new ColorRGBA(0.30f, 0.78f, 0.30f, 1.0f));
        barFill.setMaterial(fillMat);
        barFill.setLocalTranslation(barX, barY, 2);
        root.attachChild(barFill);
        barFill.setLocalScale(0.01f, 1.0f, 1.0f); // почти пусто

        percentText = new BitmapText(font);
        percentText.setSize(18);
        percentText.setColor(ColorRGBA.White);
        percentText.setText("0%");
        percentText.setLocalTranslation(w / 2 - 14, barY - 28, 2);
        root.attachChild(percentText);

        tipText = new BitmapText(font);
        tipText.setSize(16);
        tipText.setColor(new ColorRGBA(0.7f, 0.72f, 0.78f, 1.0f));
        tipText.setText(tips[rnd.nextInt(tips.length)]);
        tipText.setLocalTranslation(w / 2 - tipText.getLineWidth() / 2, h / 2 - 90, 2);
        root.attachChild(tipText);

        guiNode.attachChild(root);
    }

    public void show() { if (root.getParent() == null) guiNodeRef.attachChild(root); }
    public void hide() { if (root.getParent() != null) root.removeFromParent(); }

    public void setProgress(float p) {
        p = Math.max(0f, Math.min(1f, p));
        barFill.setLocalScale(p, 1.0f, 1.0f);
        float barW = 420f;
        float curW = barW * p;
        percentText.setText((int) (p * 100) + "%");
        // центрируем процент над баром (barX вычисляется в reposition)
        float bx = barFill.getLocalTranslation().x;
        percentText.setLocalTranslation(bx + curW / 2f - 12f, barFill.getLocalTranslation().y - 28, 2);
    }

    public void reposition(float w, float h) {
        if (root == null) return;
        root.getChild(0).setLocalTranslation(0, 0, 0);
        // пересчёт позиций текстов/бара относительно нового размера
        titleText.setLocalTranslation(w / 2 - titleText.getLineWidth() / 2, h / 2 + 70, 1);
        float barW = 420, barH = 22;
        float barX = w / 2 - barW / 2;
        float barY = h / 2 - barH / 2;
        barBg.setLocalTranslation(barX, barY, 1);
        barFill.setLocalTranslation(barX, barY, 2);
        percentText.setLocalTranslation(w / 2 - 14, barY - 28, 2);
        tipText.setLocalTranslation(w / 2 - tipText.getLineWidth() / 2, h / 2 - 90, 2);
    }

    public void cleanup() { if (root != null) root.removeFromParent(); root = null; }

}
