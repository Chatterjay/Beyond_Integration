package com.solr98.beyondintegration.client.gui.extension;

import com.solr98.beyondintegration.api.IDimensionsNetGUIExtension;
import com.solr98.beyondintegration.client.gui.WorkstationModeConstants;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.payload.OpenStorageMenuPayload;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;

/**
 * 存储界面快捷按钮扩展。
 * 在 BD 网络界面左侧渲染"打开存储"按钮，
 * 点击时保存当前界面上下文并发送打开存储菜单请求。
 */
public class StorageMenuExtension implements IDimensionsNetGUIExtension {
    private int bx, by; // 按钮坐标
    @Override public int priority() { return 0; } // 渲染优先级：最先渲染
    @Override public void onInit(DimensionsNetGUI<?> g) { bx = g.getGuiLeft() - 18; by = BDGUIExtensionRegistry.getSlotY(g.getGuiTop(), 1); } // 初始化按钮位置（界面左外侧）
    @Override
    public void onRender(DimensionsNetGUI<?> g, GuiGraphics gr, int mx, int my, float pt) {
        // STORAGE 实为 BD 终端界面（非工作台），不受 workstations.enabled 可用列表控制
        // 渲染按钮底图（悬浮态切换）与铁砧图标，悬浮时显示提示
        boolean h = mx >= bx && mx < bx + 16 && my >= by && my < by + 16;
        gr.blit(ResourceLocation.parse(h ? "beyonddimensions:textures/gui/sprites/widget/slot_button_hovered.png" : "beyonddimensions:textures/gui/sprites/widget/slot_button.png"), bx, by, 0, 0, 16, 16, 16, 16);
        var p = gr.pose(); p.pushPose(); p.translate(bx + 1, by + 1, 1); p.scale(0.85f, 0.85f, 1); gr.renderFakeItem(new ItemStack(Items.ANVIL), 0, 0); p.popPose();
        if (h) gr.renderTooltip(Minecraft.getInstance().font, List.of(Component.translatable("gui.beyond_integration.open_storage")), ItemStack.EMPTY.getTooltipImage(), ItemStack.EMPTY, mx, my);
    }
    @Override
    public boolean onMouseClicked(DimensionsNetGUI<?> g, double mx, double my, int btn) {
        // 点击按钮：保存界面上下文并请求打开存储界面（BD 终端界面，不受工作台可用列表控制）
        if (mx >= bx && mx < bx + 16 && my >= by && my < by + 16) {
            com.solr98.beyondintegration.client.gui.WorkstationTransferHelper.save(
                    (com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu) g.getMenu());
            PacketHandler.sendToServer(new OpenStorageMenuPayload(WorkstationModeConstants.Mode.STORAGE, false));
            return true;
        }
        return false;
    }
}

