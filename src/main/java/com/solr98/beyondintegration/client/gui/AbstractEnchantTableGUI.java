package com.solr98.beyondintegration.client.gui;

import com.solr98.beyondintegration.client.widget.EnchantModeBtn;
import com.solr98.beyondintegration.init.DimensionsEnchantMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EnchantmentNames;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.List;
import java.util.Optional;

/**
 * 附魔台工作站界面抽象基类（原版 {@link DimensionsEnchantGUI} / 神化 {@link DimensionsEnchantApothGUI} 共用）：
 * 面板底图 + 标题 + 3D 书本动画 + 三个费用按钮（贴图三态/随机谜名/费用数字/等级图标）+
 * 按钮点击发包、书本 tick 动画、模式切换按钮（切往另一模式菜单，偏好存客户端配置）。
 * 两模式差异（tooltip 内容 / 神化三属性条 / 候选符石）由子类实现。
 */
public abstract class AbstractEnchantTableGUI extends DimensionsStorageGUI<DimensionsEnchantMenu> {
    // 面板底图：自定义剪切贴图（原版 enchanting_table.png 自 (0,4) 起剪 176x76）
    protected static final ResourceLocation ENCHANT_TEX = ResourceLocation.parse("beyond_integration:textures/gui/enchant.png");
    // 按钮贴图：108x57 竖排三态（行0=普通，行1=禁用，行2=高亮），每行 108x19
    protected static final ResourceLocation BTN_TEX = ResourceLocation.parse("beyond_integration:textures/gui/enchant_button.png");
    protected static final int BTN_ROW_NORMAL = 0, BTN_ROW_DISABLED = 19, BTN_ROW_HIGHLIGHTED = 38;
        // 等级图标贴图：50x32（3 列 x 2 行），上排启用/下排禁用，每格 16x16（水平间距 1px）
        // 取图 u = 槽位 * 17 - 槽位（第 2/3 个按钮图标分别左移 1/2px 对齐视觉）
        protected static final ResourceLocation LVL_TEX = ResourceLocation.parse("beyond_integration:textures/gui/enchant_exp_level.png");
    protected static final int LVL_ROW_ENABLED = 0, LVL_ROW_DISABLED = 16;
    // 3D 书本模型贴图
    protected static final ResourceLocation BOOK_TEX = ResourceLocation.parse("minecraft:textures/entity/enchanting_table_book.png");

    // 书本锚点（相对面板顶 getGapY 的 Y 偏移）：原版 31 - 贴图裁剪上移 4px - 渲染对齐修正 2px
    protected static final int BOOK_ANCHOR_Y_OFFSET = 25;

    // ---- copy 原版 EnchantmentScreen 书本动画状态 ----
    private final RandomSource random = RandomSource.create();
    private BookModel bookModel;
    private float lastPartialTick; // 最近一次渲染的部分帧时间（renderBg 阶段取用）
    public int time;
    public float flip, oFlip, flipT, flipA, open, oOpen;
    private ItemStack last = ItemStack.EMPTY;
    // 模式切换按钮（神化功能暂停时停用；字段保留便于后续恢复）
    @SuppressWarnings("unused")
    private com.solr98.beyondintegration.client.widget.EnchantModeBtn modeBtn;
    // 刷新按钮（可用性由服务端开关 DataSlot 控制；预览开关改为客户端配置，不再提供按钮）
    private com.solr98.beyondintegration.client.widget.EnchantIconBtn refreshBtn;
    // 服务端开关镜像（DataSlot 到达前为 false，containerTick 同步后控制按钮显隐）
    private boolean lastServerPreview = false;
    private boolean lastServerRefresh = false;
    // 三槽"将获得"完整列表缓存（服务端 EnchantCluesPayload；供悬停预览/符石显示）
    private final java.util.Map<Integer, java.util.List<EnchantmentInstance>> previewCache = new java.util.HashMap<>();

    /** 接收服务端单槽将获得列表（预览/符石数据） */
    public void acceptClues(int slot, java.util.List<EnchantmentInstance> clues, boolean all) {
        if (slot >= 0 && slot < 3) {
            previewCache.put(slot, clues == null ? List.of() : clues);
        }
    }

    /** 读取单槽将获得列表（未下发为空） */
    protected java.util.List<EnchantmentInstance> previewList(int slot) {
        return previewCache.getOrDefault(slot, List.of());
    }

    /**
     * 预览条目样式：物品上已存在且本次将升级（结果等级更高）的附魔，追加金色升级标记。
     * 供原版/神化两套 tooltip 复用。
     */
    protected net.minecraft.network.chat.Component stylePreviewEntry(EnchantmentInstance inst) {
        net.minecraft.network.chat.Component name = net.minecraft.world.item.enchantment.Enchantment.getFullname(inst.enchantment, inst.level);
        ItemStack input = this.menu.getItemInput();
        if (!input.isEmpty()) {
            int cur = net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantmentsForCrafting(input).getLevel(inst.enchantment);
            if (cur > 0 && inst.level > cur) {
                name = name.copy().append(Component.translatable("gui.beyond_integration.enchant.preview.upgrade", cur)
                        .withStyle(ChatFormatting.GOLD));
            }
        }
        return name;
    }

    protected AbstractEnchantTableGUI(DimensionsEnchantMenu c, Inventory p, Component t) { super(c, p, t); }

    /** 当前 GUI 是否为神化模式界面（子类固定） */
    protected abstract boolean isApothGUI();

    @Override protected int getPanelHeight() { return 76; } // 与菜单一致：槽(41) + 三按钮区(12..69) 底部留白

    // 按钮区域相对面板起点（原版 y=14，贴图整体上移 4px 后取 10；实测再下移 2px 对齐贴图按钮底 → 12+19k；
    // 再向上微调 1px → 11+19k；继续向上微调 1px → 10+19k）
    protected int btnX() { return this.leftPos + 60; }
    protected int btnY(int k) { return getGapY() + 10 + 19 * k; }

    // copy 原版 EnchantmentScreen.init：烘焙 3D 书本模型
    // 侧栏功能按钮：预览开关 + 刷新（可见性由服务端开关 DataSlot 控制）
    @Override protected void init() {
        super.init();
        this.bookModel = new BookModel(this.minecraft.getEntityModels().bakeLayer(ModelLayers.BOOK));

        // TODO 神化附魔工作台开发中，暂时停用模式切换按钮（恢复神化时取消注释）
        // if (net.neoforged.fml.ModList.get().isLoaded("apothic_enchanting")) {
        //     if (modeBtn == null) {
        //         modeBtn = new EnchantModeBtn(0, 0, this::isApothGUI, b -> {
        //             boolean next = !isApothGUI(); // 切往另一模式：偏好存客户端配置，并重开对应模式菜单
        //             com.solr98.beyondintegration.ClientConfig.setEnchantTableApothMode(next);
        //             if (this.menu instanceof com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu netMenu) {
        //                 com.solr98.beyondintegration.client.gui.WorkstationTransferHelper.save(netMenu); // 保留翻页上下文
        //             }
        //             com.solr98.beyondintegration.network.PacketHandler.sendToServer(
        //                     new com.solr98.beyondintegration.network.payload.OpenStorageMenuPayload(
        //                             com.solr98.beyondintegration.client.gui.WorkstationModeConstants.Mode.ENCHANT, next));
        //             modeBtn.updateTooltip();
        //         });
        //     }
        //     this.leftButtonSidebar.addButton(modeBtn);
        //     this.addRenderableWidget(modeBtn);
        //     modeBtn.updateTooltip();
        // }

        // 预览开关改为客户端配置（beyond_integration-client.toml / Cloth Config），不再提供界面按钮
        if (refreshBtn == null) {
            // 客户端校验：物品槽为空时不发送刷新请求
            refreshBtn = new com.solr98.beyondintegration.client.widget.EnchantIconBtn(0, 0,
                    () -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CLOCK),
                    b -> {
                        if (!this.menu.getItemInput().isEmpty()) {
                            com.solr98.beyondintegration.network.PacketHandler.sendToServer(
                                    new com.solr98.beyondintegration.network.payload.RefreshEnchantPayload(this.menu.containerId));
                        }
                    });
        }
        // 刷新按钮置于工作台面板内：物品槽正下方（物品槽面板内 y=43~59，再下移 2px 留出间距）
        refreshBtn.setX(this.leftPos + 15);
        refreshBtn.setY(getGapY() + 61);
        this.addRenderableWidget(refreshBtn);
        syncButtonVisibility();
        syncSideBtnTooltips();
    }

    // 每 tick：书本动画 + 侧栏按钮显隐/tooltip 随服务端开关同步
    @Override public void containerTick() {
        super.containerTick();
        this.tickBook();
        syncButtonVisibility();
        syncSideBtnTooltips();
    }

    /** 刷新按钮可见性 = 服务端开关（DataSlot 镜像）且物品槽有待附魔物品（空槽隐藏） */
    private void syncButtonVisibility() {
        if (refreshBtn != null) {
            refreshBtn.visible = this.menu.serverRefreshEnabled && !this.menu.getItemInput().isEmpty();
        }
    }

    /** 刷新按钮悬停提示（刷新费用） */
    private void syncSideBtnTooltips() {
        if (refreshBtn != null) {
            int lapis = this.menu.serverRefreshLapis;
            refreshBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(lapis > 0
                    ? Component.translatable("gui.beyond_integration.enchant.refresh.tooltip", lapis)
                    : Component.translatable("gui.beyond_integration.enchant.refresh.tooltip.free")));
        }
    }

    // copy 原版 EnchantmentScreen.tickBook：物品变化触发翻页，有费用则开书，否则合书
    private void tickBook() {
        ItemStack itemstack = this.menu.getItemInput();
        if (!ItemStack.matches(itemstack, this.last)) {
            this.last = itemstack;
            onInputChanged(); // 子类钩子（神化模式清空旧候选符石）
            do {
                this.flipT += (float) (this.random.nextInt(4) - this.random.nextInt(4));
            } while (this.flip <= this.flipT + 1.0F && this.flip >= this.flipT - 1.0F);
        }
        this.time++;
        this.oFlip = this.flip;
        this.oOpen = this.open;
        boolean flag = false;
        for (int i = 0; i < 3; i++) {
            if (this.menu.costs[i] != 0) { flag = true; break; }
        }
        if (flag) {
            this.open += 0.2F;
        } else {
            this.open -= 0.2F;
        }
        this.open = Mth.clamp(this.open, 0.0F, 1.0F);
        float f1 = (this.flipT - this.flip) * 0.4F;
        f1 = Mth.clamp(f1, -0.2F, 0.2F);
        this.flipA += (f1 - this.flipA) * 0.9F;
        this.flip += this.flipA;
    }

    /** 输入槽物品变化钩子：不清空预览缓存——缓存以服务端下发为准（覆盖式更新）；
     *  取出/无效物品时服务端会下发空表，避免“放入即被清空后无新数据”的时序问题 */
    protected void onInputChanged() {
    }

    // 面板：底图 + 标题 + 3D 书 + 三个费用按钮（两模式共用）；子类可覆写并在其后追加专属内容
    @Override protected void renderWorkstationPanel(GuiGraphics g) {
        int gy = getGapY();
        g.blit(ENCHANT_TEX, this.leftPos, gy, 0, 0, 176, 76, 176, 76);
        g.drawString(Minecraft.getInstance().font, Component.translatable("gui.beyond_integration.workstation.enchant"), this.leftPos + 6, gy - 7, 0x404040, false);

        // 3D 书本动画（项目自有渲染封装）
        if (bookModel != null) {
            WorkstationRenderHelper.renderEnchantingBook(g, bookModel, BOOK_TEX,
                    this.leftPos + 33, getGapY() + BOOK_ANCHOR_Y_OFFSET,
                    lastPartialTick, oOpen, open, oFlip, flip);
        }

        var mc = Minecraft.getInstance();
        var font = mc.font;
        boolean creative = mc.player.getAbilities().instabuild;
        // copy 原版 renderBg：随机附魔名按 seed 解码，三按钮按费用/青金石/经验等级绘制状态
        EnchantmentNames.getInstance().initSeed((long) this.menu.getEnchantmentSeed());
        int gold = this.menu.getGoldCount();
        for (int l = 0; l < 3; l++) {
            int i1 = btnX();
            int j1 = i1 + 20;
            int k1 = this.menu.costs[l];
            if (k1 == 0) {
                g.blit(BTN_TEX, i1, btnY(l), 0, BTN_ROW_DISABLED, 108, 19, 108, 57);
            } else {
                String s = k1 + "";
                int l1 = 86 - font.width(s);
                FormattedText formattedtext = EnchantmentNames.getInstance().getRandomName(font, l1);
                int i2 = 6839882;
                // 禁用判定：等级门槛 exp >= costs（对齐原版与 Apoth）+ 青金石 + clue 有效；
                // 增强配置：enchantNoLapis 免燃料需求、enchantLevelGateIgnore 解除经验等级门槛
                boolean noLapis = !com.solr98.beyondintegration.CommandConfig.enchantNoLapis() && gold < l + 1 && !creative;
                boolean noExp = !com.solr98.beyondintegration.CommandConfig.enchantLevelGateIgnore() && mc.player.experienceLevel < k1;
                boolean disabled = ((noLapis || noExp) && !creative) || this.menu.enchantClue[l] == -1;
                if (disabled) {
                    g.blit(BTN_TEX, i1, btnY(l), 0, BTN_ROW_DISABLED, 108, 19, 108, 57);
                    g.blit(LVL_TEX, i1 + 1, btnY(l) + 1, l * 17 - l, LVL_ROW_DISABLED, 16, 16, 50, 32);
                    g.drawWordWrap(font, formattedtext, j1, btnY(l) + 2, l1, (i2 & 16711422) >> 1);
                    i2 = 4226832;
                } else {
                    int j2 = mouseX - i1;
                    int k2 = mouseY - btnY(l);
                    if (j2 >= 0 && k2 >= 0 && j2 < 108 && k2 < 19) {
                        g.blit(BTN_TEX, i1, btnY(l), 0, BTN_ROW_HIGHLIGHTED, 108, 19, 108, 57);
                        i2 = 16777088;
                    } else {
                        g.blit(BTN_TEX, i1, btnY(l), 0, BTN_ROW_NORMAL, 108, 19, 108, 57);
                    }
                    g.blit(LVL_TEX, i1 + 1, btnY(l) + 1, l * 17 - l, LVL_ROW_ENABLED, 16, 16, 50, 32);
                    g.drawWordWrap(font, formattedtext, j1, btnY(l) + 2, l1, i2);
                    i2 = 8453920;
                }
                g.drawString(font, s, j1 + 86 - font.width(s), btnY(l) + 7, i2);
            }
        }
    }

    // copy 原版 EnchantmentScreen.mouseClicked：三按钮区命中 → 本地校验 → 发包服务端执行
    @Override public boolean mouseClicked(double mx, double my, int button) {
        for (int k = 0; k < 3; k++) {
            double d0 = mx - btnX();
            double d1 = my - btnY(k);
            if (d0 >= 0.0 && d1 >= 0.0 && d0 < 108.0 && d1 < 19.0 && this.menu.clickMenuButton(Minecraft.getInstance().player, k)) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, k);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    // copy 原版 EnchantmentScreen.render：悬停费用按钮时构建各模式 tooltip（子类实现内容）并渲染
    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        this.lastPartialTick = pt; // 供 renderBg → renderWorkstationPanel 的 3D 书渲染使用
        super.render(g, mx, my, pt);
        this.renderTooltip(g, mx, my);
        var mc = Minecraft.getInstance();
        boolean creative = mc.player.getAbilities().instabuild;
        int gold = this.menu.getGoldCount();
        for (int j = 0; j < 3; j++) {
            int k = this.menu.costs[j];
            Optional<Holder.Reference<Enchantment>> optional = mc.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(this.menu.enchantClue[j]);
            int l = this.menu.levelClue[j];
            int i1 = j + 1;
            // 悬浮框命中：与按钮渲染/点击区 [btnX,btnX+108)x[btnY,btnY+19) 对齐，并按原版 isHovering 语义各扩展 1px
            if (overBtn(j, mx, my) && k > 0) {
                java.util.List<net.minecraft.network.chat.Component> list = new java.util.ArrayList<>();
                buildSlotTooltip(list, j, k, optional, l, i1, creative, gold);
                g.renderComponentTooltip(mc.font, list, mx, my);
                break;
            }
        }
    }

    /** 费用按钮悬浮命中判定（tooltip 触发框）：渲染/点击基准区 [btnX,btnX+108)x[btnY,btnY+19) 各扩展 1px（对齐原版 isHovering） */
    protected boolean overBtn(int slot, double mx, double my) {
        return mx >= btnX() - 1 && mx < btnX() + 109 && my >= btnY(slot) - 1 && my < btnY(slot) + 20;
    }

    /** 构建费用按钮悬停 tooltip（子类按模式实现） */
    protected abstract void buildSlotTooltip(List<Component> list, int slot, int cost,
                                             Optional<Holder.Reference<Enchantment>> clue, int clueLevel,
                                             int lapisNeed, boolean creative, int gold);
}