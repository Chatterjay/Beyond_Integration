package com.solr98.beyondintegration.handler;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家网络使用追踪器：记录玩家最近一次操作所属的维度网络，
 * 在短时间内优先返回该网络，过期后回退到玩家的主网络。
 */
public class PlayerNetUsageTracker {

    /** 一条使用记录：网络 ID 与最近消费时刻 */
    private record Usage(int netId, int lastConsumeTick) {}

    /** 玩家 UUID → 最近使用记录（并发安全） */
    private static final Map<UUID, Usage> usages = new ConcurrentHashMap<>();
    /** 记录有效时长（tick），超时视为过期 */
    private static final int FRESH_TICKS = 100;

    /** 记录玩家最近使用的网络 */
    public static void record(UUID playerUuid, int netId) {
        int tick = ServerLifecycleHooks.getCurrentServer() != null
                ? ServerLifecycleHooks.getCurrentServer().getTickCount() : 0;
        usages.put(playerUuid, new Usage(netId, tick));
    }

    /** 获取玩家当前应使用的网络 ID：记录未过期用记录值，否则回退主网络 */
    public static int getCurrentNetId(ServerPlayer player) {
        Usage usage = usages.get(player.getUUID());
        var server = ServerLifecycleHooks.getCurrentServer();
        if (usage != null && server != null && server.getTickCount() - usage.lastConsumeTick() <= FRESH_TICKS) {
            return usage.netId();
        }
        DimensionsNet primary = DimensionsNet.getPrimaryNetFromPlayer(player);
        return primary != null ? primary.getId() : -1;
    }

    /** 移除指定玩家的使用记录（玩家登出时调用） */
    public static void remove(UUID playerUuid) {
        usages.remove(playerUuid);
    }

    /** 清空全部使用记录 */
    public static void clear() {
        usages.clear();
    }
}
