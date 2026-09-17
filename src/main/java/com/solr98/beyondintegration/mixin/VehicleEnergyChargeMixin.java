package com.solr98.beyondintegration.mixin;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.handler.VehicleNetCache;
import com.solr98.beyondintegration.handler.INetCachedVehicle;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 载具能量充电 Mixin：注入 SuperbWarfare 的 VehicleEntity.baseTick，
 * 按配置的间隔/比例定期从载具绑定的维度网络抽取能量（FE），
 * 给载具的能量存储充电。
 */
@Mixin(targets = "com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity", remap = false)
public abstract class VehicleEnergyChargeMixin {
    /**
     * baseTick 回调：服务端按充电间隔计算缺口，从绑定维度网络抽取
     * 能量（按百分比或固定速率）充入载具能量存储。
     */
    @Inject(method = "baseTick", at = @At("HEAD"), remap = true)
    private void beyond$chargeFromNetwork(CallbackInfo ci) {
        VehicleEntity vehicle = (VehicleEntity) (Object) this;
        if (vehicle.level().isClientSide()) return;
        if (!vehicle.hasEnergyStorage()) return;

        int interval = CommandConfig.vehicleChargeInterval();
        if (vehicle.tickCount % interval != 0) return;

        int needed = vehicle.getMaxEnergy() - vehicle.getEnergy();
        if (needed <= 0) return;

        VehicleNetCache cache = ((INetCachedVehicle) vehicle).getNetCache();
        DimensionsNet net = cache.getNet();
        if (net == null) return;

        double pct = CommandConfig.vehicleChargePercentage();
        long want;
        if (pct > 0) {
            want = (long) Math.ceil(needed * pct / 100.0);
        } else {
            want = Math.min(needed, CommandConfig.SERVER.swVehicleEnergyChargeRate.get());
        }
        if (want <= 0) return;

        long got = net.getUnifiedStorage().extract(EnergyStackKey.INSTANCE, want, false, false).amount();
        if (got <= 0) return;

        int transfer = (int) Math.min(got, Integer.MAX_VALUE);
        IEnergyStorage energyStorage = vehicle.getEnergyStorage();
        if (energyStorage != null && energyStorage.canReceive()) {
            energyStorage.receiveEnergy(transfer, false);
            net.setDirty();
        }
    }
}

