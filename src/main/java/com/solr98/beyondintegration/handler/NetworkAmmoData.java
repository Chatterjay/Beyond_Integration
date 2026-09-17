package com.solr98.beyondintegration.handler;
import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import java.util.HashMap;
import java.util.Map;

/**
 * 网络级持久化数据（SavedData）：按网络 ID 存储弹药数量、
 * 附魔分离开关、TACZ 创造模式弹药类型等，并负责 NBT 存取。
 */
public class NetworkAmmoData extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 网络 ID → (弹药类型 → 数量) */
    private final Map<Integer, Map<String, Long>> networkAmmo = new HashMap<>();
    /** 网络 ID → 附魔分离开关 */
    private final Map<Integer, Boolean> enchantSeparation = new HashMap<>();
    /** 网络 ID → 自动充电开关 */
    private final Map<Integer, Boolean> energyCharge = new HashMap<>();
    /** 网络 ID → TACZ 创造模式弹药类型集合 */
    private final Map<Integer, java.util.Set<String>> networkCreativeTypes = new HashMap<>();
    /** 网络 ID → 全类型创造标记 */
    private final Map<Integer, Boolean> networkCreativeAllType = new HashMap<>();
    /** 网络 ID → 已献祭激活的工作台 ID 集合 */
    private final Map<Integer, java.util.Set<String>> networkWorkstations = new HashMap<>();

    public NetworkAmmoData() {}

    /** 获取当前服务器主世界的 NetworkAmmoData 实例（无服务器时返回临时实例） */
    public static NetworkAmmoData get() {
        return NetworkDataStore.load();
    }

    /** 获取指定网络的弹药映射（不存在则创建空映射） */
    public Map<String, Long> getAmmoForNet(int netId) {
        return networkAmmo.computeIfAbsent(netId, k -> new HashMap<>());
    }

    /** 覆盖写入指定网络的弹药映射并标记脏数据 */
    public void setAmmoForNet(int netId, Map<String, Long> ammo) {
        networkAmmo.put(netId, new HashMap<>(ammo));
        setDirty();
    }

    /** 查询网络的附魔分离开关（默认关闭） */
    public boolean getEnchantSeparation(int netId) {
        return enchantSeparation.getOrDefault(netId, false);
    }

    /** 设置网络的附魔分离开关并标记脏数据 */
    public void setEnchantSeparation(int netId, boolean v) {
        enchantSeparation.put(netId, v);
        setDirty();
    }

    /** 查询网络的自动充电开关（默认开启） */
    public boolean getEnergyCharge(int netId) {
        return energyCharge.getOrDefault(netId, true);
    }

    /** 设置网络的自动充电开关并标记脏数据 */
    public void setEnergyCharge(int netId, boolean v) {
        energyCharge.put(netId, v);
        setDirty();
    }

    /** 获取网络的 TACZ 创造类型集合（不存在则创建空集合） */
    public java.util.Set<String> getCreativeTypesForNet(int netId) {
        return networkCreativeTypes.computeIfAbsent(netId, k -> new java.util.HashSet<>());
    }

    /** 覆盖写入网络的创造类型集合并标记脏数据 */
    public void setCreativeTypesForNet(int netId, java.util.Set<String> types) {
        networkCreativeTypes.put(netId, new java.util.HashSet<>(types));
        setDirty();
    }

    /** 查询网络的"全类型创造"标记（默认关闭） */
    public boolean getCreativeAllTypeForNet(int netId) {
        return networkCreativeAllType.getOrDefault(netId, false);
    }

    /** 设置网络的"全类型创造"标记并标记脏数据 */
    public void setCreativeAllTypeForNet(int netId, boolean allType) {
        networkCreativeAllType.put(netId, allType);
        setDirty();
    }

    /** 获取网络的已激活工作台集合（不存在则创建空集合） */
    public java.util.Set<String> getActivatedWorkstations(int netId) {
        return networkWorkstations.computeIfAbsent(netId, k -> new java.util.HashSet<>());
    }

    /** 覆盖写入网络的已激活工作台集合并标记脏数据 */
    public void setActivatedWorkstations(int netId, java.util.Set<String> ids) {
        networkWorkstations.put(netId, new java.util.HashSet<>(ids == null ? java.util.Set.of() : ids));
        setDirty();
    }

    /** 查询网络是否已激活指定工作台 */
    public boolean isWorkstationActivated(int netId, String id) {
        return id != null && getActivatedWorkstations(netId).contains(id);
    }

    /** 激活网络的工作台（网络级持久化） */
    public void activateWorkstation(int netId, String id) {
        if (id == null) return;
        if (getActivatedWorkstations(netId).add(id)) setDirty();
    }

    /** 兼容旧接口：获取弹药映射 */
    public Map<String, Long> getSuperbAmmo(int netId) { return getAmmoForNet(netId); }
    /** 兼容旧接口：写入弹药映射 */
    public void setSuperbAmmo(int netId, Map<String, Long> ammo) { setAmmoForNet(netId, ammo); }
    /** 兼容旧接口：查询附魔分离开关 */
    public boolean isEnchantSeparation(int netId) { return getEnchantSeparation(netId); }

    /** 删除指定网络的全部持久化数据并标记脏数据 */
    public void remove(int netId) {
        networkAmmo.remove(netId);
        enchantSeparation.remove(netId);
        energyCharge.remove(netId);

        networkCreativeTypes.remove(netId);
        networkCreativeAllType.remove(netId);
        networkWorkstations.remove(netId);
        setDirty();
    }
    /** 转换为"类型 → 计数"映射（全类型用 "*" 表示） */
    public Map<String, Integer> getTaczCreativeTypeCounts(int netId) {
        java.util.Set<String> types = getCreativeTypesForNet(netId);
        Map<String, Integer> result = new HashMap<>();
        for (String t : types) result.put(t, 1);
        if (getCreativeAllTypeForNet(netId)) result.put("*", 1);
        return result;
    }
    /** 从"类型 → 计数"映射写回创造类型集合（只取键） */
    public void setTaczCreativeTypeCounts(int netId, Map<String, Integer> counts) {
        if (counts == null) { setCreativeTypesForNet(netId, new java.util.HashSet<>()); return; }
        setCreativeTypesForNet(netId, new java.util.HashSet<>(counts.keySet()));
    }

    /** 序列化全部数据到 NBT（弹药/开关/创造类型/全类型标记） */
    @Override
    public @NotNull CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag netsTag = new CompoundTag();
        for (var netEntry : networkAmmo.entrySet()) {
            CompoundTag netTag = new CompoundTag();
            CompoundTag ammoTag = new CompoundTag();
            for (var ammoEntry : netEntry.getValue().entrySet()) ammoTag.putLong(ammoEntry.getKey(), ammoEntry.getValue());
            netTag.put("ammo", ammoTag);
            netsTag.put(String.valueOf(netEntry.getKey()), netTag);
        }
        tag.put("networkAmmo", netsTag);

        CompoundTag esTag = new CompoundTag();
        for (var e : enchantSeparation.entrySet()) esTag.putBoolean(String.valueOf(e.getKey()), e.getValue());
        tag.put("enchantSeparation", esTag);

        CompoundTag ecTag = new CompoundTag();
        for (var e : energyCharge.entrySet()) ecTag.putBoolean(String.valueOf(e.getKey()), e.getValue());
        tag.put("energyCharge", ecTag);

        CompoundTag iesTag = new CompoundTag();

        CompoundTag ctTag = new CompoundTag();
        for (var e : networkCreativeTypes.entrySet()) {
            CompoundTag perNet = new CompoundTag();
            int idx = 0;
            for (String s : e.getValue()) perNet.putString(String.valueOf(idx++), s);
            perNet.putInt("size", e.getValue().size());
            ctTag.put(String.valueOf(e.getKey()), perNet);
        }
        tag.put("creativeTypes", ctTag);

        CompoundTag catTag = new CompoundTag();
        for (var e : networkCreativeAllType.entrySet()) {
            if (e.getValue()) catTag.putBoolean(String.valueOf(e.getKey()), true);
        }
        tag.put("creativeAllType", catTag);

        CompoundTag waTag = new CompoundTag();
        for (var e : networkWorkstations.entrySet()) {
            CompoundTag perNet = new CompoundTag();
            int idx = 0;
            for (String id : e.getValue()) perNet.putString(String.valueOf(idx++), id);
            perNet.putInt("size", idx);
            waTag.put(String.valueOf(e.getKey()), perNet);
        }
        tag.put("workstationActivation", waTag);

        return tag;
    }

    /** 从 NBT 反序列化全部数据（兼容缺失字段；损坏键跳过而非崩溃）。 */
    public static NetworkAmmoData load(CompoundTag tag, HolderLookup.Provider registries) {
        NetworkAmmoData data = new NetworkAmmoData();
        CompoundTag netsTag = tag.getCompound("networkAmmo");
        for (String netKey : netsTag.getAllKeys()) {
            int netId = parseNetId(netKey);
            if (netId < 0) continue;
            CompoundTag netTag = netsTag.getCompound(netKey);
            CompoundTag ammoTag = netTag.getCompound("ammo");
            Map<String, Long> map = new HashMap<>();
            for (String ammoKey : ammoTag.getAllKeys()) map.put(ammoKey, ammoTag.getLong(ammoKey));
            data.networkAmmo.put(netId, map);
        }

        if (tag.contains("enchantSeparation")) {
            CompoundTag esTag = tag.getCompound("enchantSeparation");
            for (String key : esTag.getAllKeys()) {
                int netId = parseNetId(key);
                if (netId >= 0) data.enchantSeparation.put(netId, esTag.getBoolean(key));
            }
        }

        if (tag.contains("energyCharge")) {
            CompoundTag ecTag = tag.getCompound("energyCharge");
            for (String key : ecTag.getAllKeys()) {
                int netId = parseNetId(key);
                if (netId >= 0) data.energyCharge.put(netId, ecTag.getBoolean(key));
            }
        }

        if (tag.contains("creativeTypes")) {
            CompoundTag ctTag = tag.getCompound("creativeTypes");
            for (String netKey : ctTag.getAllKeys()) {
                int netId = parseNetId(netKey);
                if (netId < 0) continue;
                CompoundTag perNet = ctTag.getCompound(netKey);
                int size = perNet.getInt("size");
                java.util.Set<String> types = new java.util.HashSet<>();
                for (int i = 0; i < size; i++) types.add(perNet.getString(String.valueOf(i)));
                if (!types.isEmpty()) data.networkCreativeTypes.put(netId, types);
            }
        }

        if (tag.contains("creativeAllType")) {
            CompoundTag catTag = tag.getCompound("creativeAllType");
            for (String key : catTag.getAllKeys()) {
                int netId = parseNetId(key);
                if (netId >= 0) data.networkCreativeAllType.put(netId, true);
            }
        }

        if (tag.contains("workstationActivation")) {
            CompoundTag waTag = tag.getCompound("workstationActivation");
            for (String netKey : waTag.getAllKeys()) {
                int netId = parseNetId(netKey);
                if (netId < 0) continue;
                CompoundTag perNet = waTag.getCompound(netKey);
                int size = perNet.getInt("size");
                java.util.Set<String> ids = new java.util.HashSet<>();
                for (int i = 0; i < size; i++) {
                    String id = perNet.getString(String.valueOf(i));
                    if (!id.isEmpty()) ids.add(id);
                }
                if (!ids.isEmpty()) data.networkWorkstations.put(netId, ids);
            }
        }

        return data;
    }

    /**
     * 旧版格式解析（{@code beyond_integration_attachments}，Networks 结构），
     * 用于读取存档时的兼容迁移：SuperbAmmo → networkAmmo；
     * TaczCreativeTypes（类型 → 计数，含 "*" 全类型标记）→ creativeTypes 集合 + creativeAllType；
     * EnchantSeparation → enchantSeparation。
     */
    public static NetworkAmmoData loadLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        NetworkAmmoData data = new NetworkAmmoData();
        CompoundTag networks = tag.getCompound("Networks");
        for (String netKey : networks.getAllKeys()) {
            int netId = parseNetId(netKey);
            if (netId < 0) continue;
            CompoundTag netTag = networks.getCompound(netKey);
            if (netTag.contains("SuperbAmmo")) {
                CompoundTag ammoTag = netTag.getCompound("SuperbAmmo");
                Map<String, Long> map = new HashMap<>();
                for (String ak : ammoTag.getAllKeys()) map.put(ak, ammoTag.getLong(ak));
                if (!map.isEmpty()) data.networkAmmo.put(netId, map);
            }
            if (netTag.contains("TaczCreativeTypes")) {
                CompoundTag ctTag = netTag.getCompound("TaczCreativeTypes");
                java.util.Set<String> types = new java.util.HashSet<>();
                for (String tk : ctTag.getAllKeys()) {
                    if ("*".equals(tk)) data.networkCreativeAllType.put(netId, true);
                    else types.add(tk);
                }
                if (!types.isEmpty()) data.networkCreativeTypes.put(netId, types);
            }
            if (netTag.contains("EnchantSeparation")) {
                data.enchantSeparation.put(netId, netTag.getBoolean("EnchantSeparation"));
            }
        }
        return data;
    }

    /** 解析网络 ID 键（损坏键返回 -1，保证存档可加载）。 */
    private static int parseNetId(String key) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException e) {
            LOGGER.warn("NetworkAmmoData: skipping corrupted net key '{}'", key);
            return -1;
        }
    }
}

