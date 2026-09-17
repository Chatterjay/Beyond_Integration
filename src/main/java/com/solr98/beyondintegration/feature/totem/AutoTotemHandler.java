package com.solr98.beyondintegration.feature.totem;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
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
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自动图腾：玩家死亡时自动从网络（仅网络）消耗一个不死图腾救援。
 * 支持启用开关、冷却时间与伤害黑名单（空列表 = 所有伤害类型均可触发）。
 * 触发后可选恢复被降低的最大生命值上限（restore_max_health）、回满血（heal_to_full），
 * 并施加原版图腾效果。
 */
public class AutoTotemHandler {

    // 玩家最后使用自动图腾的时间戳（CD 判定）
    private static final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

    // 玩家死亡事件：满足条件时从网络提取图腾、取消死亡并应用原版图腾效果
    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!CommandConfig.autoTotemEnabled()) return;
        DamageSource source = event.getSource();
        // 可配置的原版判定：respect_bypasses_invulnerability=true 时，无视无敌标签的伤害不触发图腾
        if (CommandConfig.autoTotemRespectBypassesInvulnerability()
                && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (isBlacklisted(source)) return;

        long now = System.currentTimeMillis();
        Long last = lastUse.get(player.getUUID());
        if (last != null && now - last < CommandConfig.autoTotemCooldownSeconds() * 1000L) return;

        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) return;
        KeyAmount got = net.getUnifiedStorage().extract(
                new ItemStackKey(new ItemStack(Items.TOTEM_OF_UNDYING)), 1, false, false);
        if (got.amount() <= 0) return;

        net.setDirty();
        lastUse.put(player.getUUID(), now);
        event.setCanceled(true);

        // 对齐原版 checkTotemDeathProtection
        player.awardStat(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING));
        CriteriaTriggers.USED_TOTEM.trigger(player, new ItemStack(Items.TOTEM_OF_UNDYING));
        // 可选：恢复被降低的最大生命值上限（移除 max_health 上的负面属性修饰符）
        if (CommandConfig.autoTotemRestoreMaxHealth()) restoreMaxHealth(player);
        // 可选：回满血（需在恢复上限之后执行，否则按旧上限计算）
        if (CommandConfig.autoTotemHealToFull()) {
            player.setHealth(player.getMaxHealth());
        } else {
            player.setHealth(1.0F);
        }
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
        // 对齐原版图腾：触发后给予 20 tick（1 秒）无敌帧，防止同 tick 多段伤害连续致死
        player.invulnerableTime = 20;
        // 客户端实体事件 35：图腾粒子 + TOTEM_USE 音效 + 图腾弹出动画
        player.level().broadcastEntityEvent(player, (byte) 35);
    }

    // 玩家登出：清理该玩家的 CD 记录
    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) lastUse.remove(event.getEntity().getUUID());
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

    // 伤害类型是否在黑名单中（支持纯 msgId 或带 minecraft: 前缀；空列表 = 全部放行）
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
