package com.solr98.beyondintegration.client.gui.extension;

import com.solr98.beyondintegration.api.IDimensionsNetGUIExtension;
import com.solr98.beyondintegration.network.OpenStorageMenuPacket;
import com.solr98.beyondintegration.network.PacketHandler;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;

/**
 * 存储界面扩展：在 BD 主界面左侧工具栏添加"打开存储界面"按钮，
 * 点击时保存切换上下文并发送打开存储工作站的请求。
 */
public class StorageMenuExtension implements IDimensionsNetGUIExtension {
    /** 按钮屏幕坐标（左侧工具栏第 1 个扩展位） */
    private int bx,by;
    @Override public int priority(){return 0;}
    /** 计算按钮位置：界面左缘左侧 18px，位于 BD 按钮下方的第 1 个扩展位 */
    @Override public void onInit(DimensionsNetGUI<?> g){bx=g.getGuiLeft()-18;by=BDGUIExtensionRegistry.getSlotY(g.getGuiTop(),1);}
    /** 渲染按钮图标（悬浮态高亮纹理 + 铁砧图标），悬浮时显示 tooltip；
     *  STORAGE 实为 BD 终端界面（非工作台），不受 workstations.enabled 可用列表控制 */
    @Override public void onRender(DimensionsNetGUI<?> g, GuiGraphics gr, int mx, int my, float pt){
        boolean h=mx>=bx&&mx<bx+16&&my>=by&&my<by+16;
        gr.blit(ResourceLocation.tryParse(h?"beyonddimensions:textures/gui/sprites/widget/slot_button_hovered.png":"beyonddimensions:textures/gui/sprites/widget/slot_button.png"),bx,by,0,0,16,16,16,16);
        var p=gr.pose();p.pushPose();p.translate(bx+1,by+1,1);p.scale(0.85f,0.85f,1);gr.renderFakeItem(new ItemStack(Items.ANVIL),0,0);p.popPose();
        if(h)gr.renderTooltip(Minecraft.getInstance().font, List.of(Component.translatable("gui.beyond_integration.open_storage")),ItemStack.EMPTY.getTooltipImage(),ItemStack.EMPTY,mx,my);
    }
    /** 点击按钮：保存切换上下文并发送打开存储界面的请求（BD 终端界面，不受工作台可用列表控制） */
    @Override public boolean onMouseClicked(DimensionsNetGUI<?> g, double mx, double my, int btn){
        if(mx>=bx&&mx<bx+16&&my>=by&&my<by+16){
            com.solr98.beyondintegration.client.gui.WorkstationTransferHelper.save(
                    (com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu)g.getMenu());
            PacketHandler.sendToServer(new OpenStorageMenuPacket(OpenStorageMenuPacket.Type.STORAGE));
            return true;
        }
        return false;
    }
}
