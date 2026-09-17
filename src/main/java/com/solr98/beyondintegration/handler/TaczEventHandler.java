package com.solr98.beyondintegration.handler;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.common.NeoForge;

/**
 * TaCZ 服务端事件注册器。
 *
 * 独立类存放引用 TaCZ 事件类型的监听器，原因：
 * NeoForge 的 EventBus.register(Object) 会反射扫描目标类的全部方法签名
 * （含 lambda 合成方法）。若这些签名直接引用 TaCZ 事件类型，则未安装
 * TaCZ 时注册过程会触发 NoClassDefFoundError，即使调用点在
 * isLoaded("tacz") 块内也无法规避（运行时判断管不到编译期类型签名）。
 *
 * 本类仅应在 ModList.isLoaded("tacz") 为 true 时调用；类加载本身
 * 不经过 EventBus 反射扫描，故无 TaCZ 时可安全跳过。
 */
public final class TaczEventHandler {

    private TaczEventHandler() {}

    /** 注册 TaCZ 拔枪/换弹事件监听：向玩家即时推送网络弹药快照（绕过轮询延迟） */
    public static void registerEvents() {
        NeoForge.EVENT_BUS.addListener(com.tacz.guns.api.event.common.GunDrawEvent.class, e -> {
            if (e.getLogicalSide() != LogicalSide.SERVER) return;
            if (e.getEntity() instanceof ServerPlayer sp) {
                var net = DimensionsNet.getPrimaryNetFromPlayer(sp);
                if (net != null) {
                    TaczAmmoPollingService.pushSnapshotToPlayer(sp, net.getId());
                }
            }
        });
        NeoForge.EVENT_BUS.addListener(com.tacz.guns.api.event.common.GunReloadEvent.class, e -> {
            if (e.getLogicalSide() != LogicalSide.SERVER) return;
            if (e.getEntity() instanceof ServerPlayer sp) {
                var net = DimensionsNet.getPrimaryNetFromPlayer(sp);
                if (net != null) {
                    TaczAmmoPollingService.pushSnapshotToPlayer(sp, net.getId());
                }
            }
        });
    }
}
