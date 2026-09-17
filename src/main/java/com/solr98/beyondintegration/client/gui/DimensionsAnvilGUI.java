package com.solr98.beyondintegration.client.gui;

import com.solr98.beyondintegration.init.DimensionsAnvilMenu;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.payload.SetAnvilNamePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 维度网络铁砧工作站界面。
 * 在工作站面板中渲染铁砧背景、改名输入框、错误提示与费用显示，
 * 费用支持等级/点数两种模式并受上限配置约束。
 */
public class DimensionsAnvilGUI extends DimensionsStorageGUI<DimensionsAnvilMenu> {
    private static final ResourceLocation BG = ResourceLocation.parse("beyond_integration:textures/gui/anvil.png"); // 铁砧面板底图
    private static final ResourceLocation RN = ResourceLocation.parse("beyond_integration:textures/gui/anvil_rename.png"); // 改名框贴图
    private static final ResourceLocation ERROR_ICON = ResourceLocation.parse("beyond_integration:textures/gui/notfor.png"); // 不可合成红叉图标
    private EditBox nameField; // 改名输入框
    private ItemStack lastInput = ItemStack.EMPTY; // 上次输入槽物品（用于检测变化）

    public DimensionsAnvilGUI(DimensionsAnvilMenu c, Inventory p, Component t) { super(c, p, t); }

    // 原版 AnvilScreen.onNameChanged：物品无 CUSTOM_NAME 且输入等于默认名时视为未改名（置空，避免误收改名费）
    /** 同步改名内容到菜单并发送服务端 */
    private void syncName(String raw) {
        ItemStack input = this.menu.getInput();
        if (input.isEmpty()) return;
        String s = raw;
        if (!input.has(DataComponents.CUSTOM_NAME) && raw.equals(input.getHoverName().getString())) {
            s = "";
        }
        this.menu.anvName = s;
        PacketHandler.sendToServer(new SetAnvilNamePayload(this.menu.containerId, s));
    }

    @Override protected void init() {
        super.init();
        // 初始化或重定位改名输入框
        if (nameField == null) {
            nameField = new EditBox(Minecraft.getInstance().font, this.leftPos + 61, getGapY() + 20, 110, 12, Component.translatable("container.repair"));
            nameField.setCanLoseFocus(false); nameField.setTextColor(-1); nameField.setTextColorUneditable(-1);
            nameField.setBordered(false); nameField.setMaxLength(50);
            nameField.setResponder(t -> { if (!t.equals(this.menu.anvName)) syncName(t); });
        } else {
            nameField.setX(this.leftPos + 61); nameField.setY(getGapY() + 20);
        }
        // resize(rebuildWidgets) 会清空所有控件，需重新注册
        if (!this.children().contains(nameField)) addRenderableWidget(nameField);
        ItemStack input = this.menu.getInput();
        nameField.setEditable(!input.isEmpty());
        if (!input.isEmpty()) {
            String name = input.getHoverName().getString();
            this.menu.anvName = name;
            nameField.setValue(name);
            syncName(name);
        }
    }

    @Override protected void setInitialFocus() { if (nameField != null) this.setInitialFocus(this.nameField); } // 初始焦点置于改名框

    // 原版 AnvilScreen.resize：重开界面后恢复输入文本
    @Override public void resize(Minecraft mc, int w, int h) {
        String s = this.nameField == null ? "" : this.nameField.getValue();
        this.init(mc, w, h);
        if (this.nameField != null) this.nameField.setValue(s);
    }

    @Override protected void renderWorkstationPanel(GuiGraphics g) {
        // 绘制铁砧面板、标题、改名框背景与错误红叉
        int gy = getGapY(); g.blit(BG, this.leftPos, gy, 0, 0, 176, 62, 176, 62);
        g.drawString(Minecraft.getInstance().font, Component.translatable("gui.beyond_integration.workstation.anvil"), this.leftPos + 6, gy - 7, 0x404040, false);
        boolean h = !this.menu.getInput().isEmpty();
        g.blit(RN, this.leftPos + 59, gy + 15, 0, h ? 0 : 16, 110, 16, 110, 32);
        // 原版 AnvilScreen.renderErrorIcon：有输入但无结果时显示红叉
        if ((!this.menu.getInput().isEmpty() || !this.menu.getAdditional().isEmpty()) && this.menu.getOutput().isEmpty()) {
            g.blit(ERROR_ICON, this.leftPos + 99, gy + 40, 0, 0, 28, 21, 28, 21);
        }
        if (nameField != null) { nameField.setX(this.leftPos + 61); nameField.setY(gy + 20); }
    }

    // 原版 AnvilScreen.renderLabels：费用显示（昂贵红字 / mayPickup 失败红字 / 右对齐 + 背景填充）
    // 放在 renderLabels 层（槽位物品之上）；消耗模式与费用上限由配置决定
    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        super.renderLabels(g, mx, my);
        int cost = this.menu.anvLevel;
        if (cost <= 0) return;
        var player = Minecraft.getInstance().player;
        var font = Minecraft.getInstance().font;
        int color = 8453920;
        Component comp;
        boolean pointsMode = com.solr98.beyondintegration.CommandConfig.anvilCostMode()
                == com.solr98.beyondintegration.CommandConfig.AnvilChargeMode.POINTS;
        // 费用上限：LEVEL 按等级、POINTS 按点数，达到即"过于昂贵"
        boolean overCap = pointsMode
                ? this.menu.getCostPoints() >= com.solr98.beyondintegration.CommandConfig.anvilPointsCap()
                : cost >= com.solr98.beyondintegration.CommandConfig.anvilLevelCap();
        if (overCap && player != null && !player.getAbilities().instabuild) {
            comp = Component.translatable("container.repair.expensive");
            color = 16736352;
        } else if (this.menu.getOutput().isEmpty()) {
            return;
        } else {
            // LEVEL 模式显示等级，POINTS 模式显示经验点数（明确标注模式，避免玩家误解）
            if (pointsMode) {
                comp = Component.translatable("gui.beyond_integration.anvil.cost_points", this.menu.getCostPoints());
            } else {
                comp = Component.translatable("gui.beyond_integration.anvil.cost_level", cost);
            }
            if (player != null && !this.menu.slots.get(this.menu.customSlotIndices.get(this.menu.customSlotIndices.size() - 1)).mayPickup(player)) {
                color = 16736352;
            }
        }
        // renderLabels 坐标系已平移至 (leftPos, topPos)，使用相对坐标
        int gy = getGapY() - this.topPos;
        int k = 176 - 8 - font.width(comp) - 2;
        g.fill(k - 2, gy + 62, 176 - 8, gy + 74, 1325400064);
        g.drawString(font, comp, k, gy + 64, color);
    }

    // 原版 AnvilScreen.keyPressed：Esc 直接关闭容器
    @Override public boolean keyPressed(int k, int s, int m) {
        if (k == 256) {
            if (this.minecraft.player != null) this.minecraft.player.closeContainer();
            return true;
        }
        if (nameField != null) {
            if (nameField.keyPressed(k, s, m)) return true;
            if (nameField.canConsumeInput()) return true;
        }
        return super.keyPressed(k, s, m);
    }

    @Override public boolean charTyped(char c, int m) { if (nameField != null && nameField.charTyped(c, m)) return true; return super.charTyped(c, m); }

    @Override public void containerTick() {
        super.containerTick();
        if (nameField == null) return;
        // 取走输出必然清空输入槽（onTake 清理），只依赖输入槽变化清空一次即可；
        // 结果槽为空但输入仍在（如配方失效）时原版也不清空名字
        ItemStack input = this.menu.getInput();
        if (!ItemStack.matches(lastInput, input)) {
            boolean wasEmpty = lastInput.isEmpty();
            lastInput = input.copy();
            if (input.isEmpty()) {
                // 原版 slotChanged：物品被移出/取走时清空输入框并禁用
                if (!wasEmpty) clearName();
            } else {
                nameField.setEditable(true);
                String newName = input.getHoverName().getString();
                if (!newName.equals(this.menu.anvName)) {
                    this.menu.anvName = newName;
                    nameField.setValue(newName);
                    syncName(newName);
                }
            }
        }
    }

    // 统一清空名称框：复位本地状态并同步服务端
    private void clearName() {
        this.menu.anvName = "";
        nameField.setEditable(false);
        nameField.setValue("");
        PacketHandler.sendToServer(new SetAnvilNamePayload(this.menu.containerId, ""));
    }
}
