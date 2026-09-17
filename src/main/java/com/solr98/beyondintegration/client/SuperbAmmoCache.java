package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.RequestItemAmmoPacket;
import com.solr98.beyondintegration.network.RequestSuperbAmmoStatusPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SW 网络缓存（按网络一份快照）。
 * 快照表 netId → NetSnapshot（固定项）；currentNetId/vehicleNetId 为当前指针。
 * ITEM 型弹药计数独立短 TTL 缓存（现查现用，5s 过期）。
 */
public class SuperbAmmoCache {

    private static final Object LOCK = new Object();

    /** 单个网络的弹药快照（不可变数据 + 更新时间戳） */
    public static final class NetSnapshot {
        /** 弹药键 → 数量 */
        public final Map<String, Long> ammo;
        /** 网络能量 */
        public final long energy;
        /** 网络名称 */
        public final String netName;
        /** 是否启用附魔分离 */
        public final boolean enchantSeparation;
        /** 最近一次更新时间戳 */
        public final long lastUpdate;
        /** 该网络可用的弹药类型列表 */
        public final List<String> ammoList;

        NetSnapshot(Map<String, Long> ammo, long energy, String netName, boolean enchantSeparation,
                    long lastUpdate, List<String> ammoList) {
            this.ammo = ammo;
            this.energy = energy;
            this.netName = netName;
            this.enchantSeparation = enchantSeparation;
            this.lastUpdate = lastUpdate;
            this.ammoList = ammoList;
        }
    }

    /** 网络 ID → 弹药快照 表 */
    private static final Map<Integer, NetSnapshot> snapshots = new HashMap<>();

    // ── 当前指针 ──
    private static int currentNetId = -1;
    private static int vehicleNetId = -1;

    // ── 玩家网络拉取节流 ──
    private static boolean requested = false;
    private static long lastRequest = 0;
    private static final long UPDATE_INTERVAL = 5000;
    private static final long REQUEST_TIMEOUT = 2000;

    // ── ITEM 现查缓存（TTL 5s，区分玩家/载具来源）──
    private static final Map<String, ItemEntry> itemCache = new HashMap<>();
    private static final long ITEM_TTL = 5000;
    private static boolean itemRequestPending = false;
    private static long itemLastRequest = 0;
    private static final long ITEM_REQUEST_TIMEOUT = 2000;
    /** 请求中的 netId → 来源标记（玩家/载具），响应按 netId 匹配，避免交错请求标错来源 */
    private static final Map<Integer, Boolean> itemVehicleByNet = new HashMap<>();

    private record ItemEntry(long count, long ts, boolean vehicle) {}

    // ═══════════ S2C 写入 ═══════════

    /** 全量快照（SuperbAmmoStatusResponsePacket）。netId < 0 视为 reset（仅载具解绑场景，清载具指针）。 */
    public static void update(int netId, boolean isVehicle, String netName, Map<String, Long> ammo,
                              long energy, boolean enchantSeparation, List<String> ammoList) {
        synchronized (LOCK) {
            if (netId < 0) {
                vehicleNetId = -1;
                clearItems(true);
                return;
            }
            NetSnapshot snap = new NetSnapshot(new HashMap<>(ammo), energy,
                    netName != null ? netName : "", enchantSeparation,
                    System.currentTimeMillis(), ammoList != null ? new ArrayList<>(ammoList) : null);
            snapshots.put(netId, snap);
            // 服务端推送的附魔分离状态同步到独立缓存（与 SW 快照解耦）
            EnchantSeparationState.set(netId, enchantSeparation);
            if (isVehicle) {
                vehicleNetId = netId;
            } else {
                currentNetId = netId;
                requested = false;
            }
        }
    }

    /** 增量/全量替换（SuperbAmmoDeltaS2CPacket）。full=true 时 ammo 为全量、energy 为绝对值。 */
    public static void applyDelta(int netId, boolean isVehicle, boolean full, Map<String, Long> ammo,
                                  long energy, String netName, boolean enchantSeparation) {
        synchronized (LOCK) {
            if (netId < 0) {
                update(-1, isVehicle, null, null, -1, true, null);
                return;
            }
            NetSnapshot snap = snapshots.get(netId);
            if (snap == null) {
                // 无本地快照无法应用增量：触发全量拉取
                if (!isVehicle) {
                    if (canRequest()) {
                        markRequested();
                        PacketHandler.sendToServer(new RequestSuperbAmmoStatusPacket());
                    }
                }
                return;
            }

            Map<String, Long> newAmmo;
            if (full) {
                newAmmo = new HashMap<>(ammo);
            } else {
                newAmmo = new HashMap<>(snap.ammo);
                for (var entry : ammo.entrySet()) {
                    long v = newAmmo.getOrDefault(entry.getKey(), 0L) + entry.getValue();
                    if (v <= 0) newAmmo.remove(entry.getKey());
                    else newAmmo.put(entry.getKey(), v);
                }
            }
            long newEnergy = full ? energy : snap.energy + energy;
            NetSnapshot ns = new NetSnapshot(newAmmo, newEnergy,
                    netName != null && !netName.isEmpty() ? netName : snap.netName,
                    enchantSeparation, System.currentTimeMillis(), snap.ammoList);
            snapshots.put(netId, ns);
            // 服务端推送的附魔分离状态同步到独立缓存（与 SW 快照解耦）
            EnchantSeparationState.set(netId, enchantSeparation);
            if (isVehicle) {
                vehicleNetId = netId;
            } else {
                currentNetId = netId;
                requested = false;
            }
        }
    }

    /** ITEM 现查响应写入（ItemAmmoResponsePacket）。来源标记按 netId 匹配请求时记录值。 */
    public static void updateItemCounts(int netId, Map<String, Long> counts) {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            Boolean vehicle = itemVehicleByNet.remove(netId);
            boolean isVehicle = vehicle != null && vehicle;
            for (var entry : counts.entrySet()) {
                itemCache.put(entry.getKey(), new ItemEntry(entry.getValue(), now, isVehicle));
            }
            itemRequestPending = false;
        }
    }

    // ═══════════ 玩家侧 API（操作 currentNetId 快照）═══════════

    public static boolean hasData() {
        synchronized (LOCK) {
            return snapshots.get(currentNetId) != null;
        }
    }

    /** 获取当前玩家网络的 ID */
    public static int getNetId() {
        synchronized (LOCK) {
            return currentNetId;
        }
    }

    /** 获取当前玩家网络名称（无数据时返回空串） */
    public static String getNetworkName() {
        synchronized (LOCK) {
            NetSnapshot snap = snapshots.get(currentNetId);
            return snap != null ? snap.netName : "";
        }
    }

    /** 获取当前玩家网络能量（无数据返回 -1） */
    public static long getNetworkEnergy() {
        synchronized (LOCK) {
            NetSnapshot snap = snapshots.get(currentNetId);
            return snap != null ? snap.energy : -1;
        }
    }

    /** 获取当前玩家网络指定弹药计数（缺失返回 0） */
    public static long getCount(String key) {
        synchronized (LOCK) {
            NetSnapshot snap = snapshots.get(currentNetId);
            return snap != null ? snap.ammo.getOrDefault(key, 0L) : 0L;
        }
    }

    /** 当前玩家网络数据是否已过期（超过 5s 未更新） */
    public static boolean isStale() {
        synchronized (LOCK) {
            NetSnapshot snap = snapshots.get(currentNetId);
            return snap == null || System.currentTimeMillis() - snap.lastUpdate > UPDATE_INTERVAL;
        }
    }

    /** 是否可发起全量拉取（未请求过，或距上次请求已超过 2s） */
    public static boolean canRequest() {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            if (!requested) return true;
            if (now - lastRequest > REQUEST_TIMEOUT) {
                requested = false;
                return true;
            }
            return false;
        }
    }

    /** 标记已发起请求并记录时间戳（配合 canRequest 节流） */
    public static void markRequested() {
        synchronized (LOCK) {
            requested = true;
            lastRequest = System.currentTimeMillis();
        }
    }

    /** 当前网络是否启用附魔分离（默认启用；读取独立状态，不依赖 SW 快照） */
    public static boolean getEnchantSeparation() {
        synchronized (LOCK) {
            return EnchantSeparationState.get(currentNetId);
        }
    }

    /** 更新当前网络的附魔分离标记（独立状态，不依赖 SW 快照） */
    public static void setEnchantSeparation(boolean v) {
        synchronized (LOCK) {
            EnchantSeparationState.set(currentNetId, v);
        }
    }

    /** 当前网络是否启用自动充电（默认启用；读取独立状态，不依赖 SW 快照） */
    public static boolean getEnergyCharge() {
        synchronized (LOCK) {
            return EnergyChargeState.get(currentNetId);
        }
    }

    /** 更新当前网络的自动充电标记（独立状态，不依赖 SW 快照） */
    public static void setEnergyCharge(boolean v) {
        synchronized (LOCK) {
            EnergyChargeState.set(currentNetId, v);
        }
    }

    // ═══════════ 载具侧 API（操作 vehicleNetId 快照）═══════════

    /** 载具网络是否有快照数据 */
    public static boolean vehicleHasData() {
        synchronized (LOCK) {
            return snapshots.get(vehicleNetId) != null;
        }
    }

    /** 获取载具网络 ID */
    public static int getVehicleNetId() {
        synchronized (LOCK) {
            return vehicleNetId;
        }
    }

    /** 获取载具网络名称（无数据时返回空串） */
    public static String getVehicleNetName() {
        synchronized (LOCK) {
            NetSnapshot snap = snapshots.get(vehicleNetId);
            return snap != null ? snap.netName : "";
        }
    }

    /** 获取载具网络能量（无数据返回 -1） */
    public static long getVehicleEnergy() {
        synchronized (LOCK) {
            NetSnapshot snap = snapshots.get(vehicleNetId);
            return snap != null ? snap.energy : -1;
        }
    }

    /** 获取载具网络指定弹药计数（缺失返回 0） */
    public static long getVehicleCount(String key) {
        synchronized (LOCK) {
            NetSnapshot snap = snapshots.get(vehicleNetId);
            return snap != null ? snap.ammo.getOrDefault(key, 0L) : 0L;
        }
    }

    /** 获取载具网络的弹药类型列表副本（无数据时返回空列表） */
    public static List<String> getVehicleAmmoList() {
        synchronized (LOCK) {
            NetSnapshot snap = snapshots.get(vehicleNetId);
            return snap != null && snap.ammoList != null ? new ArrayList<>(snap.ammoList) : new ArrayList<>();
        }
    }

    // ═══════════ ITEM 现查（玩家/载具通用）═══════════

    /** 读取 ITEM 计数（TTL 内有效，过期返回 -1 表示需重新现查） */
    public static long getItemCount(String itemKey, boolean vehicle) {
        synchronized (LOCK) {
            ItemEntry entry = itemCache.get(itemKey);
            if (entry == null || entry.vehicle() != vehicle) return -1;
            if (System.currentTimeMillis() - entry.ts() > ITEM_TTL) {
                itemCache.remove(itemKey);
                return -1;
            }
            return entry.count();
        }
    }

    /** 收集列表中缺失/过期的条目（用于批量现查请求） */
    public static List<String> getMissingItemKeys(List<String> itemKeys, boolean vehicle) {
        synchronized (LOCK) {
            List<String> missing = new ArrayList<>();
            for (String key : itemKeys) {
                if (getItemCount(key, vehicle) < 0) missing.add(key);
            }
            return missing;
        }
    }

    /** 发起 ITEM 现查（2s 节流去重；按 netId 记录来源标记，响应时匹配） */
    public static void requestItems(int netId, List<String> itemKeys, boolean vehicle) {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            if (itemRequestPending) {
                if (now - itemLastRequest <= ITEM_REQUEST_TIMEOUT) return;
                itemRequestPending = false;
            }
            itemRequestPending = true;
            itemLastRequest = now;
            itemVehicleByNet.put(netId, vehicle);
        }
        PacketHandler.sendToServer(new RequestItemAmmoPacket(netId, itemKeys));
    }

    // ═══════════ 清理 ═══════════

    /** 下车清除：清载具指针与载具来源 ITEM 条目（共享快照保留） */
    public static void clearVehicleData() {
        synchronized (LOCK) {
            vehicleNetId = -1;
            clearItems(true);
            itemVehicleByNet.values().removeIf(v -> v);
        }
    }

    private static void clearItems(boolean vehicleOnly) {
        itemCache.entrySet().removeIf(e -> !vehicleOnly || e.getValue().vehicle());
    }

    /** 清空全部快照、指针与 ITEM 缓存（登出/重进服时调用） */
    public static void clear() {
        synchronized (LOCK) {
            snapshots.clear();
            currentNetId = -1;
            vehicleNetId = -1;
            requested = false;
            itemCache.clear();
            itemRequestPending = false;
            itemVehicleByNet.clear();
        }
    }
}
