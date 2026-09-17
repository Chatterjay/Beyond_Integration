package com.solr98.beyondintegration.api;

import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.solr98.beyondintegration.handler.TaczAmmoExtractor;
import com.solr98.beyondintegration.handler.TaczAmmoTracker;
import com.solr98.beyondintegration.maid.MaidNetworkHelper;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

import java.util.Collections;
import java.util.Map;

/**
 * 女仆网络公开 API：供其他模组调用女仆（Touhou Little Maid）关联维度网络的能力。
 *
 * 特性：
 * - 全部方法对"未安装女仆/BD 模组、实体非女仆、无网络、客户端侧"安全，
 *   返回默认值（null / -1 / 0 / false / 空集合），不会抛异常；
 * - 弹药查询/消耗走服务端 BD API 直查（与内部弹药系统同口径：查询精确 reference key，
 *   扣减 extract 返回值驱动，创造箱按虚拟计数 O(1) 无限判定）；
 * - 弹药/物品操作仅应在服务端主线程调用（客户端调用返回 0/false）。
 */
public final class MaidNetworkAPI {

    private MaidNetworkAPI() {}

    // ───────────────────── 网络定位 ─────────────────────

    /** 实体是否为女仆（touhou_little_maid 未加载时恒 false）。 */
    public static boolean isMaid(LivingEntity entity) {
        if (entity == null) return false;
        if (!ModList.get().isLoaded("touhou_little_maid")) return false;
        try {
            return entity instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    /** 获取女仆关联的维度网络（Curios 饰品栏 / 女仆饰物背包终端；无网络返回 null）。 */
    public static DimensionsNet getMaidNetwork(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide()) return null;
        return MaidNetworkHelper.findTerminal(entity);
    }

    /** 获取女仆关联网络 ID（无网络返回 -1）。 */
    public static int getMaidNetId(LivingEntity entity) {
        DimensionsNet net = getMaidNetwork(entity);
        return net != null ? net.getId() : -1;
    }

    /** 女仆是否已关联维度网络。 */
    public static boolean hasNetwork(LivingEntity entity) {
        return getMaidNetwork(entity) != null;
    }

    // ───────────────────── TACZ 弹药（按弹药 ID） ─────────────────────

    /**
     * 查询女仆网络中的 TACZ 弹药数量（创造箱无限返回 Integer.MAX_VALUE；无网络返回 0）。
     * 仅服务端。
     */
    public static int getTaczAmmoCount(LivingEntity entity, ResourceLocation ammoId) {
        DimensionsNet net = getMaidNetwork(entity);
        if (net == null || ammoId == null) return 0;
        return TaczAmmoExtractor.countAmmoInNetworkByAmmoId(ammoId, net);
    }

    /** 女仆网络中该弹药是否无限（创造箱虚拟计数，O(1)）。仅服务端。 */
    public static boolean isTaczAmmoInfinite(LivingEntity entity, ResourceLocation ammoId) {
        DimensionsNet net = getMaidNetwork(entity);
        if (net == null || ammoId == null) return false;
        return TaczAmmoTracker.isInfinite(net, ammoId);
    }

    /**
     * 获取女仆网络 TACZ 弹药全量快照（弹药 ID → 数量，创造箱为 MAX_VALUE；无网络返回空 Map）。
     * 仅服务端。
     */
    public static Map<String, Integer> getTaczAmmoSnapshot(LivingEntity entity) {
        DimensionsNet net = getMaidNetwork(entity);
        if (net == null) return Collections.emptyMap();
        return TaczAmmoExtractor.countAllAmmoInNetwork(net);
    }

    /**
     * 从女仆网络消耗 TACZ 弹药（创造箱无限直接满足；extract 返回值驱动）。
     *
     * @return 实际消耗数量；0 表示不足/无网络
     */
    public static int consumeTaczAmmo(LivingEntity entity, ResourceLocation ammoId, int amount) {
        DimensionsNet net = getMaidNetwork(entity);
        if (net == null || ammoId == null || amount <= 0) return 0;
        return TaczAmmoExtractor.consumeAmmoByAmmoId(ammoId, amount, net);
    }

    /** 女仆网络是否有足够弹药（数量级判定；无限恒 true）。仅服务端。 */
    public static boolean canConsumeTaczAmmo(LivingEntity entity, ResourceLocation ammoId, int amount) {
        if (amount <= 0) return true;
        int count = getTaczAmmoCount(entity, ammoId);
        return count >= amount || count == Integer.MAX_VALUE;
    }

    // ───────────────────── SW 弹药（虚拟弹药） ─────────────────────

    /**
     * 查询女仆网络 SW 虚拟弹药数量（无限弹药返回 Long.MAX_VALUE；无网络返回 0）。
     * 仅服务端。
     */
    public static long getSwAmmoCount(LivingEntity entity, String ammoType) {
        DimensionsNet net = getMaidNetwork(entity);
        if (net == null || ammoType == null) return 0;
        if (!(net instanceof SuperbAmmoAccessor acc)) return 0;
        var map = acc.getSuperbAmmo();
        if (map.getOrDefault("__infinite__", 0L) > 0) return Long.MAX_VALUE;
        return map.getOrDefault(ammoType, 0L);
    }

    /** 女仆网络是否启用 SW 无限弹药。仅服务端。 */
    public static boolean isSwInfinite(LivingEntity entity) {
        DimensionsNet net = getMaidNetwork(entity);
        if (net == null) return false;
        return net instanceof SuperbAmmoAccessor acc
                && acc.getSuperbAmmo().getOrDefault("__infinite__", 0L) > 0;
    }

    /**
     * 从女仆网络消耗 SW 虚拟弹药（无限时不消耗直接满足；返回值驱动）。
     *
     * @return 实际消耗数量；0 表示不足/无网络
     */
    public static long consumeSwAmmo(LivingEntity entity, String ammoType, long amount) {
        DimensionsNet net = getMaidNetwork(entity);
        if (net == null || ammoType == null || amount <= 0) return 0;
        if (!(net instanceof SuperbAmmoAccessor acc)) return 0;
        var map = acc.getSuperbAmmo();
        if (map.getOrDefault("__infinite__", 0L) > 0) return amount;
        long current = map.getOrDefault(ammoType, 0L);
        if (current <= 0) return 0;
        long take = Math.min(current, amount);
        long left = current - take;
        if (left <= 0) map.remove(ammoType);
        else map.put(ammoType, left);
        net.setDirty();
        return take;
    }

    // ───────────────────── 物品操作 ─────────────────────

    /**
     * 查询女仆网络中指定物品的数量（精确 key 查询）。
     * 仅服务端。
     */
    public static long getItemCount(LivingEntity entity, ItemStack item) {
        DimensionsNet net = getMaidNetwork(entity);
        if (net == null || item == null || item.isEmpty()) return 0;
        return net.getUnifiedStorage().getStackByKey(new ItemStackKey(item)).amount();
    }

    /** 女仆网络中是否存在指定物品。仅服务端。 */
    public static boolean hasItem(LivingEntity entity, ItemStack item) {
        return getItemCount(entity, item) > 0;
    }

    /**
     * 从女仆网络提取指定物品（extract 返回值驱动）。
     *
     * @return 实际提取数量；0 表示不足/无网络
     */
    public static long extractItem(LivingEntity entity, ItemStack item, long amount) {
        DimensionsNet net = getMaidNetwork(entity);
        if (net == null || item == null || item.isEmpty() || amount <= 0) return 0;
        KeyAmount extracted = net.getUnifiedStorage().extract(new ItemStackKey(item), amount, false, false);
        if (extracted.amount() > 0) {
            net.setDirty();
            return extracted.amount();
        }
        return 0;
    }

    /**
     * 从女仆网络提取一个不死图腾（供复活类逻辑调用）。
     *
     * @return 是否成功提取
     */
    public static boolean extractTotem(LivingEntity entity) {
        return extractItem(entity, new ItemStack(Items.TOTEM_OF_UNDYING), 1) > 0;
    }
}
