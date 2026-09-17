package com.solr98.beyondintegration.client.gui;

import com.solr98.beyondintegration.init.DimensionsEnchantMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.List;
import java.util.Optional;

/**
 * 原版模式附魔台工作站界面（独立 GUI，仅原版语义：单谜名 + 等级/青金石需求 tooltip）。
 * 与神化模式界面 {@link DimensionsEnchantApothGUI} 完全分离，对应各自 MenuType。
 */
public class DimensionsEnchantGUI extends AbstractEnchantTableGUI {
    public DimensionsEnchantGUI(DimensionsEnchantMenu c, Inventory p, Component t) { super(c, p, t); }

    @Override protected boolean isApothGUI() { return false; }

    // copy 原版 EnchantmentScreen.render：悬停显示"将获得"预览（服务端开关+客户端偏好开启时）或谜语 + 需求
    @Override protected void buildSlotTooltip(List<Component> list, int slot, int cost,
                                              Optional<Holder.Reference<Enchantment>> clue, int clueLevel,
                                              int lapisNeed, boolean creative, int gold) {
        boolean preview = this.menu.serverPreviewEnabled && com.solr98.beyondintegration.ClientConfig.enchantPreviewOn();
        List<EnchantmentInstance> pv = preview ? previewList(slot) : List.of();
        if (preview && !pv.isEmpty()) {
            // 直接预览将获得的附魔（完整列表，服务端下发）
            list.add(Component.translatable("gui.beyond_integration.enchant.preview").withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE));
            int shown = Math.min(pv.size(), 6);
            for (int i = 0; i < shown; i++) {
                list.add(stylePreviewEntry(pv.get(i)));
            }
            if (pv.size() > shown) {
                list.add(Component.translatable("gui.beyond_integration.enchant.preview.more", pv.size() - shown)
                        .withStyle(ChatFormatting.GRAY));
            }
        } else if (clue.isEmpty()) {
            list.add(Component.translatable("container.enchant.clue", "").withStyle(ChatFormatting.WHITE));
            list.add(Component.literal(""));
            list.add(Component.translatable("neoforge.container.enchant.limitedEnchantability").withStyle(ChatFormatting.RED));
        } else {
            list.add(Component.translatable("container.enchant.clue",
                    Enchantment.getFullname(clue.get(), clueLevel)).withStyle(ChatFormatting.WHITE));
        }
        if (!preview && clue.isEmpty()) return;
        if (!creative) {
            list.add(CommonComponents.EMPTY);
            if (!com.solr98.beyondintegration.CommandConfig.enchantLevelGateIgnore() && this.minecraft.player.experienceLevel < cost) {
                list.add(Component.translatable("container.enchant.level.requirement", this.menu.costs[slot]).withStyle(ChatFormatting.RED));
            } else {
                MutableComponent mutablecomponent = lapisNeed == 1
                        ? Component.translatable("container.enchant.lapis.one")
                        : Component.translatable("container.enchant.lapis.many", lapisNeed);
                list.add(mutablecomponent.withStyle(gold >= lapisNeed ? ChatFormatting.GRAY : ChatFormatting.RED));
                if (com.solr98.beyondintegration.CommandConfig.enchantLevelGateIgnore()) {
                    list.add(Component.translatable("gui.beyond_integration.enchant.gate_ignored").withStyle(ChatFormatting.DARK_GRAY));
                } else {
                    MutableComponent mutablecomponent1 = lapisNeed == 1
                            ? Component.translatable("container.enchant.level.one")
                            : Component.translatable("container.enchant.level.many", lapisNeed);
                    list.add(mutablecomponent1.withStyle(ChatFormatting.GRAY));
                }
            }
        }
    }
}