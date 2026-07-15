package com.mygame;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;

public class ItemDropManager {

    public static class DroppedItem {
        Geometry geom;
        Vector3f pos = new Vector3f();
        Vector3f vel = new Vector3f();
        byte blockType;
        float age = 0.0f;
        float pickupDelay = 1.2f; 
        boolean onGround = false;
    }

    private final List<DroppedItem> droppedItems = new ArrayList<>();
    private Node droppedItemsNode;

    private SoundManager soundManager;
    public void setSoundManager(SoundManager sm) { this.soundManager = sm; }

    public void init(Node rootNode) {
        droppedItemsNode = new Node("DroppedItemsNode");
        rootNode.attachChild(droppedItemsNode);
    }

    public void spawnDroppedItemAt(float x, float y, float z, byte blockType, boolean isTossed, Vector3f camDir, Material[] blockMaterials) {
        DroppedItem item = new DroppedItem();
        item.blockType = blockType;
        item.pos.set(x, y, z);

        if (isTossed) {
            item.vel.set(camDir.mult(6.5f)); 
            item.vel.y += 1.8f; 
        } else {
            java.util.Random r = new java.util.Random();
            item.vel.set((r.nextFloat() - 0.5f) * 1.5f, r.nextFloat() * 1.5f + 1.0f, (r.nextFloat() - 0.5f) * 1.5f);
        }

        Box box = new Box(0.12f, 0.12f, 0.12f);
        item.geom = new Geometry("DroppedBlock_" + blockType, box);
        
        Material dropMat;
        if (blockType >= 1 && blockType < blockMaterials.length && blockMaterials[blockType] != null) {
            dropMat = blockMaterials[blockType];
        } else {
            dropMat = blockMaterials[19]; 
        }
        
        item.geom.setMaterial(dropMat);
        item.geom.setShadowMode(RenderQueue.ShadowMode.Cast);
        item.geom.setLocalTranslation(item.pos);

        droppedItemsNode.attachChild(item.geom);
        droppedItems.add(item);
    }

    public void update(float tpf, Player player, World world, UserInterfaceManager ui, AssetManager assetManager, Main app) {
        Iterator<DroppedItem> iterator = droppedItems.iterator();
        while (iterator.hasNext()) {
            DroppedItem item = iterator.next();
            item.age += tpf;

            if (item.pickupDelay > 0.0f) {
                item.pickupDelay -= tpf;
            }

            // ИСПРАВЛЕНО: floor() вместо round() — блоки занимают [i, i+1), иначе
            // предмет иногда проваливался сквозь пол при определённой дробной части позиции.
            int ix = (int) Math.floor(item.pos.x);
            int iz = (int) Math.floor(item.pos.z);

            if (!item.onGround) {
                item.vel.y -= 12.0f * tpf;
                Vector3f nextPos = item.pos.add(item.vel.mult(tpf));

                int iy = (int) Math.floor(nextPos.y - 0.12f);

                boolean collision = false;
                if (iy >= 0 && iy < Chunk.SIZE_Y) {
                    byte b = world.getBlockAt(ix, iy, iz);
                    if (b != 0) {
                        collision = true;
                    }
                }

                if (collision) {
                    item.pos.y = iy + 1.0f + 0.12f; 
                    item.vel.set(0, 0, 0);
                    item.onGround = true;
                } else {
                    item.pos.set(nextPos);
                    if (item.pos.y < 0) {
                        item.pos.y = 0.12f;
                        item.vel.set(0, 0, 0);
                        item.onGround = true;
                    }
                }
            } else {
                int iyUnder = (int) Math.floor(item.pos.y - 1.0f);
                if (iyUnder >= 0 && iyUnder < Chunk.SIZE_Y) {
                    byte b = world.getBlockAt(ix, iyUnder, iz);
                    if (b == 0) {
                        item.onGround = false;
                    }
                }
            }

            float bobbingOffset = FastMath.sin(item.age * 2.5f) * 0.04f;
            item.geom.setLocalTranslation(item.pos.x, item.pos.y + bobbingOffset, item.pos.z);
            item.geom.rotate(0, tpf * 1.5f, 0); 

            float dist = player.pos.distance(item.pos);
            if (dist < 1.5f && item.pickupDelay <= 0.0f && !player.isDead) {
                boolean added = player.addItem(item.blockType, 1);
                if (added) {
                    if (soundManager != null) soundManager.pickup();
                    app.syncHotbarArray();
                    for (int i = 0; i < 9; i++) {
                        ui.updateHotbarIcon(assetManager, i, player.inventory[i].blockType);
                    }
                    item.geom.removeFromParent();
                    iterator.remove();
                }
            }
        }
    }

    public void clear() {
        if (droppedItemsNode != null) {
            droppedItemsNode.detachAllChildren();
        }
        droppedItems.clear();
    }
}