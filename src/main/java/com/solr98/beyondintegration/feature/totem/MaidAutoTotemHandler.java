package com.solr98.beyondintegration.feature.totem;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidDeathEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.maid.MaidNetworkHelper;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 女仆版自动网络图腾：女仆死亡时（MaidDeathEvent，可取消）从其关联终端网络中
 * 提取图腾救援——取消死亡并施加原版图腾效果（回血、清效果、buff、图腾动画事件）。
 * 配置与玩家版共用（autoTotemEnabled / CD / 伤害黑名单 / respect_bypasses / restore_max_health / heal_to_full）。
 */
public class MaidAutoTotemHandler {

    /** 女仆 UUID → 上次使用时间戳（冷却判定） */
    private static final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onMaidDeath(MaidDeathEvent event) {
        if (!CommandConfig.autoTotemEnabled()) return;
        EntityMaid maid = event.getMaid();
        if (maid == null || maid.level().isClientSide()) return;

        DamageSource source = event.getSource();
        if (CommandConfig.autoTotemRespectBypassesInvulnerability()
                && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (isBlacklisted(source)) return;

        long now = System.currentTimeMillis();
        Long last = lastUse.get(maid.getUUID());
        if (last != null && now - last < CommandConfig.autoTotemCooldownSeconds() * 1000L) return;

        // 从女仆关联终端网络提取图腾（Curios 饰品栏 / 女仆饰物背包中的终端）
        DimensionsNet net = MaidNetworkHelper.findTerminal(maid);
        if (net == null) return;
        KeyAmount got = net.getUnifiedStorage().extract(
                new ItemStackKey(new ItemStack(Items.TOTEM_OF_UNDYING)), 1, false, false);
        if (got.amount() <= 0) return;

        net.setDirty();
        lastUse.put(maid.getUUID(), now);
        // 取消死亡：跳过 super.die，女仆保持存活
        event.setCanceled(true);

        // 对齐原版 checkTotemDeathProtection（1.21.1）
        // 可选：恢复被降低的最大生命值上限（移除 max_health 上的负面属性修饰符）
        if (CommandConfig.autoTotemRestoreMaxHealth()) restoreMaxHealth(maid);
        // 可选：回满血（需在恢复上限之后执行，否则按旧上限计算）
        if (CommandConfig.autoTotemHealToFull()) {
            maid.setHealth(maid.getMaxHealth());
        } else {
            maid.setHealth(1.0F);
        }
        maid.removeAllEffects();
        maid.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        maid.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
        maid.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
        // 对齐原版图腾：触发后给予 20 tick（1 秒）无敌帧，防止多段伤害连续致死
        maid.invulnerableTime = 20;
        // 客户端实体事件 35：图腾粒子 + TOTEM_USE 音效 + 图腾弹出动画
        maid.level().broadcastEntityEvent(maid, (byte) 35);
    }

    /** 女仆实体移除/卸载时清理冷却记录（防止内存残留） */
    public static void onMaidRemoved(UUID maidUuid) {
        if (maidUuid != null) lastUse.remove(maidUuid);
    }

    // 恢复被降低的最大生命值：移除 max_health 属性上所有负面（amount < 0）修饰符
    private static void restoreMaxHealth(LivingEntity entity) {
        AttributeInstance attr = entity.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        // 1.21.1 中 getModifiers() 已包含永久修饰符，removeModifier(id) 可一并移除
        for (AttributeModifier mod : new ArrayList<>(attr.getModifiers())) {
            if (mod.amount() < 0) {
                attr.removeModifier(mod.id());
            }
        }
    }

    /** 伤害源消息 ID（带或不带 minecraft: 前缀）是否命中配置黑名单（空列表 = 全部放行） */
    private static boolean isBlacklisted(DamageSource source) {
        List<? extends String> list = CommandConfig.autoTotemDamageBlacklist();
        if (list == null || list.isEmpty()) return false;
        String msgId = source.getMsgId();
        String full = "minecraft:" + msgId;
        for (String entry : list) {
            if (entry == null || entry.trim().isEmpty()) continue;
            String e = entry.trim();
            if (e.equals(msgId) || e.equals(full)) return true;
        }
        return false;
    }
}
