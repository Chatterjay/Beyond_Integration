package com.solr98.beyondintegration.handler;

import com.tacz.guns.api.item.IAmmoBox;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每网络一份的 Tacz 创造弹药箱虚拟计数缓存（服务端唯一维护的弹药缓存）。
 *
 * 计数存于 NetworkAmmoData（由 DimensionsNetMixin 的 storage delta 订阅维护并持久化），
 * 本类仅负责：首次访问该网络时一次性对账（物理扫描创造箱补齐 delta 缺口），
 * 以及提供 O(1) 的创造箱无限弹药判定（纯缓存直读，不做存储扫描）。
 *
 * 物理弹药不做任何服务端缓存：查询走 BD API 直查（TaczAmmoExtractor），
 * 扣减由 extract 返回值驱动。
 */
public class TaczAmmoTracker {

    /** 各网络的追踪器缓存（网络 ID → 追踪器）。 */
    private static final Map<Integer, TaczAmmoTracker> TRACKERS = new ConcurrentHashMap<>();

    /** 所属网络 ID。 */
    private final int netId;

    private TaczAmmoTracker(int netId) {
        this.netId = netId;
    }

    /** 获取指定网络的创造箱追踪器（不存在则创建并执行一次创造箱对账）。 */
    public static TaczAmmoTracker getOrCreate(DimensionsNet net) {
        if (net == null) return null;
        return TRACKERS.computeIfAbsent(net.getId(), k -> {
            reconcileCreativeBoxes(net);
            return new TaczAmmoTracker(k);
        });
    }

    /** 按网络 ID 移除追踪器（网络销毁时清理）。 */
    public static void removeById(int netId) {
        TRACKERS.remove(netId);
    }

    /** 清空全部追踪器（服务端停止等场景）。 */
    public static void clear() {
        TRACKERS.clear();
    }

    /**
     * 网络是否有该弹药的创造箱（无限弹药）：
     * 只读虚拟计数缓存（O(1)），不做存储扫描；首次访问时自动对账补齐计数。
     */
    public static boolean isInfinite(DimensionsNet net, ResourceLocation ammoId) {
        if (net == null || ammoId == null) return false;
        getOrCreate(net);
        if (net instanceof TaczCreativeAccessor tac) {
            Map<String, Integer> counts = tac.getTaczCreativeCounts();
            return counts.getOrDefault("*", 0) > 0
                    || counts.getOrDefault(ammoId.toString(), 0) > 0;
        }
        return false;
    }

    /**
     * 创造箱对账（首次访问该网络时执行一次）：
     * 物理扫描存储中的创造弹药箱，把缺失的虚拟计数补齐（只增不删，删除走 delta 事件），
     * 修正结果落盘——兜底 delta 订阅建立前/存档恢复期的计数缺口。
     */
    private static void reconcileCreativeBoxes(DimensionsNet net) {
        if (!(net instanceof TaczCreativeAccessor tac)) return;
        Map<String, Integer> counts = tac.getTaczCreativeCounts();
        boolean changed = false;
        var opt = net.getUnifiedStorage().getBucket(ItemStackKey.ID);
        if (opt.isEmpty()) return;
        var bucket = opt.get();
        for (int i = 0; i < bucket.size(); i++) {
            IStackKey<?> rawKey = bucket.get(i);
            if (!(rawKey instanceof ItemStackKey ik)) continue;
            ItemStack stack = ik.getReadOnlyStack();
            if (!(stack.getItem() instanceof IAmmoBox box)) continue;
            if (box.isAllTypeCreative(stack)) {
                if (counts.getOrDefault("*", 0) <= 0) {
                    counts.put("*", 1);
                    changed = true;
                }
            } else if (box.isCreative(stack)) {
                ResourceLocation ammoId = box.getAmmoId(stack);
                if (ammoId != null && counts.getOrDefault(ammoId.toString(), 0) <= 0) {
                    counts.put(ammoId.toString(), 1);
                    changed = true;
                }
            }
        }
        if (changed) NetworkAmmoData.get().setDirty();
    }
}
