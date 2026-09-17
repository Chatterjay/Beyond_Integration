package com.solr98.beyondintegration.client.gui;

import com.solr98.beyondintegration.init.DimensionsSmithMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SmithingTemplateItem;
import org.joml.Quaternionf;
import java.util.List;
import java.util.Optional;

/**
 * 维度网络锻造台工作站界面。
 * 绘制锻造台面板，渲染结果预览盔甲架（穿戴合成产物）、
 * 空槽图标循环动画与错误/引导提示，行为对齐原版锻造台界面。
 */
public class DimensionsSmithGUI extends DimensionsStorageGUI<DimensionsSmithMenu> {
    private static final ResourceLocation BG = ResourceLocation.parse("beyond_integration:textures/gui/smithing.png"); // 面板底图
    // 原版锻造台参数（SmithingScreen）：固定角度 25°X + 180°Z，缩放 25
    private static final Quaternionf ARMOR_STAND_ANGLE = new Quaternionf().rotationXYZ(0.43633232F, 0.0F, (float) Math.PI); // 盔甲架展示角度
    // 原版红叉图标（BI 自备纹理，28×21）
    private static final ResourceLocation NOT_FOR = ResourceLocation.parse("beyond_integration:textures/gui/notfor.png"); // 配方无效红叉
    // 原版 SmithingScreen：模板槽空槽图标（装甲纹饰 / 下界合金升级）
    private static final List<ResourceLocation> EMPTY_SLOT_SMITHING_TEMPLATES = List.of(
            ResourceLocation.withDefaultNamespace("item/empty_slot_smithing_template_armor_trim"),
            ResourceLocation.withDefaultNamespace("item/empty_slot_smithing_template_netherite_upgrade"));
    private ArmorStand armorStand; // 结果预览盔甲架
    private final CyclingSlotBackground templateIcon = new CyclingSlotBackground(); // 模板槽空槽图标动画
    private final CyclingSlotBackground baseIcon = new CyclingSlotBackground(); // 底座槽空槽图标动画
    private final CyclingSlotBackground additionalIcon = new CyclingSlotBackground(); // 附加槽空槽图标动画

    public DimensionsSmithGUI(DimensionsSmithMenu c, Inventory p, Component t) { super(c, p, t); }

    @Override protected void init() {
        super.init();
        // 初始化预览盔甲架（朝向对齐原版锻造台）
        if (armorStand == null) {
            armorStand = new ArmorStand(Minecraft.getInstance().level, 0.0D, 0.0D, 0.0D);
            armorStand.setNoBasePlate(true);
            armorStand.setShowArms(true);
            // 原版朝向（SmithingScreen.subInit）
            armorStand.yBodyRot = 210.0F;
            armorStand.setXRot(25.0F);
            armorStand.yHeadRot = armorStand.getYRot();
            armorStand.yHeadRotO = armorStand.getYRot();
        }
    }

    @Override protected void renderWorkstationPanel(GuiGraphics g) {
        int gy = getGapY();
        g.blit(BG, this.leftPos, gy, 0, 0, 176, 62, 176, 62);
        g.drawString(Minecraft.getInstance().font, Component.translatable("gui.beyond_integration.workstation.smith"), this.leftPos + 6, gy - 7, 0x404040, false);
        // 盔甲架：原版位置 (leftPos+141, topPos+75)，按 BI 面板换算 → 结果槽 (97, ey(41)) + 原版偏移 (7, 30)
        if (armorStand != null) {
            int ax = this.leftPos + 140;
            int ay = gy + 54;
            WorkstationRenderHelper.renderEntityInInventory(g, ax, ay, 25, ARMOR_STAND_ANGLE, null, armorStand);
        }
        // 原版空槽图标循环动画（模板/底座/附加槽）
        templateIcon.render(g, this.leftPos, this.topPos, slotOf(0));
        baseIcon.render(g, this.leftPos, this.topPos, slotOf(1));
        additionalIcon.render(g, this.leftPos, this.topPos, slotOf(2));
        // 原版 hasRecipeError：三个输入槽都有物品且结果为空（红叉 y 对齐输出槽 gy+42，上移 1px）
        if (hasRecipeError()) {
            g.blit(NOT_FOR, this.leftPos + 65, gy + 41, 0, 0, 28, 21, 28, 21);
            g.drawString(Minecraft.getInstance().font,
                    Component.translatable("gui.beyond_integration.smithing.invalid"),
                    this.leftPos + 6, gy + 66, 0xFFFF5555, false);
        }
        renderOnboardingTooltips(g, mouseX, mouseY);
    }

    /** 按工作站槽位序号取实际槽位对象 */
    private Slot slotOf(int wsIdx) { return this.menu.slots.get(this.menu.customSlotIndices.get(wsIdx)); }

    @Override public void containerTick() {
        super.containerTick();
        if (armorStand == null) return;
        updateArmorStand(this.menu.getOutput()); // 每 tick 同步预览盔甲架
        Optional<SmithingTemplateItem> optional = getTemplateItem();
        templateIcon.tick(EMPTY_SLOT_SMITHING_TEMPLATES);
        baseIcon.tick(optional.map(SmithingTemplateItem::getBaseSlotEmptyIcons).orElse(List.of()));
        additionalIcon.tick(optional.map(SmithingTemplateItem::getAdditionalSlotEmptyIcons).orElse(List.of()));
    }

    /** 获取模板槽中的锻造模板物品 */
    private Optional<SmithingTemplateItem> getTemplateItem() {
        ItemStack itemstack = this.menu.getTemplate();
        return !itemstack.isEmpty() && itemstack.getItem() instanceof SmithingTemplateItem st ? Optional.of(st) : Optional.empty();
    }

    // 原版 SmithingScreen.hasRecipeError
    /** 三个输入槽均有物但结果为空 → 配方无效 */
    private boolean hasRecipeError() {
        return !this.menu.getTemplate().isEmpty()
                && !this.menu.getBase().isEmpty()
                && !this.menu.getAdditional().isEmpty()
                && this.menu.getOutput().isEmpty();
    }

    // 原版 SmithingScreen.renderOnboardingTooltips：错误提示 / 缺模板提示 / 槽位描述提示（115px 换行）
    /** 渲染错误红叉、缺模板、槽位描述等引导提示 */
    private void renderOnboardingTooltips(GuiGraphics g, int mx, int my) {
        int gy = getGapY();
        Optional<Component> optional = Optional.empty();
        if (hasRecipeError() && isHovering(65, gy - this.topPos + 41, 28, 21, mx, my)) {
            optional = Optional.of(Component.translatable("container.upgrade.error_tooltip"));
        }
        if (this.hoveredSlot != null) {
            ItemStack itemstack = this.menu.getTemplate();
            ItemStack itemstack1 = this.hoveredSlot.getItem();
            if (itemstack.isEmpty()) {
                if (this.hoveredSlot == this.menu.getTemplateSlot()) {
                    optional = Optional.of(Component.translatable("container.upgrade.missing_template_tooltip"));
                }
            } else if (itemstack.getItem() instanceof SmithingTemplateItem smithingtemplateitem && itemstack1.isEmpty()) {
                if (this.hoveredSlot == this.menu.getBaseSlot()) {
                    optional = Optional.of(smithingtemplateitem.getBaseSlotDescription());
                } else if (this.hoveredSlot == this.menu.getAdditionalSlot()) {
                    optional = Optional.of(smithingtemplateitem.getAdditionSlotDescription());
                }
            }
        }
        optional.ifPresent(c -> g.renderTooltip(Minecraft.getInstance().font, Minecraft.getInstance().font.split(c, 115), mx, my));
    }

    // 原版 SmithingScreen.CyclingSlotBackground（空槽图标循环动画）
    /** 空槽图标循环动画：按 tick 轮换显示空槽图标列表 */
    private static class CyclingSlotBackground {
        private static final int ICON_SWAP_TICKS = 30; // 图标切换间隔（tick）
        private static final int ICON_SIZE = 16; // 图标尺寸
        private List<ResourceLocation> icons = List.of(); // 当前图标列表
        private int tick; // 动画计时

        /** 更新图标列表并推进动画计时 */
        public void tick(List<ResourceLocation> icons) {
            if (icons.isEmpty()) { this.tick = 0; return; }
            this.icons = icons;
            this.tick++;
        }

        /** 在空且激活的槽位上渲染当前循环图标 */
        public void render(GuiGraphics g, int leftPos, int topPos, Slot slot) {
            if (this.icons.isEmpty()) return;
            int i = (int)((float)this.tick / ICON_SWAP_TICKS % (float)this.icons.size());
            ResourceLocation rl = this.icons.get(i);
            if (!slot.hasItem() && slot.isActive()) {
                // 原版：空槽图标在 BLOCK atlas（物品纹理），需 getTextureAtlas(LOCATION_BLOCKS) + blit
                var sprite = Minecraft.getInstance().getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(rl);
                g.blit(leftPos + slot.x, topPos + slot.y, 0, ICON_SIZE, ICON_SIZE, sprite, 1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }

    // 原版逻辑（SmithingScreen.updateArmorStandPreview）：清空全部槽位 → 盔甲穿对应部位，否则副手
    /** 按合成结果更新盔甲架外观（盔甲穿对应部位，其余放副手） */
    private void updateArmorStand(ItemStack stack) {
        for (EquipmentSlot es : EquipmentSlot.values())
            armorStand.setItemSlot(es, ItemStack.EMPTY);
        if (!stack.isEmpty()) {
            ItemStack copy = stack.copy();
            if (copy.getItem() instanceof ArmorItem armorItem) {
                armorStand.setItemSlot(armorItem.getEquipmentSlot(), copy);
            } else {
                armorStand.setItemSlot(EquipmentSlot.OFFHAND, copy);
            }
        }
    }
}
