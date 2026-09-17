package com.solr98.beyondintegration.handler;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.TaczAmmoPushPayload;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tacz 弹药轮询推送服务：按配置间隔（默认 10 tick）对玩家当前所在网络
 * 执行 API 直查（TaczAmmoExtractor.countAllAmmoInNetwork，无服务端缓存），
 * 与上次推送快照比较（仅推送层去重），有变化才推送 TaczAmmoPushPayload。
 *
 * 切枪/换弹事件触发时向玩家即时推送最新快照（绕过轮询延迟）。
 */
public class TaczAmmoPollingService {

    /** 服务端 tick 计数器，用于按配置间隔触发轮询。 */
    private static int tickCounter = 0;

    /** 网络 ID → 上次推送快照（推送层去重基准，非扫桶缓存）。 */
    private static final Map<Integer, Map<String, Integer>> lastPushedByNet = new ConcurrentHashMap<>();

    /** 服务端 tick 后阶段回调：按配置间隔触发全量轮询推送。 */
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        if (!CommandConfig.taczAmmoPollEnabled()) return;
        int interval = CommandConfig.taczAmmoPollIntervalTicks();
        if (interval <= 0) return;

        tickCounter++;
        if (tickCounter % interval != 0) return;

        pollAndPush(server);
    }

    /**
     * 立即推送某网络当前弹药快照给指定玩家（扣弹/切枪/换弹成功后调用，HUD 实时更新）。
     * 不更新轮询去重基线，轮询仍按原逻辑推送（客户端缓存覆盖式更新，重复推送无副作用）。
     */
    public static void pushSnapshotToPlayer(ServerPlayer player, int netId) {
        if (player == null) return;
        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) return;
        Map<String, Integer> snapshot = TaczAmmoExtractor.countAllAmmoInNetwork(net);
        PacketHandler.sendToPlayer(player, new TaczAmmoPushPayload(netId, net.getCustomName(), snapshot));
    }

    /** 汇总玩家当前所在网络，API 直查快照（与上次推送一致则不推）并推送给对应玩家。 */
    private void pollAndPush(MinecraftServer server) {
        Set<Integer> netIds = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int netId = PlayerNetUsageTracker.getCurrentNetId(player);
            if (netId >= 0) netIds.add(netId);
        }

        if (netIds.isEmpty()) return;

        for (int netId : netIds) {
            DimensionsNet net = DimensionsNet.getNetFromId(netId);
            if (net == null) {
                lastPushedByNet.remove(netId);
                continue;
            }
            Map<String, Integer> snapshot = TaczAmmoExtractor.countAllAmmoInNetwork(net);
            Map<String, Integer> last = lastPushedByNet.get(netId);
            if (snapshot.equals(last)) continue;
            lastPushedByNet.put(netId, new HashMap<>(snapshot));

            TaczAmmoPushPayload packet = new TaczAmmoPushPayload(netId, net.getCustomName(), snapshot);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (PlayerNetUsageTracker.getCurrentNetId(player) == netId) {
                    PacketHandler.sendToPlayer(player, packet);
                }
            }
        }
    }

    /** 清空推送去重基线（服务器停止时调用）。 */
    public static void clear() {
        lastPushedByNet.clear();
    }
}
