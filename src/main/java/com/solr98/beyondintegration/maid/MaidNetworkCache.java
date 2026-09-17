package com.solr98.beyondintegration.maid;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.world.entity.LivingEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 女仆网络缓存：以实体 UUID 为键缓存其绑定的网络 ID，带 5 秒过期时间，
 * 过期或网络已删除时自动失效，避免频繁扫描女仆饰品栏。
 */
public class MaidNetworkCache {
    /** 实体 UUID -> 网络 ID 缓存 */
    private static final Map<UUID, Integer> cache = new HashMap<>();
    /** 实体 UUID -> 缓存写入时间戳 */
    private static final Map<UUID, Long> ts = new HashMap<>();
    /** 缓存有效期（毫秒） */
    private static final long TTL = 5000;

    /** 获取实体的缓存网络；未命中、过期或网络已删除时返回 null 并清理缓存 */
    public static DimensionsNet get(LivingEntity e) {
        Integer id = cache.get(e.getUUID());
        if (id == null) return null;
        Long t = ts.get(e.getUUID());
        if (t == null || System.currentTimeMillis() - t > TTL) { cache.remove(e.getUUID()); ts.remove(e.getUUID()); return null; }
        DimensionsNet n = DimensionsNet.getNetFromId(id);
        if (n == null || n.deleted) { cache.remove(e.getUUID()); ts.remove(e.getUUID()); return null; }
        return n;
    }
    /** 写入缓存并刷新时间戳 */
    public static void put(UUID u, int id) { cache.put(u, id); ts.put(u, System.currentTimeMillis()); }
    /** 移除指定实体的缓存 */
    public static void remove(UUID u) { cache.remove(u); ts.remove(u); }
}

