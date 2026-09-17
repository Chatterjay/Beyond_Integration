package com.solr98.beyondintegration.feature.revive;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 复活实现的共享底层（事件版与 Mixin 版共用）。
 * <p>
 * 职责：
 * <ul>
 *   <li>致死伤害指纹：记录"原版伤害链足以致死"的 tick，用于区分正常伤害致死与 setHealth 直杀；</li>
 *   <li>冷却：两个实现共享同一冷却，避免重复触发；</li>
 *   <li>网络图腾提取：从玩家网络提取 1 + 额外数量的图腾（不足则回滚并失败）；</li>
 *   <li>复活动作：回血、恢复生命上限、重置死亡时间/姿态、原版图腾 buff 与动画。</li>
 * </ul>
 * 保守判定：死亡无法由致死级 LivingDamageEvent 解释时，一律按 setHealth 直杀处理（多扣图腾）。
 */
public final class ReviveSupport {

    /** 玩家 UUID -> 上次复活时间戳（共享冷却判定） */
    private static final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();
    /** 玩家 UUID -> 预期可致死的原版伤害所在 tick（setHealth 死亡判定指纹） */
    private static final Map<UUID, Integer> expectedLethalTick = new ConcurrentHashMap<>();

    private ReviveSupport() {}

    /**
     * 记录"本次原版伤害足以致死"的 tick。
     * LivingDamageEvent 的 amount 为护甲/吸收结算后、即将从生命值中扣除的数值，
     * 因此 health - amount <= 0 表示这次伤害链会正常致死。
     */
    public static void recordLethalDamage(ServerPlayer player, float amount) {
        if (player.getHealth() - amount <= 0.0F) {
            expectedLethalTick.put(player.getUUID(), player.tickCount);
        }
    }

    /** 本次死亡是否由原版伤害链（致死级伤害，且与死亡同 tick）解释 */
    public static boolean isExpectedLethalDeath(ServerPlayer player) {
        Integer tick = expectedLethalTick.get(player.getUUID());
        return tick != null && tick == player.tickCount;
    }

    /** 清除该玩家的致死指纹（死亡处理完成后调用，避免残留误判） */
    public static void clearDeathData(ServerPlayer player) {
        expectedLethalTick.remove(player.getUUID());
    }

    /** 是否处于共享冷却中 */
    public static boolean onCooldown(ServerPlayer player) {
        Long last = lastUse.get(player.getUUID());
        return last != null && System.currentTimeMillis() - last < CommandConfig.reviveCooldownSeconds() * 1000L;
    }

    /** 标记一次复活已触发（写入共享冷却） */
    public static void markUsed(ServerPlayer player) {
        lastUse.put(player.getUUID(), System.currentTimeMillis());
    }

    /** 玩家登出/移除时清理共享状态，避免内存残留 */
    public static void cleanup(UUID uuid) {
        if (uuid == null) return;
        lastUse.remove(uuid);
        expectedLethalTick.remove(uuid);
    }

    /**
     * 尝试消耗网络图腾复活玩家。
     *
     * @param setHealthDeath 是否判定为 setHealth 直杀（命中时按配置额外扣图腾）
     * @return 图腾充足并已执行复活动作返回 true；网络不存在/图腾不足返回 false（已提取部分会回滚）
     */
    public static boolean tryRevive(ServerPlayer player, boolean setHealthDeath) {
        int extra = setHealthDeath && CommandConfig.reviveExtraTotemOnSetHealthDeath()
                ? Math.max(0, CommandConfig.reviveExtraTotemCount()) : 0;
        int cost = 1 + extra;

        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) return false;
        ItemStackKey totemKey = new ItemStackKey(new ItemStack(Items.TOTEM_OF_UNDYING));
        KeyAmount got = net.getUnifiedStorage().extract(totemKey, cost, false, false);
        if (got.amount() < cost) {
            // 图腾不足：回滚已提取部分，复活失败
            if (got.amount() > 0) {
                net.getUnifiedStorage().insert(totemKey, got.amount(), false);
                net.setDirty();
            }
            return false;
        }
        net.setDirty();
        applyRevive(player);
        return true;
    }

    /** 执行复活动作：回血、恢复生命上限、重置死亡时间/姿态、原版图腾效果与动画 */
    public static void applyRevive(ServerPlayer player) {
        // 对齐原版 checkTotemDeathProtection 的统计与进度
        player.awardStat(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING));
        CriteriaTriggers.USED_TOTEM.trigger(player, new ItemStack(Items.TOTEM_OF_UNDYING));
        if (CommandConfig.reviveRestoreMaxHealth()) {
            restoreMaxHealth(player);
        }
        if (CommandConfig.reviveHealToFull()) {
            player.setHealth(player.getMaxHealth());
        } else {
            player.setHealth(1.0F);
        }
        if (CommandConfig.reviveResetDeathTime()) {
            // 撤销死亡计时与姿态，阻止死亡表现与移除逻辑继续推进
            player.deathTime = 0;
            player.hurtTime = 0;
            player.setPose(Pose.STANDING);
        }
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
        player.invulnerableTime = 20;
        // 客户端实体事件 35：图腾粒子 + 音效 + 图腾弹出动画
        player.level().broadcastEntityEvent(player, (byte) 35);
    }

    /** 恢复被降低的最大生命值：移除 max_health 属性上所有负面（amount < 0）修饰符 */
    public static void restoreMaxHealth(LivingEntity entity) {
        AttributeInstance attr = entity.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        for (AttributeModifier mod : new ArrayList<>(attr.getModifiers())) {
            if (mod.getAmount() < 0) {
                attr.removeModifier(mod.getId());
                attr.removePermanentModifier(mod.getId());
            }
        }
    }

    /** 伤害源消息 ID（带或不带 minecraft: 前缀）是否命中配置黑名单（空列表 = 全部放行） */
    public static boolean isBlacklisted(DamageSource source) {
        List<? extends String> list = CommandConfig.reviveDamageBlacklist();
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
