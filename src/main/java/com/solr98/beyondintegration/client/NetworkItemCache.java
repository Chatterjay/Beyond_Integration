package com.solr98.beyondintegration.client;
import net.minecraft.network.chat.Component;
import java.util.HashMap;
import java.util.Map;

/**
 * 网络物品数量客户端缓存（静态工具类）。
 * 缓存网络内物品键 → 数量的映射及网络元信息（ID/名称/存在标记），
 * 供工作站界面等读取展示，写入时版本号自增以便界面感知刷新。
 */
public class NetworkItemCache {
    private static Map<String, Long> counts = new HashMap<>(); // 物品键 → 数量
    private static boolean hasNetwork = true; // 是否已连接网络
    private static int netId = -1; // 网络 ID
    private static String netName = ""; // 网络名称
    private static int version = 0; // 缓存版本号（每次 setAll 自增）

    /** 整体覆盖缓存并递增版本号 */
    public static void setAll(Map<String, Long> data, boolean hasNet, int id, String name) {
        counts = new HashMap<>(data);
        hasNetwork = hasNet;
        netId = id;
        netName = name != null ? name : "";
        version++;
    }

    public static int getNetId() { return netId; } // 获取网络 ID
    public static boolean hasNetwork() { return hasNetwork; } // 是否已连接网络
    public static long getCount(String itemKey) { return counts.getOrDefault(itemKey, 0L); } // 获取物品数量（无则 0）
    public static int getVersion() { return version; } // 获取缓存版本号
    public static boolean isEmpty() { return counts.isEmpty(); } // 缓存是否为空

    /** 构建网络显示名（自定义名/翻译键兜底） */
    public static Component getDisplayName() {
        if (!hasNetwork) return Component.translatable("gui.beyond_integration.network.none");
        if (!netName.isEmpty())
            return Component.literal(netName + " (Net#" + netId + ")");
        return Component.translatable("gui.beyond_integration.network.connected", netId >= 0 ? netId : "?");
    }

    /** 清空缓存并重置网络标记 */
    public static void clear() { counts.clear(); hasNetwork = true; netId = -1; netName = ""; }
}

