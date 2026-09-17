package com.solr98.beyondintegration.maid;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.handler.EnergyAmmoChargeHandler;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 女仆能量武器充电事件监听。
 * 订阅 TLM 女仆每 tick 事件（MaidTickEvent，由 EntityMaid#tick 触发），
 * 按能量充电配置节流后，让手持 SW 能量武器的女仆从其绑定网络补能。
 * 仅当 touhou_little_maid 加载时由主类注册本监听器。
 */
public class MaidEnergyChargeHandler {

    /** 女仆 tick：节流后尝试为手持能量武器充电（仅服务端处理） */
    @SubscribeEvent
    public void onMaidTick(MaidTickEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid == null || maid.level().isClientSide()) return;
        if (!CommandConfig.energyAmmoChargeEnabled()) return;
        int interval = CommandConfig.energyAmmoChargeInterval();
        if (interval <= 0 || maid.tickCount % interval != 0) return;
        EnergyAmmoChargeHandler.chargeMaid(maid);
    }
}
