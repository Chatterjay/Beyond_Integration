package com.solr98.beyondintegration.mixin.ywzj_vehicle;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.CommandConfig.ChargeMode;
import com.solr98.beyondintegration.CommandConfig.FuelSource;
import com.solr98.beyondintegration.handler.VehicleNetStorage;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.ywzj.vehicle.all.AllConfigs;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;

/**
 * 载具能量充电 Mixin：注入遥装甲载具的 AbstractVehicle.tick，
 * 按配置的充电模式/间隔，从绑定维度网络抽取 FE 或白名单流体燃料为载具补能。
 */
@Mixin(AbstractVehicle.class)
public class VehicleEnergyChargeMixin {

    /**
     * tick 回调：服务端按配置的充电模式/间隔/燃料来源，
     * 从绑定维度网络抽取 FE 或流体燃料补充载具能量。
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void beyond$chargeFromNetwork(CallbackInfo ci) {
        AbstractVehicle self = (AbstractVehicle) (Object) this;
        if (self.level().isClientSide()) return;

        ChargeMode mode = CommandConfig.ywzjChargeMode();
        if (mode == ChargeMode.OFF) return;

        int interval = CommandConfig.ywzjVehicleChargeInterval();
        if (self.tickCount % interval != 0) return;

        int boundNetId = VehicleNetStorage.getBoundNetId(self.getUUID());
        if (boundNetId < 0) return;

        DimensionsNet net = DimensionsNet.getNetFromId(boundNetId);
        if (net == null) {
            VehicleNetStorage.unbindVehicle(self.getUUID());
            return;
        }

        float space = self.energyInfo.energyCapacity - self.getEnergy();
        if (space <= 0) return;

        if (CommandConfig.ywzjFuelSource() == FuelSource.FE) {
            chargeFromFE(net, self, space);
        } else {
            chargeFromFluid(net, self, space);
        }
    }

    /** 以 FE 能量充电：按比例或固定速率计算抽取量，并向下取整为换算倍率整数倍避免损耗 */
    private static void chargeFromFE(DimensionsNet net, AbstractVehicle vehicle, float space) {
        int conversion = CommandConfig.ywzjVehicleEnergyConversion();
        long feNeeded = (long) (space * conversion);
        long want;

        if (CommandConfig.ywzjChargeMode() == ChargeMode.PERCENTAGE) {
            want = (long) Math.ceil(feNeeded * CommandConfig.ywzjVehicleChargePercentage() / 100.0);
        } else {
            want = Math.min(CommandConfig.ywzjVehicleEnergyChargeRate(), feNeeded);
        }
        if (want <= 0) return;

        // 向下取整到 conversion 的整数倍再抽取，避免抽出非整倍 FE 造成损耗
        long available = net.getUnifiedStorage().getStackByKey(EnergyStackKey.INSTANCE).amount();
        if (available <= 0) return;
        long toExtract = Math.min(available, want);
        toExtract -= toExtract % conversion;
        if (toExtract <= 0) return;

        long got = net.getUnifiedStorage().extract(EnergyStackKey.INSTANCE, toExtract, false, false).amount();
        if (got <= 0) return;

        vehicle.addEnergy(got / conversion);
        net.setDirty();
    }

    /** 以流体燃料充电：从网络中按白名单抽取燃料流体（1000mb = 1 燃料单位）转换为载具能量 */
    private static void chargeFromFluid(DimensionsNet net, AbstractVehicle vehicle, float space) {
        var bucketOpt = net.getUnifiedStorage().getBucket(FluidStackKey.ID);
        if (bucketOpt.isEmpty()) return;
        var bucket = bucketOpt.get();

        var whitelist = AllConfigs.common.fuelNameWhiteList.get();

        for (int i = 0; i < bucket.size(); i++) {
            if (space <= 0) break;
            var rawKey = bucket.get(i);
            if (!(rawKey instanceof FluidStackKey fluidKey)) continue;

            Fluid fluid = fluidKey.getSource();
            String fluidId = BuiltInRegistries.FLUID.getKey(fluid).toString();

            boolean allowed = whitelist.stream().anyMatch(fluidId::contains);
            if (!allowed) continue;

            long available = net.getUnifiedStorage().getStackByKey(fluidKey).amount();
            if (available <= 0) continue;

            int mbPerFuelUnit = 1000; // 1000 mb fluid = 1 fuel unit (same as FuelTankItem)
            long neededMb = (long) (space * mbPerFuelUnit);
            long toExtract = Math.min(available, neededMb);
            if (toExtract <= 0) continue;

            long extracted = net.getUnifiedStorage().extract(fluidKey, toExtract, false, false).amount();
            if (extracted <= 0) continue;

            float fuelAdded = extracted / (float) mbPerFuelUnit;
            vehicle.addEnergy(fuelAdded);
            space -= fuelAdded;
            net.setDirty();
            return;
        }
    }
}

