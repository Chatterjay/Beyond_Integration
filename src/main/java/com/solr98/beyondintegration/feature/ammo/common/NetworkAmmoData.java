package com.solr98.beyondintegration.feature.ammo.common;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网络弹药附加数据（SavedData 存档持久化）
 * 以网络 ID 为键保存 SW 虚拟弹药、TACZ 创造弹药箱与附魔分离开关，
 * 随服务器世界存档读写，并提供静态实例访问与脏标记接口。
 *
 * <p>NBT 存储格式与 1.21.1 版本统一（文件键名统一为
 * {@code beyond_integration_data}）：networkAmmo / enchantSeparation /
 * creativeTypes / creativeAllType 四段；旧版 {@code beyond_integration_attachments}
 * （Networks 结构）存档由 {@link #loadLegacy(CompoundTag)} 在读取时兼容迁移。
 */
public class NetworkAmmoData extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 存档数据条目名称（与 1.21.1 统一为 beyond_integration_data） */
    private static final String NAME = "beyond_integration_data";
    /** 网络ID -> 附加数据（并发安全的静态缓存） */
    private static final Map<Integer, Attachment> data = new ConcurrentHashMap<>();

    /** 全局单例实例 */
    private static NetworkAmmoData instance;

    /**
     * 初始化/加载存档数据（不存在则新建）
     * 新建/加载前清空静态缓存，防止切换世界/存档时残留旧数据被写入新存档。
     */
    public static void initialize(ServerLevel level) {
        data.clear();
        instance = level.getDataStorage().computeIfAbsent(NetworkAmmoData::load, NetworkAmmoData::new, NAME);
    }

    /**
     * 标记数据已修改（触发自动保存）
     */
    public static void markDirty() {
        if (instance != null) instance.setDirty();
    }

    /**
     * 获取当前已初始化的活动实例（主世界加载后可用；未初始化返回 null）。
     * 供 NetworkDataStore 兜底读取/强制落盘使用。
     */
    public static NetworkAmmoData activeInstance() {
        return instance;
    }

    /** 空构造：供 SavedData 创建新实例使用 */
    public NetworkAmmoData() {}

    /**
     * 从 NBT 反序列化加载数据（统一新格式；加载前清空静态缓存，避免跨存档数据残留）。
     */
    public static NetworkAmmoData load(CompoundTag tag) {
        // 静态 map 跨存档残留清理：新存档加载前清空，避免旧存档数据污染
        data.clear();

        // 1) networkAmmo：网络 ID → {"ammo": {弹药类型: 数量}}
        CompoundTag netsTag = tag.getCompound("networkAmmo");
        for (String key : netsTag.getAllKeys()) {
            int netId = parseNetId(key);
            if (netId < 0) continue;
            CompoundTag netTag = netsTag.getCompound(key);
            CompoundTag ammoTag = netTag.getCompound("ammo");
            Attachment att = new Attachment();
            for (String ak : ammoTag.getAllKeys()) {
                att.superbAmmo.put(ak, ammoTag.getLong(ak));
            }
            data.put(netId, att);
        }

        // 2) enchantSeparation：网络 ID → 附魔分离开关
        CompoundTag esTag = tag.getCompound("enchantSeparation");
        for (String key : esTag.getAllKeys()) {
            int netId = parseNetId(key);
            if (netId < 0) continue;
            data.computeIfAbsent(netId, k -> new Attachment()).enchantSeparation = esTag.getBoolean(key);
        }

        // 2b) energyCharge：网络 ID → 自动充电开关
        CompoundTag ecTag = tag.getCompound("energyCharge");
        for (String key : ecTag.getAllKeys()) {
            int netId = parseNetId(key);
            if (netId < 0) continue;
            data.computeIfAbsent(netId, k -> new Attachment()).energyCharge = ecTag.getBoolean(key);
        }

        // 3) creativeTypes：网络 ID → {"size": n, "0".."n-1": 弹药类型}
        CompoundTag ctTag = tag.getCompound("creativeTypes");
        for (String key : ctTag.getAllKeys()) {
            int netId = parseNetId(key);
            if (netId < 0) continue;
            CompoundTag perNet = ctTag.getCompound(key);
            int size = perNet.getInt("size");
            Attachment att = data.computeIfAbsent(netId, k -> new Attachment());
            for (int i = 0; i < size; i++) {
                att.taczCreativeTypeCounts.put(perNet.getString(String.valueOf(i)), 1);
            }
        }

        // 4) creativeAllType：网络 ID → 全类型创造标记（"*" 计数 1）
        CompoundTag catTag = tag.getCompound("creativeAllType");
        for (String key : catTag.getAllKeys()) {
            int netId = parseNetId(key);
            if (netId < 0) continue;
            data.computeIfAbsent(netId, k -> new Attachment()).taczCreativeTypeCounts.put("*", 1);
        }

        // 5) workstationActivation：网络 ID → {"size": n, "0".."n-1": 工作台ID}
        CompoundTag waTag = tag.getCompound("workstationActivation");
        for (String key : waTag.getAllKeys()) {
            int netId = parseNetId(key);
            if (netId < 0) continue;
            CompoundTag perNet = waTag.getCompound(key);
            int size = perNet.getInt("size");
            Attachment att = data.computeIfAbsent(netId, k -> new Attachment());
            for (int i = 0; i < size; i++) {
                String id = perNet.getString(String.valueOf(i));
                if (!id.isEmpty()) att.activatedWorkstations.add(id);
            }
        }

        if (instance == null) instance = new NetworkAmmoData();
        return instance;
    }

    /**
     * 旧版格式（{@code beyond_integration_attachments}，Networks 结构）解析，
     * 用于读取存档时的兼容迁移；解析结果与 {@link #load} 一致地填充静态缓存。
     */
    public static NetworkAmmoData loadLegacy(CompoundTag tag) {
        data.clear();
        CompoundTag networks = tag.getCompound("Networks");
        for (String key : networks.getAllKeys()) {
            int netId = parseNetId(key);
            if (netId < 0) continue;
            CompoundTag netTag = networks.getCompound(key);
            Attachment att = new Attachment();
            if (netTag.contains("SuperbAmmo")) {
                CompoundTag ammoTag = netTag.getCompound("SuperbAmmo");
                for (String ak : ammoTag.getAllKeys()) {
                    att.superbAmmo.put(ak, ammoTag.getLong(ak));
                }
            }
            if (netTag.contains("TaczCreativeTypes")) {
                CompoundTag ctTag = netTag.getCompound("TaczCreativeTypes");
                for (String tk : ctTag.getAllKeys()) {
                    att.taczCreativeTypeCounts.put(tk, ctTag.getInt(tk));
                }
            }
            att.enchantSeparation = netTag.contains("EnchantSeparation") && netTag.getBoolean("EnchantSeparation");
            data.put(netId, att);
        }
        if (instance == null) instance = new NetworkAmmoData();
        return instance;
    }

    /**
     * 将内存数据序列化写入 NBT（统一新格式，与 1.21.1 一致）。
     */
    @Override
    public CompoundTag save(CompoundTag tag) {
        // 1) networkAmmo：网络 ID → {"ammo": {弹药类型: 数量}}
        CompoundTag netsTag = new CompoundTag();
        for (var entry : data.entrySet()) {
            CompoundTag netTag = new CompoundTag();
            CompoundTag ammoTag = new CompoundTag();
            for (var e : entry.getValue().superbAmmo.entrySet()) {
                ammoTag.putLong(e.getKey(), e.getValue());
            }
            netTag.put("ammo", ammoTag);
            netsTag.put(String.valueOf(entry.getKey()), netTag);
        }
        tag.put("networkAmmo", netsTag);

        // 2) enchantSeparation：网络 ID → 附魔分离开关
        CompoundTag esTag = new CompoundTag();
        for (var entry : data.entrySet()) {
            esTag.putBoolean(String.valueOf(entry.getKey()), entry.getValue().enchantSeparation);
        }
        tag.put("enchantSeparation", esTag);

        // 2b) energyCharge：网络 ID → 自动充电开关
        CompoundTag ecTag = new CompoundTag();
        for (var entry : data.entrySet()) {
            ecTag.putBoolean(String.valueOf(entry.getKey()), entry.getValue().energyCharge);
        }
        tag.put("energyCharge", ecTag);

        CompoundTag iesTag = new CompoundTag();
        for (var entry : data.entrySet()) {
        }

        // 3) creativeTypes：网络 ID → {"size": n, "0".."n-1": 弹药类型}（"*" 全类型标记排除）
        CompoundTag ctTag = new CompoundTag();
        for (var entry : data.entrySet()) {
            CompoundTag perNet = new CompoundTag();
            int idx = 0;
            for (String s : entry.getValue().taczCreativeTypeCounts.keySet()) {
                if ("*".equals(s)) continue;
                perNet.putString(String.valueOf(idx++), s);
            }
            perNet.putInt("size", idx);
            ctTag.put(String.valueOf(entry.getKey()), perNet);
        }
        tag.put("creativeTypes", ctTag);

        // 4) creativeAllType：网络 ID → 全类型创造标记（仅 true 写入）
        CompoundTag catTag = new CompoundTag();
        for (var entry : data.entrySet()) {
            if (entry.getValue().taczCreativeTypeCounts.containsKey("*")) {
                catTag.putBoolean(String.valueOf(entry.getKey()), true);
            }
        }
        tag.put("creativeAllType", catTag);

        // 5) workstationActivation：网络 ID → {"size": n, "0".."n-1": 工作台ID}
        CompoundTag waTag = new CompoundTag();
        for (var entry : data.entrySet()) {
            CompoundTag perNet = new CompoundTag();
            int idx = 0;
            for (String id : entry.getValue().activatedWorkstations) {
                perNet.putString(String.valueOf(idx++), id);
            }
            perNet.putInt("size", idx);
            waTag.put(String.valueOf(entry.getKey()), perNet);
        }
        tag.put("workstationActivation", waTag);
        return tag;
    }

    /**
     * 获取指定网络的附加数据（不存在则创建新条目）
     */
    public static Attachment getOrCreate(int netId) {
        return data.computeIfAbsent(netId, k -> new Attachment());
    }

    /**
     * 删除指定网络的附加数据
     */
    public static void remove(int netId) {
        data.remove(netId);
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

    /**
     * 单个网络附加的弹药数据
     */
    public static class Attachment {
        /** SW 虚拟弹药表（弹药类型 -> 数量） */
        private final Map<String, Long> superbAmmo = new HashMap<>();
        /** TACZ 创造弹药箱记录（弹药ID -> 数量，"*" 表示全类型创造） */
        private final Map<String, Integer> taczCreativeTypeCounts = new HashMap<>();
        /** 附魔分离开关（默认关闭） */
        private boolean enchantSeparation = false;
        /** 自动充电开关（网络级，默认开启） */
        private boolean energyCharge = true;
        /** 已献祭激活的工作台 ID 集合（网络级，默认空=全部未激活） */
        private final java.util.Set<String> activatedWorkstations = new java.util.HashSet<>();
        /** 附魔物品(装备)分离开关（默认开启） */

        /** 获取 SW 虚拟弹药表 */
        public Map<String, Long> getSuperbAmmo() { return superbAmmo; }
        /** 整体替换 SW 虚拟弹药表 */
        public void setSuperbAmmo(Map<String, Long> map) {
            superbAmmo.clear();
            superbAmmo.putAll(map);
        }
        /** 获取 TACZ 创造弹药箱记录 */
        public Map<String, Integer> getTaczCreativeTypeCounts() { return taczCreativeTypeCounts; }
        /** 是否启用附魔分离 */
        public boolean isEnchantSeparation() { return enchantSeparation; }
        /** 设置附魔分离开关 */
        public void setEnchantSeparation(boolean v) { this.enchantSeparation = v; }
        /** 是否启用自动充电 */
        public boolean isEnergyCharge() { return energyCharge; }
        /** 设置自动充电开关 */
        public void setEnergyCharge(boolean v) { this.energyCharge = v; }
        /** 已献祭激活的工作台 ID 集合 */
        public java.util.Set<String> getActivatedWorkstations() { return activatedWorkstations; }
        /** 是否启用附魔物品(装备)分离 */
        /** 设置附魔物品(装备)分离开关 */
    }
}
