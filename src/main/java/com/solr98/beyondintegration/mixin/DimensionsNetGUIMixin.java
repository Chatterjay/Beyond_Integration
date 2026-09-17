package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.client.SuperbAmmoCache;
import com.solr98.beyondintegration.client.WorkstationActivationCache;
import com.solr98.beyondintegration.client.gui.LeftSidebarLayout;
import com.solr98.beyondintegration.client.gui.WorkstationModeConstants;
import com.solr98.beyondintegration.client.widget.EnchantToggleBtn;
import com.solr98.beyondintegration.client.widget.EnergyChargeToggleBtn;
import com.solr98.beyondintegration.feature.workstation.WorkstationActivation;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.RequestEnchantSeparationPacket;
import com.solr98.beyondintegration.network.RequestEnergyChargePacket;
import com.solr98.beyondintegration.network.RequestSuperbAmmoExtractPacket;
import com.solr98.beyondintegration.network.RequestSuperbAmmoStatusPacket;
import com.solr98.beyondintegration.network.ToggleEnchantSeparationPacket;
import com.solr98.beyondintegration.network.ToggleEnergyChargePacket;
import com.solr98.beyondintegration.network.payload.ActivateWorkstationPayload;
import com.solr98.beyondintegration.network.payload.OpenStorageMenuPayload;
import com.solr98.beyondintegration.network.payload.RequestWorkstationActivationPayload;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import com.wintercogs.beyonddimensions.client.gui.widget.LeftButtonSidebar;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * 注入目标：Beyond Dimensions 0.7.30+ 的 {@code DimensionsNetGUI}（维度网络界面）。
 * 0.7.30 起作者提供了 LeftButtonSidebar（左侧按钮栏，按添加顺序自动纵向布局）。
 * 附魔分离/自动充电开关按钮：leftButtonSidebar.addButton() 自动定位后，反射调用父类
 * Screen.addRenderableWidget() 注册（Mixin 0.8.5 不支持 @Shadow 继承方法，直接 shadow 会使整个 mixin 失效）。
 * 另注入工作站模式切换按钮与 Superb Warfare 弹药面板（显示网络弹药余量并支持点击取出）。
 */
@Mixin(targets = "com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI", remap = false)
public class DimensionsNetGUIMixin {
    // 0.7.30+ 左侧按钮栏：addButton 自动排列按钮坐标（按钮仍需 addRenderableWidget 注册渲染/点击）
    @Shadow(remap = false) private LeftButtonSidebar leftButtonSidebar;
    /** BD 可容纳的最大行数（按当前屏幕高度计算） */
    @Shadow(remap = false) protected int calMaxLines() { return 0; }
    /** BD GUI 图像高度（按当前行数计算） */
    @Shadow(remap = false) protected int rebuildImageHeight() { return 0; }
    /** 是否处于"行数已达当前屏幕最大值"状态（屏幕调整后继续跟随最大值） */
    @Unique private boolean beyond$atMaxLines = false;
    /** 上一次 init 时的屏幕尺寸（用于区分"窗口调整"与"加/减页触发的 init"） */
    @Unique private int beyond$lastInitW = -1;
    @Unique private int beyond$lastInitH = -1;
    // 反射缓存的父类 Screen#addRenderableWidget：Mixin 0.8.5 不支持 @Shadow 继承方法，故运行时反射注册
    @Unique private static java.lang.reflect.Method beyond$addWidgetMethod;

    // 把侧栏按钮注册进原生 widget 体系（渲染/点击/tooltip）；反射失败时静默跳过
    @Unique
    private void beyond$addWidget(net.minecraft.client.gui.components.events.GuiEventListener widget) {
        try {
            if (beyond$addWidgetMethod == null) {
                beyond$addWidgetMethod = net.minecraft.client.gui.screens.Screen.class.getDeclaredMethod(
                        "addRenderableWidget", net.minecraft.client.gui.components.events.GuiEventListener.class);
            }
            beyond$addWidgetMethod.setAccessible(true);
            beyond$addWidgetMethod.invoke(this, widget);
        } catch (Throwable ignored) {}
    }
    // 按钮贴图（普通/悬停态）
    @Unique private static final ResourceLocation BTN = ResourceLocation.parse("beyonddimensions:textures/gui/sprites/widget/slot_button.png");
    @Unique private static final ResourceLocation BH = ResourceLocation.parse("beyonddimensions:textures/gui/sprites/widget/slot_button_hovered.png");
    // 模式按钮禁用贴图（工作台未献祭激活时使用）
    @Unique private static final ResourceLocation BTN_DISABLED = ResourceLocation.parse("beyonddimensions:textures/gui/sprites/widget/slot_button_disabled.png");
    // 弹药面板的物品 ID 与对应弹药键名（与 Superb Warfare 的五种弹药对应）
    @Unique private static final String[] AMMO_ITEMS = {
        "superbwarfare:handgun_ammo", "superbwarfare:rifle_ammo", "superbwarfare:shotgun_ammo",
        "superbwarfare:sniper_ammo", "superbwarfare:heavy_ammo"
    };
    @Unique private static final String[] AMMO_NAMES = {
        "HandgunAmmo", "RifleAmmo", "ShotgunAmmo", "SniperAmmo", "HeavyAmmo"
    };
    // 弹药面板槽位尺寸与间距
    @Unique private static final int SLOT_SIZE = 18;
    @Unique private static final int SLOT_GAP = 2;
    // 当前鼠标悬停的弹药槽位（-1 表示无）
    @Unique private int beyond$hoveredSlot = -1;
    // 附魔分离开关按钮及其上次渲染状态（用于检测变化后刷新提示文本）
    @Unique private EnchantToggleBtn beyond$enchantBtn;
    @Unique private boolean beyond$lastEnchantState = true;
    // 自动充电开关按钮及其上次渲染状态（用于检测变化后刷新提示文本）
    @Unique private EnergyChargeToggleBtn beyond$energyBtn;
    @Unique private boolean beyond$lastEnergyState = true;
    /** 已屏蔽的 RI 原生按钮（EAT/SELECT/MODE/MENU）：仅保留在界面中让 RI 的 existing 检测命中，隐藏且不可点 */
    @Unique private final java.util.List<net.minecraft.client.gui.components.Button> beyond$rsButtons = new java.util.ArrayList<>();
    /** 本模组接管的按钮（排后组，onInit 收集；用于接管布局重排） */
    @Unique private final java.util.List<AbstractButton> beyond$biButtons = new java.util.ArrayList<>();

    /** 屏幕调整时（init HEAD）：若此前行数已达屏幕最大值，则跟随新屏幕最大值（加/减页触发的 init 不受影响） */
    @Inject(method = "init", at = @At("HEAD"))
    private void beyond$followMaxLinesOnResize(CallbackInfo ci) {
        var self = (DimensionsNetGUI<?>) (Object) this;
        boolean resized = beyond$lastInitW > 0 && (beyond$lastInitW != self.width || beyond$lastInitH != self.height);
        beyond$lastInitW = self.width;
        beyond$lastInitH = self.height;
        if (!resized || !beyond$atMaxLines) return;
        int newMax = Math.max(2, Math.min(99, this.calMaxLines()));
        var menu = self.getMenu();
        if (menu.getLines() != newMax) {
            menu.setLines(newMax);
            menu.rebuildSlots();
        }
    }

    /** 初始化完成时（init RETURN）：记录是否处于"不能再加行"的最大值状态 */
    @Inject(method = "init", at = @At("RETURN"))
    private void beyond$trackMaxLinesState(CallbackInfo ci) {
        var self = (DimensionsNetGUI<?>) (Object) this;
        beyond$atMaxLines = self.getMenu().getLines() >= 99
                || self.height - 36 <= this.rebuildImageHeight() + 18;
    }

    // 初始化末尾：把附魔按钮挂到左侧按钮栏（自动定位，追加在作者按钮之后），并请求网络弹药状态
    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        var self = (DimensionsNetGUI<?>) (Object) this;
        beyond$biButtons.clear();

        beyond$enchantBtn = new EnchantToggleBtn(0, 0, btn -> {
            boolean next = !SuperbAmmoCache.INSTANCE.getEnchantSeparation();
            SuperbAmmoCache.INSTANCE.setEnchantSeparation(next);
            PacketDistributor.sendToServer(new ToggleEnchantSeparationPacket());
        });
        beyond$enchantBtn.updateTooltip();
        beyond$lastEnchantState = SuperbAmmoCache.INSTANCE.getEnchantSeparation();

        // 自动充电开关按钮：本地乐观切换 + 通知服务端（网络级开关，需经理/所有者权限）
        beyond$energyBtn = new EnergyChargeToggleBtn(0, 0, btn -> {
            boolean next = !SuperbAmmoCache.INSTANCE.getEnergyCharge();
            SuperbAmmoCache.INSTANCE.setEnergyCharge(next);
            PacketDistributor.sendToServer(new ToggleEnergyChargePacket());
        });
        beyond$energyBtn.updateTooltip();
        beyond$lastEnergyState = SuperbAmmoCache.INSTANCE.getEnergyCharge();

        // 本模组开关按钮：统一加入 BD 左侧按钮栏（自动排列）
        beyond$addWidget(leftButtonSidebar.addButton(beyond$enchantBtn));
        beyond$addWidget(leftButtonSidebar.addButton(beyond$energyBtn));
        beyond$biButtons.add(beyond$enchantBtn);
        beyond$biButtons.add(beyond$energyBtn);

        // 主动兼容 rs_integration（Forge 侧模组，Neo 环境通常不存在）：
        // 其自身的按钮安装/定位已被 RsIntegrationAutoEatMixin 屏蔽，这里由本模组调用其按钮创建入口，
        // 但原生 EAT/SELECT/MODE/MENU 按钮全部屏蔽：仅注册进界面（让 RI 的 existing 检测命中，避免其每帧重建），
        // 不加入侧栏布局、不参与接管排序；渲染隐藏 + 点击吞掉，功能由下方本模组的 RsAutoEatButton 替代
        beyond$rsButtons.clear();
        com.solr98.beyondintegration.compat.RsIntegrationCompat.installControls(self, button -> {
            com.solr98.beyondintegration.compat.RsIntegrationCompat.manage(button);
            beyond$rsButtons.add(button);
            beyond$addWidget(button);
        });

        // RI 的“机器中心 / 维度共振盘”在 BD 左侧栏为手动渲染按钮（Forge 侧由 RsIntegrationMachineHubMixin 屏蔽），
        // 这里以 BD 侧栏按钮形式接管；Neo 环境通常不加载 RI，isLoaded() 为 false 时跳过
        if (com.solr98.beyondintegration.compat.RsIntegrationCompat.isLoaded()) {
            // RI 原生自动进食按钮已屏蔽，这里以 BD 侧栏按钮替代其功能（进食/选择/模式切换）
            for (var role : com.solr98.beyondintegration.client.widget.RsAutoEatButton.Role.values()) {
                var autoEatBtn = new com.solr98.beyondintegration.client.widget.RsAutoEatButton(role, 0, 0);
                beyond$addWidget(leftButtonSidebar.addButton(autoEatBtn));
                beyond$biButtons.add(autoEatBtn);
            }

            var machineCenterBtn = new com.wintercogs.beyonddimensions.client.gui.widget.shared.IconButton(
                    0, 0, 16, 16,
                    ResourceLocation.tryParse("rs_integration:textures/gui/machine_center_bd_icon_16x16.png"),
                    b -> com.solr98.beyondintegration.compat.RsIntegrationCompat.toggleMachineCenter());
            machineCenterBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable("gui.beyond_integration.rs_machine_center")));
            beyond$addWidget(leftButtonSidebar.addButton(machineCenterBtn));
            beyond$biButtons.add(machineCenterBtn);

            var resonanceBtn = new com.wintercogs.beyonddimensions.client.gui.widget.shared.IconButton(
                    0, 0, 16, 16,
                    ResourceLocation.tryParse("rs_integration:textures/gui/resonance_backpack_bd_icon_16x16.png"),
                    b -> com.solr98.beyondintegration.compat.RsIntegrationCompat.toggleResonanceBackpack());
            resonanceBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable("gui.beyond_integration.rs_resonance_backpack")));
            beyond$addWidget(leftButtonSidebar.addButton(resonanceBtn));
            beyond$biButtons.add(resonanceBtn);
        }

        // 接管左侧栏布局：BD 原有按钮始终在最前（保持原序），本模组按钮排后；超出 GUI 底部的按钮移到左侧新增列
        beyond$applySidebarLayout();

        if (net.neoforged.fml.ModList.get().isLoaded("superbwarfare"))
            PacketDistributor.sendToServer(new RequestSuperbAmmoStatusPacket(-1));
        // 附魔分离状态请求：独立于 SW，仅 BD+BI 时按钮也能正确回显
        PacketDistributor.sendToServer(new RequestEnchantSeparationPacket());
        // 自动充电状态请求：独立于 SW，仅 BD+BI 时按钮也能正确回显
        PacketDistributor.sendToServer(new RequestEnergyChargePacket());
        // 工作台献祭激活状态请求（可选平衡项）：供工作站模式按钮显示锁定状态
        PacketHandler.sendToServer(new RequestWorkstationActivationPayload());
    }

    /** 渲染前：屏蔽 RI 原生按钮，并每帧重排左侧栏（防止 RI 在渲染后事件中把它们设回可见/固定坐标） */
    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(GuiGraphics g, int mx, int my, float pt, CallbackInfo ci) {
        beyond$hideNativeRsButtons();
        beyond$applySidebarLayout();
    }

    /** 重新应用左侧栏接管布局（幂等；每帧渲染前调用，确保外部模组的坐标覆盖不生效） */
    @Unique
    private void beyond$applySidebarLayout() {
        var self = (DimensionsNetGUI<?>) (Object) this;
        LeftSidebarLayout.apply(leftButtonSidebar, beyond$biButtons, self.getGuiTop() + self.getYSize());
    }

    /** 屏蔽 RI 原生按钮：每帧渲染前强制隐藏并禁用（RI 会在渲染后事件中把它们设回可见/可点） */
    @Unique
    private void beyond$hideNativeRsButtons() {
        for (net.minecraft.client.gui.components.Button rsBtn : beyond$rsButtons) {
            if (rsBtn.visible) rsBtn.visible = false;
            if (rsBtn.active) rsBtn.active = false;
        }
    }

    // 渲染：绘制工作站模式按钮及弹药面板；开关按钮由 widget 体系渲染，此处仅刷新 tooltip 状态
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics g, int mx, int my, float pt, CallbackInfo ci) {
        var self = (DimensionsNetGUI<?>) (Object) this;
        var mc = Minecraft.getInstance();
        var f = mc.font;

        beyond$hoveredSlot = -1;

        // 开关按钮状态刷新：悬停 tooltip 文本随网络开关状态变化（渲染/点击由 widget 体系负责）
        if (beyond$enchantBtn != null) {
            boolean cur = SuperbAmmoCache.INSTANCE.getEnchantSeparation();
            if (cur != beyond$lastEnchantState) {
                beyond$lastEnchantState = cur;
                beyond$enchantBtn.updateTooltip();
            }
        }
        if (beyond$energyBtn != null) {
            boolean cur = SuperbAmmoCache.INSTANCE.getEnergyCharge();
            if (cur != beyond$lastEnergyState) {
                beyond$lastEnergyState = cur;
                beyond$energyBtn.updateTooltip();
            }
        }

        // 工作站模式按钮（统一由 mixin 绘制：BI 工作站界面/BD 合成终端/纯存储）
        // 顺序/可见集取客户端配置（workstationOrder），位置按索引沿用原序列间距保持紧凑
        var menu = (DimensionsNetMenu) self.getMenu();
        int lx = self.getGuiLeft();
        int gy = self.getGuiTop() + 24 + 18 + (menu.getLines() - 2) * 18 + 26;
        List<Component> modeTooltip = null;
        // 服务端禁用的工作台已在 availableModes 中剔除（紧凑排列，不留空位）
        var cfgModes = WorkstationModeConstants.availableModes();
        for (int i = 0; i < cfgModes.size(); i++) {
            var mode = cfgModes.get(i);
            int bx = lx + WorkstationModeConstants.xFor(i), by = gy + WorkstationModeConstants.yFor(i);
            boolean on = beyond$isCurrentMode(menu.getClass(), mode);
            boolean h = mx >= bx && mx < bx + 16 && my >= by && my < by + 16;
            // 未献祭激活的工作台：禁用纹理 + 锁定 tooltip（点击将尝试献祭激活而非打开）
            boolean locked = WorkstationActivationCache.isLocked(mode.name().toLowerCase());
            g.blit(locked ? BTN_DISABLED : (h || on ? BH : BTN), bx, by, 0, 0, 16, 16, 16, 16);
            var p = g.pose(); p.pushPose(); p.translate(bx + 1, by + 1, 1); p.scale(0.85f, 0.85f, 1);
            g.renderFakeItem(WorkstationModeConstants.iconFor(mode), 0, 0); p.popPose();
            // 仅记录第一个命中的 tooltip，统一在全部背景绘制后渲染（避免被后续按钮/面板背景覆盖）
            if (h && modeTooltip == null) {
                if (locked) {
                    // 多行 Component 列表（不用 \n，避免部分渲染上下文把换行识别为未知字符）
                    modeTooltip = List.of(
                            Component.translatable("gui.beyond_integration.workstation.locked.title",
                                    Component.translatable("gui.beyond_integration.mode." + mode.name().toLowerCase())),
                            Component.translatable("gui.beyond_integration.workstation.locked.cost",
                                    WorkstationActivation.costName(mode.name().toLowerCase())),
                            Component.translatable("gui.beyond_integration.workstation.locked.hint"));
                } else {
                    modeTooltip = List.of(Component.translatable("gui.beyond_integration.mode." + mode.name().toLowerCase()));
                }
            }
        }

        // 弹药面板（数据不可用时仅跳过面板，模式按钮 tooltip 仍需渲染）
        boolean showAmmo = net.neoforged.fml.ModList.get().isLoaded("superbwarfare")
                && SuperbAmmoCache.INSTANCE.hasData() && SuperbAmmoCache.INSTANCE.getNetId() >= 0;
        boolean infinite = showAmmo && SuperbAmmoCache.INSTANCE.hasInfinite();
        if (showAmmo) {
            int panelX = self.getGuiLeft() + self.getXSize() + 4;
            int panelY = self.getGuiTop() + 8;

            for (int i = 0; i < 5; i++) {
                int sx = panelX;
                int sy = panelY + i * (SLOT_SIZE + SLOT_GAP);
                boolean hover = mx >= sx && mx < sx + SLOT_SIZE && my >= sy && my < sy + SLOT_SIZE;
                if (hover) beyond$hoveredSlot = i;

                g.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF8B8B8B);
                g.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, 0xFF373737);
                if (hover) g.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, 0x80FFFFFF);

                var ammoItem = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(AMMO_ITEMS[i]));
                if (ammoItem != null) g.renderFakeItem(new ItemStack(ammoItem), sx + 1, sy + 1);

                long count = SuperbAmmoCache.INSTANCE.getCount(AMMO_NAMES[i]);
                var overlay = infinite ? "\u221E" : count == 0 ? "0" : compactFormat(count);
                int overlayColor = infinite ? 0xFFAA00 : count == 0 ? 0x555555 : 0xFFFFFF;
                var pose = g.pose();
                pose.pushPose();
                pose.translate(0, 0, 300);
                float scale = 0.666f;
                pose.scale(scale, scale, scale);
                int textX = (int)((sx + 19 - f.width(overlay) * scale) / scale);
                int textY = (int)((sy + 12) / scale);
                g.drawString(f, overlay, textX, textY, overlayColor);
                pose.popPose();
            }
        }

        // tooltip 统一最后渲染：保证悬浮框渲染在所有按钮/面板背景之上
        if (modeTooltip != null) {
            g.renderComponentTooltip(f, modeTooltip, mx, my);
        }
        if (showAmmo && beyond$hoveredSlot >= 0) {
            String ammoName = AMMO_NAMES[beyond$hoveredSlot];
            long count = SuperbAmmoCache.INSTANCE.getCount(ammoName);
            var ammoItem = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(AMMO_ITEMS[beyond$hoveredSlot]));
            List<Component> tooltip = new ArrayList<>();
            if (ammoItem != null) tooltip.add(Component.translatable(ammoItem.getDescriptionId()));
            else tooltip.add(Component.literal(ammoName));
            if (infinite) tooltip.add(Component.literal("\u221E").withStyle(ChatFormatting.GOLD));
            else tooltip.add(Component.literal(NumberFormat.getIntegerInstance().format(count)).withStyle(ChatFormatting.WHITE));
            if (ammoItem != null) g.renderTooltip(f, tooltip, new ItemStack(ammoItem).getTooltipImage(), new ItemStack(ammoItem), mx, my);
        }
    }

    // 鼠标点击：开关按钮由 widget 体系处理；此处处理工作站模式按钮与弹药面板
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mx, double my, int button, CallbackInfoReturnable<Boolean> cir) {
        var self = (DimensionsNetGUI<?>) (Object) this;

        // 屏蔽的 RI 原生按钮：命中其坐标直接吞掉（RI 每帧会把它们设回固定位置，避免点击触发原功能）
        for (net.minecraft.client.gui.components.Button rsBtn : beyond$rsButtons) {
            if (mx >= rsBtn.getX() && mx < rsBtn.getX() + rsBtn.getWidth()
                    && my >= rsBtn.getY() && my < rsBtn.getY() + rsBtn.getHeight()) {
                cir.setReturnValue(true);
                return;
            }
        }

        // 扩展驱动：优先处理扩展点击（如 Ctrl+右键 附魔分离保护切换）
        for (var ext : com.solr98.beyondintegration.client.gui.extension.BDGUIExtensionRegistry.getExtensions()) {
            if (ext.onMouseClicked(self, mx, my, button)) {
                cir.setReturnValue(true);
                return;
            }
        }

        // 工作站模式按钮点击（统一由 mixin 处理；顺序/可见集取客户端配置 ∩ 服务端可用列表）
        var menu = (DimensionsNetMenu) self.getMenu();
        int lx = self.getGuiLeft();
        int gy = self.getGuiTop() + 24 + 18 + (menu.getLines() - 2) * 18 + 26;
        var cfgModes = WorkstationModeConstants.availableModes();
        for (int i = 0; i < cfgModes.size(); i++) {
            int bx = lx + WorkstationModeConstants.xFor(i), by = gy + WorkstationModeConstants.yFor(i);
            if (mx >= bx && mx < bx + 16 && my >= by && my < by + 16) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                String wsId = cfgModes.get(i).name().toLowerCase();
                if (WorkstationActivationCache.isLocked(wsId)) {
                    // 未献祭激活：发送激活请求（从网络扣除献祭物品），不打开工作台
                    PacketHandler.sendToServer(new ActivateWorkstationPayload(wsId));
                } else {
                    com.solr98.beyondintegration.client.gui.WorkstationTransferHelper.save(menu);
                    PacketHandler.sendToServer(new OpenStorageMenuPayload(cfgModes.get(i),
                            com.solr98.beyondintegration.ClientConfig.enchantTableApothMode()));
                }
                cir.setReturnValue(true);
                return;
            }
        }

        // 弹药面板点击
        if (!net.neoforged.fml.ModList.get().isLoaded("superbwarfare")) return;
        if (!SuperbAmmoCache.INSTANCE.hasData()) return;
        if (SuperbAmmoCache.INSTANCE.getNetId() < 0) return;

        int panelX = self.getGuiLeft() + self.getXSize() + 4;
        int panelY = self.getGuiTop() + 8;
        int hitSlot = -1;
        for (int i = 0; i < 5; i++) {
            int sx = panelX;
            int sy = panelY + i * (SLOT_SIZE + SLOT_GAP);
            if (mx >= sx && mx < sx + SLOT_SIZE && my >= sy && my < sy + SLOT_SIZE) { hitSlot = i; break; }
        }
        if (hitSlot < 0) return;

        String ammoName = AMMO_NAMES[hitSlot];
        boolean infinite = SuperbAmmoCache.INSTANCE.hasInfinite();
        long count = infinite ? Long.MAX_VALUE : SuperbAmmoCache.INSTANCE.getCount(ammoName);
        if (count <= 0) return;

        cir.setReturnValue(true);
        long toExtract = 64;
        if (beyond$hasShiftDown()) toExtract = 256;
        PacketDistributor.sendToServer(new RequestSuperbAmmoExtractPacket(ammoName, Math.min(toExtract, count)));
    }

    // 判断当前菜单是否为 Shift 按下状态（用于批量取出弹药）
    @Unique
    private boolean beyond$hasShiftDown() {
        try {
            var self = (DimensionsNetGUI<?>) (Object) this;
            var menu = self.getMenu();
            if (menu instanceof DimensionsNetMenu) return ((DimensionsNetMenu) menu).hasShiftDown;
        } catch (Exception ignored) {}
        return false;
    }

    // 判断当前打开的工作台菜单类型是否对应指定的工作站模式
    @Unique
    private static boolean beyond$isCurrentMode(Class<?> menuClass, WorkstationModeConstants.Mode mode) {
        return switch (mode) {
            case ANVIL -> menuClass == com.solr98.beyondintegration.init.DimensionsAnvilMenu.class;
            case CUT -> menuClass == com.solr98.beyondintegration.init.DimensionsCutMenu.class;
            case GRIND -> menuClass == com.solr98.beyondintegration.init.DimensionsGrindMenu.class;
            case SMITH -> menuClass == com.solr98.beyondintegration.init.DimensionsSmithMenu.class;
            case CRAFT -> menuClass == com.solr98.beyondintegration.init.DimensionsCraftMenu.class;
            case ENCHANT -> com.solr98.beyondintegration.init.DimensionsEnchantMenu.class.isAssignableFrom(menuClass); // 覆盖原版/神化两模式菜单
            default -> false;
        };
    }

    // 数字缩写显示：10K / 1.2M / 3.4B
    @Unique
    private static String compactFormat(long value) {
        if (value >= 1_000_000_000L) return String.format("%.1fB", value / 1_000_000_000.0);
        if (value >= 1_000_000L)     return String.format("%.1fM", value / 1_000_000.0);
        if (value >= 1_000L)         return String.format("%.1fK", value / 1_000.0);
        return String.valueOf(value);
    }
}