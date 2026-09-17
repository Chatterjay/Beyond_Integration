package com.solr98.beyondintegration.client;

import com.tacz.guns.api.event.common.GunDrawEvent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * TACZ 客户端事件注册器。
 * 监听拔枪（GunDraw）事件触发弹药快捷请求，
 * 并在玩家登录/登出时清空弹药缓存。
 */
public class TaczClientRegistrar {
    /** 注册客户端事件监听 */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(GunDrawEvent.class, e -> {
            if (e.getLogicalSide() == LogicalSide.CLIENT
                    && e.getEntity() instanceof net.minecraft.client.player.LocalPlayer) {
                // 仅处理本地玩家拔枪：提取弹药 ID 并快捷请求计数
                ResourceLocation ammoId =
                        com.solr98.beyondintegration.handler.TaczAmmoExtractor.getAmmoIdClient(e.getCurrentGunItem());
                if (ammoId != null)
                    TaczAmmoCache.requestQuick(ammoId);
            }
        });

        // 登录/登出时清空弹药缓存与 SW/工作台缓存，避免跨服务器残留
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, e -> {
            TaczAmmoCache.clear();
            SuperbAmmoCache.INSTANCE.clear();
            NetworkItemCache.clear();
        });
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            TaczAmmoCache.clear();
            SuperbAmmoCache.INSTANCE.clear();
            NetworkItemCache.clear();
        });
    }
}
