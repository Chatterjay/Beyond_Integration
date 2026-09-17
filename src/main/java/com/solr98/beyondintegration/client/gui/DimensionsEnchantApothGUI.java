package com.solr98.beyondintegration.client.gui;

import com.solr98.beyondintegration.init.DimensionsApothEnchantMenu;
import com.solr98.beyondintegration.init.DimensionsEnchantMenu;
import dev.shadowsoffire.placebo.util.EnchantmentUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 神化(Apothic)模式附魔台工作站界面（独立 GUI，仅神化语义）：
 * 悬停"符石候选/将获得"（服务端下发的完整列表，展示前 2 与穷尽提示）、三属性条（位阶/量子化/阿卡那）、
 * 经验点数制信息 tooltip（等级门槛 + 初始经验花费 + 威力范围 + 附魔能力 + 线索数）。
 * 对应独立 MenuType（ENCHANT_APOTH），与 {@link DimensionsEnchantGUI}（原版）完全分离。
 */
public class DimensionsEnchantApothGUI extends AbstractEnchantTableGUI {
    public DimensionsEnchantApothGUI(DimensionsEnchantMenu c, Inventory p, Component t) { super(c, p, t); }

    @Override protected boolean isApothGUI() { return true; }

    /** 菜单强转神化菜单（仅本 GUI 使用） */
    private DimensionsApothEnchantMenu apothMenu() { return (DimensionsApothEnchantMenu) this.menu; }

    // 面板追加：三属性条（位阶/量子化/阿卡那），绘制在面板下方空隙区（对齐 Apoth 值/100*110 长度）
    @Override protected void renderWorkstationPanel(GuiGraphics g) {
        super.renderWorkstationPanel(g);
        var font = Minecraft.getInstance().font;
        var menu = apothMenu();
        int gy = getGapY();
        int barsX = this.leftPos + 46;
        int barsY = gy + 76; // 面板(76)底与连接条(102)之间的空隙
        g.fill(this.leftPos, barsY - 1, this.leftPos + 176, barsY + 21, 0x66000000); // 背景
        String[] labels = { "gui.apothic_enchanting.enchant.eterna", "gui.apothic_enchanting.enchant.quanta", "gui.apothic_enchanting.enchant.arcana" };
        int[] values = { menu.apothEterna, menu.apothQuanta, menu.apothArcana };
        int[] colors = { 0xFF3DB53D, 0xFFFC5454, 0xFFA800A8 };
        for (int r = 0; r < 3; r++) {
            int y = barsY + r * 7;
            g.drawString(font, Component.translatable(labels[r]), this.leftPos + 4, y - 1, 0xFFFFFF);
            g.fill(barsX, y, barsX + 110, y + 5, 0xFF3A3A3A); // 底
            int len = Math.max(0, Math.min(110, (int) (values[r] / 100F * 110)));
            if (len > 0) g.fill(barsX, y, barsX + len, y + 5, colors[r]);
            g.drawString(font, Component.literal("" + values[r]), barsX + 114, y - 1, 0xFFFFFF);
        }
    }

    // copy 原版 EnchantmentScreen.render：悬停显示符石候选（服务端完整列表，展示前 2）+ 等级门槛/青金石 + Apoth 信息
    @Override protected void buildSlotTooltip(List<Component> list, int slot, int cost,
                                              Optional<Holder.Reference<Enchantment>> clue, int clueLevel,
                                              int lapisNeed, boolean creative, int gold) {
        boolean preview = this.menu.serverPreviewEnabled && com.solr98.beyondintegration.ClientConfig.enchantPreviewOn();
        List<EnchantmentInstance> pv = preview ? previewList(slot) : List.of();
        if (!pv.isEmpty()) {
            // 符石候选：展示前 2；若还有更多则以"符文揭示了一切"提示
            boolean hasMore = pv.size() > 2;
            list.add(Component.translatable(hasMore
                            ? "gui.beyond_integration.enchant.runes_all"
                            : "gui.beyond_integration.enchant.runes")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE));
            int shown = Math.min(pv.size(), 2);
            for (int i = 0; i < shown; i++) {
                list.add(stylePreviewEntry(pv.get(i)));
            }
        } else if (clue.isEmpty()) {
            list.add(Component.translatable("container.enchant.clue", "").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            list.add(CommonComponents.EMPTY);
            list.add(Component.translatable("neoforge.container.enchant.limitedEnchantability").withStyle(ChatFormatting.RED));
        } else {
            list.add(Component.translatable("container.enchant.clue",
                    Enchantment.getFullname(clue.get(), clueLevel)).withStyle(ChatFormatting.WHITE));
        }

        if (clue.isEmpty()) return;
        if (!creative) {
            list.add(Component.literal(""));
            // 需求：等级门槛（对齐 Apoth：exp >= costs 才可点；levelGateIgnore 解除）+ 青金石
            if (!com.solr98.beyondintegration.CommandConfig.enchantLevelGateIgnore() && this.minecraft.player.experienceLevel < cost) {
                list.add(Component.translatable("container.enchant.level.requirement", this.menu.costs[slot]).withStyle(ChatFormatting.RED));
            } else {
                MutableComponent lapis = lapisNeed == 1
                        ? Component.translatable("container.enchant.lapis.one")
                        : Component.translatable("container.enchant.lapis.many", lapisNeed);
                list.add(lapis.withStyle(gold >= lapisNeed ? ChatFormatting.GRAY : ChatFormatting.RED));
            }
            // Apoth 信息：经验点数 / 功率波动范围 / 附魔能力 / 线索数（对齐 Apoth 左侧信息块，并入主 tooltip）
            var menu = apothMenu();
            int pts = menu.getApothExpCost(slot);
            list.add(Component.translatable("info.apothic_enchanting.ench_at", cost)
                    .withStyle(ChatFormatting.UNDERLINE, ChatFormatting.GREEN));
            list.add(Component.translatable("info.apothic_enchanting.xp_cost",
                    Component.literal("" + pts).withStyle(ChatFormatting.GREEN),
                    Component.literal("" + EnchantmentUtils.getLevelForExperience(pts)).withStyle(ChatFormatting.GREEN)));
            list.add(Component.translatable("gui.beyond_integration.enchant.xp_points.network_first")
                    .withStyle(ChatFormatting.DARK_GRAY));
            float q = menu.apothQuanta / 100F;
            int minPow = Math.round(Mth.clamp(cost - cost * q, 1, 200));
            int maxPow = Math.round(Mth.clamp(cost + cost * q, 1, 200));
            list.add(Component.translatable("info.apothic_enchanting.power_range",
                    Component.literal("" + minPow).withStyle(ChatFormatting.DARK_RED),
                    Component.literal("" + maxPow).withStyle(ChatFormatting.BLUE)));
            list.add(Component.translatable("info.apothic_enchanting.item_ench",
                    Component.literal("" + menu.getItemInput().getEnchantmentValue()).withStyle(ChatFormatting.GREEN)));
            list.add(Component.translatable("info.apothic_enchanting.num_clues",
                    Component.literal("" + 2).withStyle(ChatFormatting.DARK_AQUA)));
        }
    }
}