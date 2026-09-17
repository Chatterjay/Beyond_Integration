package com.solr98.beyondintegration.mixin;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.solr98.beyondintegration.handler.VehicleNetCache;
import com.solr98.beyondintegration.handler.INetCachedVehicle;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.SuperbAmmoDeltaS2CPacket;
import com.solr98.beyondintegration.network.SuperbAmmoStatusResponsePacket;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;

/**
 * 载具网络状态同步：读 VehicleNetCache（实体级缓存）。
 * 仅在有乘客时刷新（B3）；乘客从无到有时强制全量（上车重建）；
 * 解绑时向乘客推送 reset 包。
 */
@Mixin(targets = "com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity", remap = false)
public abstract class VehicleNetSyncMixin {

    /** 上次已向乘客广播的绑定网络 ID，用于检测解绑/切换 */
    @Unique
    private int beyond$lastBoundNetId = -1;

    /** 上次是否有乘客，用于乘客从无到有时强制全量同步 */
    @Unique
    private boolean beyond$lastHasPassengers = false;

    /**
     * baseTick 回调：服务端按乘客数量与绑定状态向乘客推送
     * 全量状态包或增量弹药包；解绑时广播 reset 包。
     */
    @Inject(method = "baseTick", at = @At("HEAD"), remap = true)
    private void beyond$syncNetworkStatus(CallbackInfo ci) {
        VehicleEntity vehicle = (VehicleEntity) (Object) this;
        if (vehicle.level().isClientSide()) return;

        VehicleNetCache cache = ((INetCachedVehicle) vehicle).getNetCache();
        DimensionsNet net = cache.getNet();

        if (net == null) {
            if (beyond$lastBoundNetId >= 0) {
                beyond$lastBoundNetId = -1;
                sendResetToPassengers(vehicle);
            }
            beyond$lastHasPassengers = !vehicle.getPassengers().isEmpty();
            return;
        }

        int boundNetId = net.getId();
        boolean hasPassengers = !vehicle.getPassengers().isEmpty();

        if (boundNetId != beyond$lastBoundNetId || (hasPassengers && !beyond$lastHasPassengers)) {
            cache.forceFull();
        }
        beyond$lastBoundNetId = boundNetId;
        beyond$lastHasPassengers = hasPassengers;

        if (!hasPassengers) return;

        VehicleNetCache.PushData push = cache.refresh();
        if (push == null) return;

        for (Entity p : vehicle.getPassengers()) {
            if (!(p instanceof ServerPlayer sp)) continue;
            if (push.full()) {
                PacketHandler.sendToPlayer(sp, new SuperbAmmoStatusResponsePacket(
                        boundNetId, push.netName(), push.energy(), push.enchantSeparation(),
                        push.ammo(), cache.getAmmoList()));
            } else {
                PacketHandler.sendToPlayer(sp, new SuperbAmmoDeltaS2CPacket(
                        boundNetId, true, false, push.ammo(), push.energy(),
                        push.netName(), push.enchantSeparation()));
            }
        }
    }

    /** 向全部乘客发送解绑复位包（网络 ID 为 -1，清空客户端 HUD 数据） */
    @Unique
    private static void sendResetToPassengers(VehicleEntity vehicle) {
        var reset = new SuperbAmmoStatusResponsePacket(-1, "", -1, true,
                new HashMap<>(), null);
        for (Entity p : vehicle.getPassengers()) {
            if (p instanceof ServerPlayer sp) PacketHandler.sendToPlayer(sp, reset);
        }
    }
}
