package com.solr98.beyondintegration.feature.revive;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 致死伤害指纹记录器（事件版与 Mixin 版共享）。
 * <p>
 * 仅监听 {@link LivingDamageEvent}：当某次原版伤害链的结算值足以致死时，
 * 记录该玩家当前 tick，供复活实现区分"正常伤害致死"与"setHealth 直杀"。
 * 该处理器只在任一复活实现启用时注册；两个实现都关闭时不加载，零事件开销。
 */
public class ReviveFingerprintHandler {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ReviveSupport.recordLethalDamage(player, event.getAmount());
    }

    /** 登出清理共享状态，避免内存残留 */
    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) {
            ReviveSupport.cleanup(event.getEntity().getUUID());
        }
    }
}
