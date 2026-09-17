package com.solr98.beyondintegration.client.gui;

import com.solr98.beyondintegration.api.IDimensionsNetGUIExtension;
import com.solr98.beyondintegration.client.gui.extension.BDGUIExtensionRegistry;
import com.solr98.beyondintegration.init.*;
import com.solr98.beyondintegration.network.PacketHandler;
import com.wintercogs.beyonddimensions.client.gui.CommonTextures;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * 维度网络存储/工作站基类界面。
 * 继承 BD 的网络菜单界面，负责绘制网络行背景并预留工作站面板渲染钩子；
 * 工作站子类界面在此追加"清空到背包/清空到网络/归还方向"操作按钮。
 */
public class DimensionsStorageGUI<T extends DimensionsStorageMenu> extends DimensionsNetGUI<T> {
    protected static final ResourceLocation
        TEX_TOP  = ResourceLocation.parse("beyonddimensions:textures/gui/top_base.png"), // 顶部底图
        TEX_TSL  = ResourceLocation.parse("beyonddimensions:textures/gui/top_slots.png"), // 顶部槽位行
        TEX_MSL  = ResourceLocation.parse("beyonddimensions:textures/gui/mid_slots.png"), // 中部槽位行
        TEX_BSL  = ResourceLocation.parse("beyonddimensions:textures/gui/bottom_slots.png"), // 底部槽位行
        TEX_PINV = ResourceLocation.parse("beyonddimensions:textures/gui/player_inv.png"); // 玩家物品栏底图

    public DimensionsStorageGUI(T c, Inventory p, Component t) { super(c, p, t); }
    protected int mouseX, mouseY; // 当前鼠标坐标（渲染期间记录）

    /** 工作站面板的起始 Y（相对 topPos） */
    protected int getGapY() { return this.topPos + 24 + 18 + (this.menu.getLines() - 2) * 18 + 26; }
    protected int getPanelHeight() { return 62; } // 工作站面板高度（子类可覆盖）
    protected void renderWorkstationPanel(GuiGraphics g) {} // 工作站面板渲染钩子（默认空实现）

    @Override protected void init() {
        super.init();
        addWorkstationActionButtons();
        // 打开工作站界面音效（对齐原版方块打开行为：铁砧=ANVIL_USE，切石/磨石=STONECUTTER；存储界面无）
        var soundManager = net.minecraft.client.Minecraft.getInstance().getSoundManager();
        if (this instanceof DimensionsAnvilGUI) {
            soundManager.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.ANVIL_USE, 1.0F));
        } else if (this instanceof DimensionsCutGUI || this instanceof DimensionsGrindGUI) {
            soundManager.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
        } else if (this instanceof DimensionsCraftGUI || this instanceof DimensionsSmithGUI) {
            soundManager.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    // ═══ 工作站面板右上角操作按钮（仅工作站界面）：清空到背包 / 清空到网络 / 关闭归还方向 ═══
    // 渲染在工作站面板顶部（gy+2，同 BD），8x8 紧贴排列（步进 8）、右对齐留 8px
    /** 归还方向枚举：INV=归还背包，STORAGE=归还网络 */
    private enum ReturnMode { INV, STORAGE }
    private com.wintercogs.beyonddimensions.client.gui.widget.shared.StatusButton returnDirButton; // 归还方向切换按钮

    /** 为工作站子类界面添加清空与归还方向操作按钮（存储界面跳过） */
    private void addWorkstationActionButtons() {
        if (!(this instanceof DimensionsCraftGUI || this instanceof DimensionsCutGUI
                || this instanceof DimensionsSmithGUI || this instanceof DimensionsGrindGUI
                || this instanceof DimensionsAnvilGUI || this instanceof AbstractEnchantTableGUI)) return;
        // 顺序（左→右）：清空到网络 → 清空到背包 → 归还方向；8x8 按钮、间隔 1px；
        // 右边界对齐 168（面板右缘 176 留 8px）：142 / 151 / 160
        int bxNet = this.leftPos + 142;
        int bxInv = this.leftPos + 151;
        int bxDir = this.leftPos + 160;
        int by = getGapY() + 2;

        com.wintercogs.beyonddimensions.client.gui.widget.shared.IconButton clearToNet = new com.wintercogs.beyonddimensions.client.gui.widget.shared.IconButton(
                bxNet, by, 8, 8, ResourceLocation.parse("beyonddimensions:widget/up_arrow"),
                b -> PacketHandler.sendToServer(new com.solr98.beyondintegration.network.payload.CleanWorkstationPayload(true))); // 清空到网络
        clearToNet.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("tooltip.beyond_integration.clean_to_net")));

        com.wintercogs.beyonddimensions.client.gui.widget.shared.IconButton clearToInv = new com.wintercogs.beyonddimensions.client.gui.widget.shared.IconButton(
                bxInv, by, 8, 8, ResourceLocation.parse("beyonddimensions:widget/down_arrow"),
                b -> PacketHandler.sendToServer(new com.solr98.beyondintegration.network.payload.CleanWorkstationPayload(false))); // 清空到背包
        clearToInv.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("tooltip.beyond_integration.clean_to_inv")));

        // 归还方向切换：点击后同步到服务端菜单，并写回客户端配置持久化（对齐 BD uiCraftReturnButton）
        returnDirButton = new com.wintercogs.beyonddimensions.client.gui.widget.shared.StatusButton(
                bxDir, by, 8, 8, b -> {
            returnDirButton.toggleState();
            boolean toStorage = returnDirButton.currentState == ReturnMode.STORAGE;
            this.menu.setReturnDir(toStorage);
            this.menu.writeAndSendQuickData();
            com.solr98.beyondintegration.ClientConfig.setWorkstationReturnToStorage(toStorage);
        }) {
            @Override protected void initButton() {
                iconMap.put(ReturnMode.INV, ResourceLocation.parse("beyonddimensions:widget/sort_desc"));
                iconMap.put(ReturnMode.STORAGE, ResourceLocation.parse("beyonddimensions:widget/sort_asc"));
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

    @Override protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        mouseX = mx; mouseY = my;
        // 自上而下绘制：顶图 → 顶部行 → 中部可滚动行 → 底部行 → 工作站面板 → 连接分隔条 → 玩家物品栏
        int dy = this.topPos;
        g.blit(TEX_TOP, this.leftPos, dy, 0, 0, 194, 24, 194, 24); dy += 24;
        g.blit(TEX_TSL, this.leftPos, dy, 0, 0, 194, 18, 194, 18); dy += 18;
        for (int i = 0; i < this.menu.getLines() - 2; i++) { g.blit(TEX_MSL, this.leftPos, dy, 0, 0, 194, 18, 194, 18); dy += 18; }
        g.blit(TEX_BSL, this.leftPos, dy, 0, 0, 194, 26, 194, 26); dy += 26;
        renderWorkstationPanel(g);
        dy += getPanelHeight();
        g.blit(CommonTextures.COMMON_CONNECTION, this.leftPos, dy, 0, 0, 176, 8, 176, 8);
        dy += 8;
        g.blit(TEX_PINV, this.leftPos, dy, 0, 0, 176, 89, 176, 89);
    }

    @Override protected int rebuildImageHeight() { int ph = this.menu.getPanelHeight(); return 24 + 18 + (this.menu.getLines() - 2) * 18 + 26 + ph + 8 + 89; } // 按面板高度计算界面总高
    @Override protected void rebuildLabelHeight() { int ph = this.menu.getPanelHeight(); this.titleLabelY = 8; this.inventoryLabelY = 24 + this.menu.getLines() * 18 + 5 + ph + 8; } // 重新计算标题/物品栏标签 Y
    @Override protected int calMaxLines() { int ph = this.menu.getPanelHeight(); return (int)((this.height - 36 - (24 + 18 + 26 + ph + 8 + 89)) / 18 + 2); } // 计算可用行数上限
    @Override public void onClose() { WorkstationTransferHelper.clearPending(); super.onClose(); } // 关闭时清除切换标记
}

