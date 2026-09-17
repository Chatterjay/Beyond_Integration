package com.solr98.beyondintegration.handler;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每网络一份的 SW 固定项（虚拟弹药 + FE + 网络名 + 附魔开关）快照追踪器。
 * 虚拟弹药存储于 NetworkAmmoData（map 直读，无事件源），采用"对账差分"：
 * 每次 drain 对比当前快照与上次快照，产出增量（delta）或全量。
 * 不依赖任何 storage delta 订阅。
 */
public class SwAmmoTracker {

    /** 各网络的追踪器缓存（网络 ID → 追踪器）。 */
    private static final Map<Integer, SwAmmoTracker> TRACKERS = new ConcurrentHashMap<>();

    /** 所属网络 ID。 */
    private final int netId;
    /** 上次快照的虚拟弹药映射，用于差分对比。 */
    private final Map<String, Long> lastAmmo = new HashMap<>();
    /** 上次快照能量（-1 表示无快照，触发全量输出）。 */
    private long lastEnergy = -1;
    /** 上次快照网络自定义名称。 */
    private String lastName = null;
    /** 上次快照附魔分离开关状态。 */
    private boolean lastEnchant = true;
    /** 脏标记：外部标记为脏时，即使数值相同也输出增量。 */
    private volatile boolean dirty = false;

    private SwAmmoTracker(int netId) {
        this.netId = netId;
    }

    /** 获取（不存在则创建）指定网络的追踪器。 */
    public static SwAmmoTracker getOrCreate(DimensionsNet net) {
        if (net == null) return null;
        return TRACKERS.computeIfAbsent(net.getId(), k -> new SwAmmoTracker(k));
    }

    /** 按网络 ID 移除追踪器（网络销毁时清理）。 */
    public static void removeById(int netId) {
        TRACKERS.remove(netId);
    }

    /** 清空全部追踪器（服务端停止等场景）。 */
    public static void clear() {
        TRACKERS.clear();
    }

    /** 获取所属网络 ID。 */
    public int getNetId() {
        return netId;
    }

    /** 外部（如虚拟弹药写入）标记为脏，即使数值相同也输出增量。 */
    public void markDirty() {
        dirty = true;
    }

    /**
     * 对账差分：对比当前快照与上次快照，输出全量（无快照或元数据变化）或增量；
     * 无变化且未置脏时返回 null。
     */
    public DeltaResult drain(DimensionsNet net) {
        if (net == null) return null;
        if (!(net instanceof SuperbAmmoAccessor acc)) return null;

        Map<String, Long> current = new HashMap<>(acc.getSuperbAmmo());
        long energy = net.getUnifiedStorage().getStackByKey(EnergyStackKey.INSTANCE).amount();
        String netName = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "";
        boolean enchantSep = net instanceof EnchantSeparationAccessor ea && ea.beyond$isEnchantSeparationEnabled();

        boolean metaChanged = !netName.equals(lastName == null ? "" : lastName) || enchantSep != lastEnchant;

        if (lastEnergy < 0 || metaChanged) {
            lastAmmo.clear();
            lastAmmo.putAll(current);
            lastEnergy = energy;
            lastName = netName;
            lastEnchant = enchantSep;
            dirty = false;
            return new DeltaResult(true, new HashMap<>(current), energy, netName, enchantSep);
        }

        if (!dirty && current.equals(lastAmmo) && energy == lastEnergy) {
            return null;
        }

        Map<String, Long> deltas = new HashMap<>();
        for (var entry : current.entrySet()) {
            Long old = lastAmmo.get(entry.getKey());
            long diff = old == null ? entry.getValue() : entry.getValue() - old;
            if (diff != 0) deltas.put(entry.getKey(), diff);
        }
        for (var entry : lastAmmo.entrySet()) {
            if (!current.containsKey(entry.getKey())) {
                deltas.put(entry.getKey(), -entry.getValue());
            }
        }

        lastAmmo.clear();
        lastAmmo.putAll(current);
        long energyDelta = energy - lastEnergy;
        lastEnergy = energy;
        lastName = netName;
        lastEnchant = enchantSep;
        dirty = false;

        return new DeltaResult(false, deltas, energyDelta, netName, enchantSep);
    }

    /** 强制下次 drain 输出全量快照。 */
    public void forceFull() {
        lastEnergy = -1;
    }

    /** 差分结果：full 为 true 时 ammo 为全量、energy 为绝对值，否则均为增量。 */
    public record DeltaResult(boolean full, Map<String, Long> ammo, long energy, String netName,
                              boolean enchantSeparation) {
    }
}
