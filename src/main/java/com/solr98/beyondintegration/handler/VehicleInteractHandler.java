package com.solr98.beyondintegration.handler;

import com.mojang.logging.LogUtils;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.init.BDDataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;

/**
 * 载具交互处理器：玩家使用绑定网络的物品右键点击 SW / ywzj 载具时，
 * 将载具与网络绑定（SW 使用实体级缓存，其他载具使用全局映射），并取消原交互。
 */
public class VehicleInteractHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 最高优先级实体交互事件：检测目标载具并绑定网络。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        boolean isVehicle = false;
        String vehicleType = "unknown";
        try {
            Class<?> swClass = Class.forName("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity");
            if (swClass.isInstance(event.getTarget())) { isVehicle = true; vehicleType = "SW"; }
        } catch (Exception ignored) {}
        if (!isVehicle) {
            try {
                Class<?> ywzjClass = Class.forName("org.ywzj.vehicle.entity.vehicle.AbstractVehicle");
                if (ywzjClass.isInstance(event.getTarget())) { isVehicle = true; vehicleType = "ywzj"; }
            } catch (Exception ignored) {}
        }
        if (!isVehicle) return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        int netId = stack.getOrDefault(BDDataComponents.NET_ID_DATA, -1);
        if (netId < 0) return;

        // Check if network exists
        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) {
            LOGGER.warn("[BD-Net] Network {} not found for vehicle binding!", netId);
        } else if (event.getTarget() instanceof INetCachedVehicle icv) {
            // SW 载具：实体级缓存
            icv.getNetCache().attach(netId, null);
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                VehicleNetCache.PushData push = icv.getNetCache().refresh();
                if (push != null && push.full()) {
                    com.solr98.beyondintegration.network.PacketHandler.sendToPlayer(serverPlayer,
                            new com.solr98.beyondintegration.network.SuperbAmmoStatusResponsePacket(
                                    netId, push.netName(), push.energy(), push.enchantSeparation(),
                                    push.ammo(), icv.getNetCache().getAmmoList()));
                }
            }
        } else {
            VehicleNetStorage.bindVehicle(event.getTarget().getUUID(), netId);
        }

        event.setCanceled(true);
    }
}
