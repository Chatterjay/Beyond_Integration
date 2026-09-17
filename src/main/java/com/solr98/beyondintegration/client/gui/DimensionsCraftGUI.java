package com.solr98.beyondintegration.client.gui;

import com.solr98.beyondintegration.init.DimensionsCraftMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * 维度网络合成工作站界面。
 * 绘制合成面板背景，并在面板中跟随鼠标旋转渲染玩家实体模型作为装饰。
 */
public class DimensionsCraftGUI extends DimensionsStorageGUI<DimensionsCraftMenu> {
    private static final ResourceLocation BG = ResourceLocation.parse("beyond_integration:textures/gui/craft.png"); // 合成面板底图
    public static final int PANEL_H = 72; // 面板高度

    public DimensionsCraftGUI(DimensionsCraftMenu c, Inventory p, Component t) { super(c, p, t); }
    @Override protected int getPanelHeight() { return PANEL_H; }
    @Override protected void renderWorkstationPanel(GuiGraphics g) {
        int gy = getGapY(); int lx = this.leftPos;
        g.blit(BG, lx, gy, 0, 0, 176, PANEL_H, 176, PANEL_H);
        // 面板内渲染玩家模型，视角跟随鼠标
        var player = Minecraft.getInstance().player;
        if (player != null) WorkstationRenderHelper.renderEntityInInventoryFollowsMouse(g, lx + 50, gy + 67, 30, (float)(lx + 50) - mouseX, (float)(gy + 36) - mouseY, player);
        g.drawString(Minecraft.getInstance().font, Component.translatable("gui.beyond_integration.workstation.craft"), lx + 6, gy - 7, 0x404040, false);
    }
}
