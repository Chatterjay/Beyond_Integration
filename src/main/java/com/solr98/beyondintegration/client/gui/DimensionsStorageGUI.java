package com.solr98.beyondintegration.client.gui;

import com.solr98.beyondintegration.feature.crafting.DimensionsStorageMenu;
import com.solr98.beyondintegration.network.PacketHandler;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * 工作站通用界面基类：在 BD 网络存储界面之上叠加工作站面板，
 * 负责面板背景分段拼接、面板高度计算、操作按钮（清空/归还方向）与标签重排。
 */
public class DimensionsStorageGUI<T extends DimensionsStorageMenu> extends DimensionsNetGUI<T> {
    /** BD 网络界面各段背景纹理（顶栏 / 槽位条 / 连接条 / 玩家背包等） */
    protected static final ResourceLocation
        TEX_TOP  = ResourceLocation.tryParse("beyonddimensions:textures/gui/top_base.png"),
        TEX_TSL  = ResourceLocation.tryParse("beyonddimensions:textures/gui/top_slots.png"),
        TEX_MSL  = ResourceLocation.tryParse("beyonddimensions:textures/gui/mid_slots.png"),
        TEX_BSL  = ResourceLocation.tryParse("beyonddimensions:textures/gui/bottom_slots.png"),
        TEX_PINV = ResourceLocation.tryParse("beyonddimensions:textures/gui/player_inv.png"),
        TEX_CONN = ResourceLocation.tryParse("beyonddimensions:textures/gui/common_connection.png");

    public DimensionsStorageGUI(T c, Inventory p, Component t) { super(c,p,t); }
    /** 最近一次渲染的鼠标坐标（供子类面板交互使用） */
    protected int mouseX, mouseY;

    /** 工作站面板顶部 Y 坐标（位于存储网格与玩家背包之间的间隙处） */
    protected int getGapY() { return this.topPos + 24 + 18 + (this.menu.getLines() - 2) * 18 + 26; }
    /** 工作站面板高度（默认 62px，合成界面覆盖为 72px） */
    protected int getPanelHeight() { return 62; }
    /** 子类渲染工作站面板内容（默认空实现） */
    protected void renderWorkstationPanel(GuiGraphics g) {}

    /** 初始化时注册工作站操作按钮，并播放打开音效（对齐原版方块打开行为；存储界面无） */
    @Override protected void init() {
        super.init();
        addWorkstationActionButtons();
        var soundManager = net.minecraft.client.Minecraft.getInstance().getSoundManager();
        if (this instanceof com.solr98.beyondintegration.client.gui.DimensionsAnvilGUI) {
            soundManager.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.ANVIL_USE, 1.0F));
        } else if (this instanceof com.solr98.beyondintegration.client.gui.DimensionsCutGUI
                || this instanceof com.solr98.beyondintegration.client.gui.DimensionsGrindGUI) {
            soundManager.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
        } else if (this instanceof com.solr98.beyondintegration.client.gui.DimensionsCraftGUI
                || this instanceof com.solr98.beyondintegration.client.gui.DimensionsSmithGUI) {
            soundManager.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    // ═══ 工作站面板右上角操作按钮（仅工作站界面）：清空到背包 / 清空到网络 / 关闭归还方向 ═══
    // 渲染在工作站面板顶部（gy+2，同 BD），8x8 按钮、间隔 1px（步进 9）、右对齐留 8px
    /** 归还方向：物品清空后回到背包 或 回到网络 */
    private enum ReturnMode { INV, STORAGE }
    /** 归还方向切换按钮（两态：背包 / 网络） */
    private com.wintercogs.beyonddimensions.client.gui.widget.shared.StatusButton returnDirButton;

    /** 添加清空到背包 / 清空到网络 / 归还方向 三个操作按钮（仅工作站界面生效） */
    private void addWorkstationActionButtons() {
        if (!(this instanceof DimensionsCraftGUI || this instanceof DimensionsCutGUI
                || this instanceof DimensionsSmithGUI || this instanceof DimensionsGrindGUI
                || this instanceof DimensionsAnvilGUI)) return;
        // 顺序（左→右）：清空到网络 → 清空到背包 → 归还方向；8x8 按钮、间隔 1px；
        // 右边界对齐 168（面板右缘 176 留 8px）：142 / 151 / 160
        int bxNet = this.leftPos + 142;
        int bxInv = this.leftPos + 151;
        int bxDir = this.leftPos + 160;
        int by = getGapY() + 2;

        com.wintercogs.beyonddimensions.client.gui.widget.shared.IconButton clearToNet = new com.wintercogs.beyonddimensions.client.gui.widget.shared.IconButton(
                bxNet, by, 8, 8, ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/up_arrow.png"),
                b -> PacketHandler.sendToServer(new com.solr98.beyondintegration.network.CleanWorkstationPacket(true)));
        clearToNet.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("tooltip.beyond_integration.clean_to_net")));

        com.wintercogs.beyonddimensions.client.gui.widget.shared.IconButton clearToInv = new com.wintercogs.beyonddimensions.client.gui.widget.shared.IconButton(
                bxInv, by, 8, 8, ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/down_arrow.png"),
                b -> PacketHandler.sendToServer(new com.solr98.beyondintegration.network.CleanWorkstationPacket(false)));
        clearToInv.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("tooltip.beyond_integration.clean_to_inv")));

        returnDirButton = new com.wintercogs.beyonddimensions.client.gui.widget.shared.StatusButton(
                bxDir, by, 8, 8, b -> {
            returnDirButton.toggleState();
            boolean toStorage = returnDirButton.currentState == ReturnMode.STORAGE;
            this.menu.setReturnDir(toStorage);
            this.menu.writeAndSendQuickData();
            // 对齐 BD uiCraftReturnButton：方向写回客户端配置持久化（会话间保留）
            com.solr98.beyondintegration.ClientConfig.setWorkstationReturnToStorage(toStorage);
        }) {
            @Override protected void initButton() {
                iconMap.put(ReturnMode.INV, ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/sort_desc.png"));
                iconMap.put(ReturnMode.STORAGE, ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/sort_asc.png"));
                tooltipMap.put(ReturnMode.INV, net.minecraft.client.gui.components.Tooltip.create(Component.translatable("tooltip.beyond_integration.return_dir_inv")));
                tooltipMap.put(ReturnMode.STORAGE, net.minecraft.client.gui.components.Tooltip.create(Component.translatable("tooltip.beyond_integration.return_dir_net")));
                states.add(ReturnMode.INV);
                states.add(ReturnMode.STORAGE);
                // 初始方向从客户端配置读取（对齐 BD：setState(CommonConfigRuntime.uiCraftReturnButton)）
                setState(com.solr98.beyondintegration.ClientConfig.workstationReturnToStorage() ? ReturnMode.STORAGE : ReturnMode.INV);
            }
        };
        addRenderableWidget(clearToInv);
        addRenderableWidget(clearToNet);
        addRenderableWidget(returnDirButton);
        // 打开界面时按客户端持久化方向同步服务端（对齐 BD DimensionsCraftGUI.init 的 writeAndSendQuickData）
        this.menu.setReturnDir(com.solr98.beyondintegration.ClientConfig.workstationReturnToStorage());
        this.menu.writeAndSendQuickData();
    }

    /** 渲染背景：分段拼接 BD 网络纹理，随后渲染工作站面板与玩家背包 */
    @Override protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        mouseX = mx; mouseY = my;
        int dy = this.topPos;
        g.blit(TEX_TOP, this.leftPos, dy, 0,0,194,24,194,24); dy += 24;
        g.blit(TEX_TSL, this.leftPos, dy, 0,0,194,18,194,18); dy += 18;
        for (int i = 0; i < this.menu.getLines() - 2; i++) { g.blit(TEX_MSL, this.leftPos, dy, 0,0,194,18,194,18); dy += 18; }
        g.blit(TEX_BSL, this.leftPos, dy, 0,0,194,26,194,26); dy += 26;
        renderWorkstationPanel(g);
        dy += getPanelHeight();
        g.blit(TEX_CONN, this.leftPos, dy, 0,0,176,8,176,8); dy += 8;
        g.blit(TEX_PINV, this.leftPos, dy, 0,0,176,89,176,89);
    }

    /** 计算界面总高度（含工作站面板高度，供重建图像尺寸用） */
    @Override protected int rebuildImageHeight() { int ph = this.menu.getPanelHeight(); return 24+18+(this.menu.getLines()-2)*18+26+ph+8+89; }
    /** 重排标题与背包标签的 Y 坐标（含工作站面板高度） */
    @Override protected void rebuildLabelHeight() { int ph = this.menu.getPanelHeight(); this.titleLabelY = 8; this.inventoryLabelY = 24 + this.menu.getLines() * 18 + 5 + ph + 8; }
    /** 按当前屏幕可用高度计算存储网格最大行数（扣除工作站面板占位） */
    @Override protected int calMaxLines() { int ph = this.menu.getPanelHeight(); return (int)((this.height - 36 - (24+18+26+ph+8+89)) / 18 + 2); }
    /** 关闭界面时清除工作站切换上下文标记 */
    @Override public void onClose() { WorkstationTransferHelper.clearPending(); super.onClose(); }
}
