package com.solr98.beyondintegration.mixin;
import com.atsuishio.superbwarfare.data.gun.AmmoConsumer;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.client.SuperbAmmoCache;
import com.solr98.beyondintegration.command.util.NetworkUtils;
import com.solr98.beyondintegration.handler.FakePlayerNetMarker;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.solr98.beyondintegration.handler.INetCachedVehicle;
import com.solr98.beyondintegration.maid.MaidNetworkHelper;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayList;
import java.util.List;

/**
 * 注入目标：Superb Warfare 的 {@code GunData}（枪械数据）。
 * 目的：扩展"备用弹药"判定与计数逻辑，把维度网络中的弹药（玩家弹药/物品/无限模式）
 * 计入结果，使 HUD 与射击逻辑能正确反映网络弹药存量，支持玩家、载具、FakePlayer、女仆等实体。
 */
@Mixin(targets = "com.atsuishio.superbwarfare.data.gun.GunData", remap = false)
public class GunDataMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    // 判断当前枪械是否处于背包弹药模式
    private boolean beyond$isBackpackMode() {
        try { return ((GunData) (Object) this).useBackpackAmmo(); } catch (Exception e) { return false; }
    }

    // 拦截 hasBackupAmmo：原判断无备用弹药时，检查各网络的弹药存量
    @Inject(method = "hasBackupAmmo", at = @At("RETURN"), cancellable = true, remap = false)
    private void onHasBackupAmmo(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        try {
            var consumer = ((GunData) (Object) this).selectedAmmoConsumer();
            if (consumer == null) return;
            var type = consumer.getType();
            if (type == AmmoConsumer.AmmoConsumeType.PLAYER_AMMO) {
                var ammoType = consumer.getPlayerAmmoType();
                if (ammoType == null) return;
                String key = ammoType.serializationName;
                if (SuperbAmmoCache.INSTANCE.hasData() && SuperbAmmoCache.INSTANCE.hasInfinite()) { cir.setReturnValue(true); return; }
                if (entity instanceof VehicleEntity && SuperbAmmoCache.INSTANCE.vehicleHasData()) {
                    if (SuperbAmmoCache.INSTANCE.getVehicleCount("__infinite__") > 0) { cir.setReturnValue(true); return; }
                    if (SuperbAmmoCache.INSTANCE.getVehicleCount(key) > 0) { cir.setReturnValue(true); return; }
                }
                for (var net : getNets(entity)) {
                    if (!(net instanceof SuperbAmmoAccessor acc)) continue;
                    var m = acc.getSuperbAmmo();
                    if (m.getOrDefault("__infinite__", 0L) > 0 || m.getOrDefault(key, 0L) > 0) { cir.setReturnValue(true); return; }
                }
                if (SuperbAmmoCache.INSTANCE.hasData() && SuperbAmmoCache.INSTANCE.getCount(key) > 0) cir.setReturnValue(true);
            } else if (type == AmmoConsumer.AmmoConsumeType.ITEM && beyond$isExpAmmo(consumer)) {
                // 经验弹药：玩家经验或网络 XP 流体（可自动转化为经验）存在即视为有备用弹药
                if (entity instanceof ServerPlayer p && p.totalExperience > 0) { cir.setReturnValue(true); return; }
                for (var net : getNets(entity)) {
                    long xp = net.getUnifiedStorage().getStackByKey(
                            com.solr98.beyondintegration.handler.EnchantmentBookSeparatorHandler.xpFluidKey()).amount();
                    if (xp > 0) { cir.setReturnValue(true); return; }
                }
            } else if (type == AmmoConsumer.AmmoConsumeType.ITEM) {
                ItemStack ammoStack = consumer.stack();
                if (ammoStack.isEmpty()) return;
                for (var net : getNets(entity)) {
                    if (net instanceof SuperbAmmoAccessor acc && acc.getSuperbAmmo().getOrDefault("__infinite__", 0L) > 0) { cir.setReturnValue(true); return; }
                    if (countItems(net, ammoStack) > 0) { cir.setReturnValue(true); return; }
                }
            }
        } catch (Exception e) { LOGGER.warn("hasBackupAmmo error", e); }
    }

    // 拦截 countBackupAmmo：统计并累加各网络的备用弹药数量（无限模式返回最大值）
    @Inject(method = "countBackupAmmo", at = @At("RETURN"), cancellable = true, remap = false)
    private void onCountBackupAmmo(Entity entity, CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValue() >= Integer.MAX_VALUE / 2) return;
        // 客户端HUD: 只对 useBackpackAmmo 枪显示网络弹药
        if (entity != null && entity.level() != null && entity.level().isClientSide() && !beyond$isBackpackMode()) return;
        try {
            var consumer = ((GunData) (Object) this).selectedAmmoConsumer();
            if (consumer == null) return;
            var type = consumer.getType();
            if (type == AmmoConsumer.AmmoConsumeType.PLAYER_AMMO) {
                var ammoType = consumer.getPlayerAmmoType();
                if (ammoType == null) return;
                String key = ammoType.serializationName;
                if (SuperbAmmoCache.INSTANCE.hasData() && SuperbAmmoCache.INSTANCE.hasInfinite()) { cir.setReturnValue(Integer.MAX_VALUE); return; }
                if (entity instanceof VehicleEntity && SuperbAmmoCache.INSTANCE.vehicleHasData()) {
                    if (SuperbAmmoCache.INSTANCE.getVehicleCount("__infinite__") > 0) { cir.setReturnValue(Integer.MAX_VALUE); return; }
                    long vc = SuperbAmmoCache.INSTANCE.getVehicleCount(key);
                    if (vc > 0) { cir.setReturnValue((int) Math.min(cir.getReturnValue() + vc, Integer.MAX_VALUE)); return; }
                }
                for (var net : getNets(entity)) {
                    if (!(net instanceof SuperbAmmoAccessor acc)) continue;
                    if (acc.getSuperbAmmo().getOrDefault("__infinite__", 0L) > 0) { cir.setReturnValue(Integer.MAX_VALUE); return; }
                }
                for (var net : getNets(entity)) {
                    if (!(net instanceof SuperbAmmoAccessor acc)) continue;
                    long n = acc.getSuperbAmmo().getOrDefault(key, 0L);
                    if (n > 0) { cir.setReturnValue((int) Math.min(cir.getReturnValue() + n, Integer.MAX_VALUE)); return; }
                }
                if (SuperbAmmoCache.INSTANCE.hasData()) {
                    long n = SuperbAmmoCache.INSTANCE.getCount(key);
                    if (n > 0) cir.setReturnValue((int) Math.min(cir.getReturnValue() + n, Integer.MAX_VALUE));
                }
            } else if (type == AmmoConsumer.AmmoConsumeType.ITEM && beyond$isExpAmmo(consumer)) {
                // 经验弹药（ExpAmmoStrategy）：网络 XP 流体可自动转化为玩家经验补给
                // 玩家已有经验由原逻辑计入；此处叠加网络可转化经验（按 1 经验 = 1 弹药保守折算）
                for (var net : getNets(entity)) {
                    long netXp = net.getUnifiedStorage().getStackByKey(
                            com.solr98.beyondintegration.handler.EnchantmentBookSeparatorHandler.xpFluidKey()).amount() / 20;
                    if (netXp > 0) { cir.setReturnValue((int) Math.min(cir.getReturnValue() + netXp, Integer.MAX_VALUE)); return; }
                }
            } else if (type == AmmoConsumer.AmmoConsumeType.ITEM) {
                ItemStack ammoStack = consumer.stack();
                if (ammoStack.isEmpty()) return;
                for (var net : getNets(entity))
                    if (net instanceof SuperbAmmoAccessor acc && acc.getSuperbAmmo().getOrDefault("__infinite__", 0L) > 0) { cir.setReturnValue(Integer.MAX_VALUE); return; }
                for (var net : getNets(entity)) {
                    long n = countItems(net, ammoStack);
                    if (n > 0) { cir.setReturnValue((int) Math.min(cir.getReturnValue() + n, Integer.MAX_VALUE)); return; }
                }
            }
        } catch (Exception e) { LOGGER.warn("countBackupAmmo error", e); }
    }

    /** 判断消耗器是否为经验弹药（ammo 字符串以 "exp" 开头，SW ExpAmmoStrategy） */
    @Unique
    private static boolean beyond$isExpAmmo(AmmoConsumer consumer) {
        try {
            String ammo = consumer.getAmmo();
            return ammo != null && ammo.toLowerCase(java.util.Locale.ROOT).startsWith("exp");
        } catch (Exception e) {
            return false;
        }
    }

    // SW 物品弹药无 NBT 变种：reference key 精确查询（O(1)，与扣减同口径）
    private static long countItems(DimensionsNet net, ItemStack target) {
        if (net == null) return 0;
        return net.getUnifiedStorage().getStackByKey(new ItemStackKey(target)).amount();
    }

    // 收集实体可用的网络列表：服务端玩家取主网络优先的全部网络，载具/女仆/FakePlayer 取各自绑定网络
    private static List<DimensionsNet> getNets(Entity entity) {
        List<DimensionsNet> nets = new ArrayList<>();
        // 客户端渲染线程不触碰服务端 SavedData：远程服务器不可达（getNetFromId 返回 null），
        // 单机集成服务器由 SuperbAmmoCache 的 S2C 快照提供数据，无需直读服务端
        if (entity == null || entity.level() == null || entity.level().isClientSide()) return nets;
        if (entity instanceof ServerPlayer p) nets.addAll(NetworkUtils.getPlayerNetsPrimaryFirst(p));
        if (entity instanceof VehicleEntity v) {
            var cache = ((com.solr98.beyondintegration.handler.INetCachedVehicle) v).getNetCache();
            var n = cache.getNet();
            if (n != null) nets.add(n);
        }
        if (entity instanceof FakePlayer fp) { var n = FakePlayerNetMarker.getNet(fp); if (n != null) nets.add(n); }
        if (entity instanceof LivingEntity living && net.neoforged.fml.ModList.get().isLoaded("touhou_little_maid")) {
            var n = MaidNetworkHelper.findTerminal(living);
            if (n != null) nets.add(n);
        }
        return nets;
    }
}

