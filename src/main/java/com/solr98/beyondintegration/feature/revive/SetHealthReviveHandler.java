package com.solr98.beyondintegration.feature.revive;

import com.solr98.beyondintegration.CommandConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 事件版 setHealth 复活实现：监听 {@link LivingDeathEvent}，
 * 玩家死亡时消耗网络图腾复活；当死亡无法由原版伤害链解释
 * （未记录到致死级 LivingDamageEvent）时，判定为 setHealth 直杀并额外消耗图腾。
 * <p>
 * 配置 {@code revive.event_enabled=false} 时该处理器不会被注册（类不加载，零事件开销）。
 * <p>
 * 注意：本实现依赖死亡事件，因此在"死亡事件被其他机制拦截/抢先"或
 * FE 补偿路径接管的情况下能力有限；需要抢在 die() 之前拦截时请启用 Mixin 版实现。
 */
public class SetHealthReviveHandler {

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!CommandConfig.reviveEventEnabled()) return;
        DamageSource source = event.getSource();
        if (CommandConfig.reviveRespectBypassesInvulnerability()
                && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (ReviveSupport.isBlacklisted(source)) return;
        if (ReviveSupport.onCooldown(player)) return;

        // 保守判定：无法确认由原版伤害链致死时，一律按 setHealth 直杀处理（多扣图腾）
        boolean setHealthDeath = !ReviveSupport.isExpectedLethalDeath(player);
        ReviveSupport.clearDeathData(player);

        if (!ReviveSupport.tryRevive(player, setHealthDeath)) return;
        ReviveSupport.markUsed(player);
        event.setCanceled(true);
    }
}
