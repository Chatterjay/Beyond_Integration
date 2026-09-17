package com.solr98.beyondintegration.client.gui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.ClientConfig;
import com.solr98.beyondintegration.feature.crafting.DimensionsEnchantMenu;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.RefreshEnchantPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.EnchantmentNames;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 原版增强附魔台工作站界面（1.20.1，参考原版 EnchantmentScreen 移植）：
 * 面板底图 + 3D 书本动画 + 三个费用按钮（三态贴图/随机谜名/费用/等级图标）+ 悬停"将获得"预览（升级标记）；
 * 左侧栏：预览开关、刷新（重掷三槽）按钮；可用性受服务端开关（DataSlot）控制。
 * 神化模式不在 1.20.1 移植范围。
 */
public class DimensionsEnchantGUI extends DimensionsStorageGUI<DimensionsEnchantMenu> {
    private static final ResourceLocation ENCHANT_TEX = ResourceLocation.tryParse("beyond_integration:textures/gui/enchant.png");
    private static final ResourceLocation BTN_TEX = ResourceLocation.tryParse("beyond_integration:textures/gui/enchant_button.png");
    private static final ResourceLocation LVL_TEX = ResourceLocation.tryParse("beyond_integration:textures/gui/enchant_exp_level.png");
    private static final ResourceLocation BOOK_TEX = ResourceLocation.tryParse("minecraft:textures/entity/enchanting_table_book.png");
    private static final int BTN_ROW_NORMAL = 0, BTN_ROW_DISABLED = 19, BTN_ROW_HIGHLIGHTED = 38;
    private static final int LVL_ROW_ENABLED = 0, LVL_ROW_DISABLED = 16;
    private static final int BOOK_ANCHOR_Y_OFFSET = 25;
    private static final int ROW_H = 20;

    private final RandomSource random = RandomSource.create();
    private BookModel bookModel;
    private float lastPartialTick;
    private int time;
    private float flip, oFlip, flipT, flipA, open, oOpen;
    private ItemStack last = ItemStack.EMPTY;
    private SideBtn refreshBtn;
    // 三槽"将获得"缓存（服务端下发；覆盖式更新，无需主动清理）
    private final Map<Integer, List<EnchantmentInstance>> previewCache = new HashMap<>();

    public DimensionsEnchantGUI(DimensionsEnchantMenu c, Inventory p, Component t) { super(c, p, t); }

    public void acceptClues(int slot, List<EnchantmentInstance> clues) {
        if (slot >= 0 && slot < 3) previewCache.put(slot, clues == null ? List.of() : clues);
    }

    private List<EnchantmentInstance> previewList(int slot) { return previewCache.getOrDefault(slot, List.of()); }

    @Override protected int getPanelHeight() { return 76; }

    // 按钮区（与 1.21.1 校准一致：getGapY + 10 + 19k）
    private int btnX() { return this.leftPos + 60; }
    private int btnY(int k) { return getGapY() + 10 + 19 * k; }

    /** 费用按钮悬浮命中（tooltip 触发框，基准区各扩展 1px） */
    private boolean overBtn(int slot, double mx, double my) {
        return mx >= btnX() - 1 && mx < btnX() + 109 && my >= btnY(slot) - 1 && my < btnY(slot) + 20;
    }

    /** 预览条目样式：已有但将升级的附魔追加金色标记 */
    private Component stylePreviewEntry(EnchantmentInstance inst) {
        Component name = inst.enchantment.getFullname(inst.level);
        ItemStack input = this.menu.getItemInput();
        if (!input.isEmpty()) {
            int cur = EnchantmentHelper.getEnchantments(input).getOrDefault(inst.enchantment, 0);
            if (cur > 0 && inst.level > cur) {
                name = name.copy().append(Component.translatable("gui.beyond_integration.enchant.preview.upgrade", cur).withStyle(ChatFormatting.GOLD));
            }
        }
        return name;
    }

    @Override protected void init() {
        super.init();
        this.bookModel = new BookModel(this.minecraft.getEntityModels().bakeLayer(ModelLayers.BOOK));

        // 预览开关改为客户端配置（beyond_integration-client.toml / Cloth Config），不再提供界面按钮
        if (refreshBtn == null) {
            // 客户端校验：物品槽为空时不发送刷新请求
            refreshBtn = new SideBtn(() -> new ItemStack(Items.CLOCK),
                    b -> {
                        if (!this.menu.getItemInput().isEmpty()) {
                            PacketHandler.sendToServer(new RefreshEnchantPacket(this.menu.containerId));
                        }
                    });
        }
        // 刷新按钮置于工作台面板内：物品槽正下方（物品槽面板内 y=43~59，再下移 2px 留出间距）
        refreshBtn.setX(this.leftPos + 15);
        refreshBtn.setY(getGapY() + 61);
        this.addRenderableWidget(refreshBtn);
        syncSideButtons();
        syncSideTooltips();
    }

    @Override public void containerTick() {
        super.containerTick();
        tickBook();
        syncSideButtons();
        syncSideTooltips();
    }

    private void syncSideButtons() {
        // 物品槽为空时不显示刷新按钮（仅服务端开启且有待附魔物品时可用）
        if (refreshBtn != null) refreshBtn.visible = this.menu.serverRefreshEnabled && !this.menu.getItemInput().isEmpty();
    }

    private void syncSideTooltips() {
        if (refreshBtn != null) {
            int lapis = this.menu.serverRefreshLapis;
            refreshBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(lapis > 0
                    ? Component.translatable("gui.beyond_integration.enchant.refresh.tooltip", lapis)
                    : Component.translatable("gui.beyond_integration.enchant.refresh.tooltip.free")));
        }
    }

    private void tickBook() {
        ItemStack item = this.menu.getItemInput();
        if (!ItemStack.matches(item, this.last)) {
            this.last = item;
            do {
                this.flipT += (float) (this.random.nextInt(4) - this.random.nextInt(4));
            } while (this.flip <= this.flipT + 1.0F && this.flip >= this.flipT - 1.0F);
        }
        this.time++;
        this.oFlip = this.flip;
        this.oOpen = this.open;
        boolean flag = false;
        for (int i = 0; i < 3; i++) if (this.menu.costs[i] != 0) { flag = true; break; }
        this.open = Mth.clamp(this.open + (flag ? 0.2F : -0.2F), 0.0F, 1.0F);
        float f1 = Mth.clamp((this.flipT - this.flip) * 0.4F, -0.2F, 0.2F);
        this.flipA += (f1 - this.flipA) * 0.9F;
        this.flip += this.flipA;
    }

    @Override protected void renderWorkstationPanel(GuiGraphics g) {
        int gy = getGapY();
        g.blit(ENCHANT_TEX, this.leftPos, gy, 0, 0, 176, 76, 176, 76);
        g.drawString(Minecraft.getInstance().font, Component.translatable("gui.beyond_integration.workstation.enchant"), this.leftPos + 6, gy - 7, 0x404040, false);

        // 3D 书本动画
        if (bookModel != null) {
            float openNow = Mth.lerp(lastPartialTick, this.oOpen, this.open);
            float flipNow = Mth.lerp(lastPartialTick, this.oFlip, this.flip);
            Lighting.setupForEntityInInventory();
            PoseStack pose = g.pose();
            pose.pushPose();
            pose.translate(this.leftPos + 33.0F, gy + BOOK_ANCHOR_Y_OFFSET, 100.0F);
            pose.scale(-40.0F, 40.0F, 40.0F);
            pose.mulPose(Axis.XP.rotationDegrees(25.0F));
            pose.translate((1.0F - openNow) * 0.2F, (1.0F - openNow) * 0.1F, (1.0F - openNow) * 0.25F);
            pose.mulPose(Axis.YP.rotationDegrees(-(1.0F - openNow) * 90.0F - 90.0F));
            pose.mulPose(Axis.XP.rotationDegrees(180.0F));
            float f4 = Mth.clamp(Mth.frac(flipNow + 0.25F) * 1.6F - 0.3F, 0.0F, 1.0F);
            float f5 = Mth.clamp(Mth.frac(flipNow + 0.75F) * 1.6F - 0.3F, 0.0F, 1.0F);
            bookModel.setupAnim(0.0F, f4, f5, openNow);
            VertexConsumer vc = g.bufferSource().getBuffer(bookModel.renderType(BOOK_TEX));
            bookModel.renderToBuffer(pose, vc, 15728880, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
            g.flush();
            pose.popPose();
            Lighting.setupFor3DItems();
        }

        var font = Minecraft.getInstance().font;
        boolean creative = Minecraft.getInstance().player.getAbilities().instabuild;
        EnchantmentNames.getInstance().initSeed(this.menu.getEnchantmentSeed());
        int gold = this.menu.getGoldCount();
        for (int l = 0; l < 3; l++) {
            int x0 = btnX();
            int x1 = x0 + 20;
            int k1 = this.menu.costs[l];
            if (k1 == 0) {
                g.blit(BTN_TEX, x0, btnY(l), 0, BTN_ROW_DISABLED, 108, 19, 108, 57);
            } else {
                String s = k1 + "";
                int w = 86 - font.width(s);
                FormattedText name = EnchantmentNames.getInstance().getRandomName(font, w);
                int color = 6839882;
                boolean noLapis = !CommandConfig.enchantNoLapis() && gold < l + 1 && !creative;
                boolean noExp = !CommandConfig.enchantLevelGateIgnore() && Minecraft.getInstance().player.experienceLevel < k1;
                boolean disabled = ((noLapis || noExp) && !creative) || this.menu.enchantClue[l] == -1;
                if (disabled) {
                    g.blit(BTN_TEX, x0, btnY(l), 0, BTN_ROW_DISABLED, 108, 19, 108, 57);
                    g.blit(LVL_TEX, x0 + 1, btnY(l) + 1, l * 17 - l, LVL_ROW_DISABLED, 16, 16, 50, 32);
                    g.drawWordWrap(font, name, x1, btnY(l) + 2, w, (color & 16711422) >> 1);
                    color = 4226832;
                } else {
                    boolean hover = mouseX >= x0 && mouseX < x0 + 108 && mouseY >= btnY(l) && mouseY < btnY(l) + 19;
                    g.blit(BTN_TEX, x0, btnY(l), 0, hover ? BTN_ROW_HIGHLIGHTED : BTN_ROW_NORMAL, 108, 19, 108, 57);
                    g.blit(LVL_TEX, x0 + 1, btnY(l) + 1, l * 17 - l, LVL_ROW_ENABLED, 16, 16, 50, 32);
                    g.drawWordWrap(font, name, x1, btnY(l) + 2, w, color);
                    color = 8453920;
                }
                g.drawString(font, s, x1 + 86 - font.width(s), btnY(l) + 7, color);
            }
        }
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        for (int k = 0; k < 3; k++) {
            if (mx >= btnX() && mx < btnX() + 108 && my >= btnY(k) && my < btnY(k) + 19
                    && this.menu.clickMenuButton(Minecraft.getInstance().player, k)) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, k);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        this.lastPartialTick = pt;
        super.render(g, mx, my, pt);
        this.renderTooltip(g, mx, my);
        boolean creative = Minecraft.getInstance().player.getAbilities().instabuild;
        boolean preview = this.menu.serverPreviewEnabled && ClientConfig.enchantPreviewOn();
        int gold = this.menu.getGoldCount();
        for (int j = 0; j < 3; j++) {
            int k = this.menu.costs[j];
            if (!overBtn(j, mx, my) || k <= 0) continue;
            List<Component> list = new ArrayList<>();
            List<EnchantmentInstance> pv = preview ? previewList(j) : List.of();
            if (preview && !pv.isEmpty()) {
                list.add(Component.translatable("gui.beyond_integration.enchant.preview").withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE));
                int shown = Math.min(pv.size(), 6);
                for (int i = 0; i < shown; i++) list.add(stylePreviewEntry(pv.get(i)));
                if (pv.size() > shown) {
                    list.add(Component.translatable("gui.beyond_integration.enchant.preview.more", pv.size() - shown).withStyle(ChatFormatting.GRAY));
                }
            } else {
                int clue = this.menu.enchantClue[j];
                Enchantment ench = clue >= 0 ? BuiltInRegistries.ENCHANTMENT.byId(clue) : null;
                list.add(Component.translatable("container.enchant.clue",
                        ench == null ? "" : ench.getFullname(this.menu.levelClue[j])).withStyle(ChatFormatting.WHITE));
                if (ench == null) {
                    list.add(Component.literal(""));
                    list.add(Component.translatable("neoforge.container.enchant.limitedEnchantability").withStyle(ChatFormatting.RED));
                }
            }
            if (!creative && !(!preview && this.menu.enchantClue[j] < 0)) {
                list.add(CommonComponents.EMPTY);
                int lapisNeed = j + 1;
                if (!CommandConfig.enchantLevelGateIgnore() && Minecraft.getInstance().player.experienceLevel < k) {
                    list.add(Component.translatable("container.enchant.level.requirement", this.menu.costs[j]).withStyle(ChatFormatting.RED));
                } else {
                    MutableComponent lapis = lapisNeed == 1
                            ? Component.translatable("container.enchant.lapis.one")
                            : Component.translatable("container.enchant.lapis.many", lapisNeed);
                    list.add(lapis.withStyle(gold >= lapisNeed ? ChatFormatting.GRAY : ChatFormatting.RED));
                    if (CommandConfig.enchantLevelGateIgnore()) {
                        list.add(Component.translatable("gui.beyond_integration.enchant.gate_ignored").withStyle(ChatFormatting.DARK_GRAY));
                    } else {
                        MutableComponent lvl = lapisNeed == 1
                                ? Component.translatable("container.enchant.level.one")
                                : Component.translatable("container.enchant.level.many", lapisNeed);
                        list.add(lvl.withStyle(ChatFormatting.GRAY));
                    }
                }
            }
            g.renderComponentTooltip(Minecraft.getInstance().font, list, mx, my);
            break;
        }
    }

    // 侧栏 16x16 图标按钮（slot_button 纹理 + 物品图标，悬停亮显）
    private class SideBtn extends Button {
        private final java.util.function.Supplier<ItemStack> icon;

        SideBtn(java.util.function.Supplier<ItemStack> icon, OnPress onPress) {
            super(0, 0, 16, 16, Component.empty(), onPress, DEFAULT_NARRATION);
            this.icon = icon;
        }

        @Override public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            g.blit(ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/slot_button.png"), getX(), getY(), 0, 0, 16, 16, 16, 16);
            if (this.isHovered) {
                g.blit(ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/slot_button_hovered.png"), getX(), getY(), 0, 0, 16, 16, 16, 16);
            }
            ItemStack ic = icon.get();
            if (!ic.isEmpty()) {
                PoseStack pose = g.pose();
                pose.pushPose();
                pose.translate(getX() + 1, getY() + 1, 1);
                pose.scale(0.85f, 0.85f, 1);
                g.renderFakeItem(ic, 0, 0);
                pose.popPose();
            }
        }
    }
}