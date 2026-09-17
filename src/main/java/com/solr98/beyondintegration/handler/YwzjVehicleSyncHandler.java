package com.solr98.beyondintegration.handler;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.network.YwzjVehicleDataResponsePacket;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;
import org.ywzj.vehicle.vehicle.part.PartUnit;
import org.ywzj.vehicle.vehicle.part.WeaponUnit;
import org.ywzj.vehicle.vehicle.weapon.AbstractVehicleWeapon;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * ywzj 载具数据同步处理器：玩家乘坐 ywzj 载具时，周期性（最小间隔 2 秒）
 * 将载具绑定网络的能量、网络名及弹药数量推送给乘客。
 */
public class YwzjVehicleSyncHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** 各载具上次同步时间戳（载具 UUID → 毫秒时间戳，弱引用防止内存泄漏）。 */
    private final Map<UUID, Long> lastSyncTime = new WeakHashMap<>();
    /** 同步最小间隔（毫秒）。 */
    private static final long SYNC_INTERVAL = 2000;

    /** 玩家 tick 回调：乘坐在 ywzj 载具上时周期性同步载具网络数据。 */
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        syncVehicleData(player);
    }

    /** 玩家登录回调：进入世界时立即同步一次。 */
    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            syncVehicleData(p);
        }
    }

    /** 同步载具绑定的网络数据（受同步间隔限制），推送能量、网络名与弹药清单。 */
    private void syncVehicleData(ServerPlayer player) {
        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof AbstractVehicle ywzjVehicle)) return;

        UUID vehUuid = vehicle.getUUID();
        long now = System.currentTimeMillis();
        Long last = lastSyncTime.get(vehUuid);
        if (last != null && now - last < SYNC_INTERVAL) return;
        lastSyncTime.put(vehUuid, now);

        int netId = VehicleNetStorage.getBoundNetId(vehUuid);
        if (netId < 0) return;

        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) {
            VehicleNetStorage.unbindVehicle(vehUuid);
            return;
        }

        long energy = net.getUnifiedStorage().getStackByKey(EnergyStackKey.INSTANCE).amount();
        String netName = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "";

        Map<String, Long> ammoMap = collectAmmoCounts(ywzjVehicle, net);

        PacketDistributor.sendToPlayer(player, new YwzjVehicleDataResponsePacket(netId, energy, netName, ammoMap));
    }

    /** 收集载具全部武器部件所需弹药的网络数量（弹药注册名 → 数量）。 */
    private static Map<String, Long> collectAmmoCounts(AbstractVehicle vehicle, DimensionsNet net) {
        Map<String, Long> result = new HashMap<>();

        for (PartUnit<?> part : vehicle.getPartUnits()) {
            if (!(part instanceof WeaponUnit weaponUnit)) continue;
            collectWeaponAmmo(weaponUnit.weapons, net, result);
            collectWeaponAmmo(weaponUnit.secondaryWeapons, net, result);
            collectWeaponAmmo(weaponUnit.independentWeapons, net, result);
        }

        return result;
    }

    /** 收集一组武器所需的弹药网络数量，并入结果映射。 */
    private static void collectWeaponAmmo(
            java.util.List<AbstractVehicleWeapon<?>> weapons,
            DimensionsNet net,
            Map<String, Long> result
    ) {
        for (var weapon : weapons) {
            var ingredient = weapon.getData().getReload().getAmmo();
            if (ingredient == null) continue;

            for (ItemStack stack : ingredient.getItems()) {
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (result.containsKey(id)) continue;
                long count = net.getUnifiedStorage().getStackByKey(new ItemStackKey(stack)).amount();
                if (count > 0) result.put(id, count);
            }
        }
    }
}

