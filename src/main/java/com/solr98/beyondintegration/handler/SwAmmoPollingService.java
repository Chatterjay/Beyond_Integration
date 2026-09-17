package com.solr98.beyondintegration.handler;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.SuperbAmmoDeltaS2CPacket;
import com.solr98.beyondintegration.network.SuperbAmmoStatusResponsePacket;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SW 固定项轮询对账服务：按配置间隔（默认 10 tick）轮询所有在用网络，
 * 经 SwAmmoTracker 差分产出全量/增量后推送给主网络为该网络的玩家。
 *
 * 同步 TACZ 更新链路：进服时推送一次、主手物品变化（切枪）时即时推送，
 * 扣弹后由 AmmoConsumerMixin 调用 pushSnapshotToPlayer 即时推送。
 */
public class SwAmmoPollingService {

    /** 服务端 tick 计数器，用于按配置间隔触发轮询。 */
    private static int tickCounter = 0;

    /** 主手变化检测节流计数器。 */
    private static int handCheckCounter = 0;
    /** 主手变化检测间隔（tick）。 */
    private static final int HAND_CHECK_INTERVAL = 10;
    /** 玩家 UUID -> 上次主手物品（切枪检测）。 */
    private static final Map<UUID, ItemStack> lastMainHand = new ConcurrentHashMap<>();

    /** 服务端 tick 后阶段回调：按配置间隔触发轮询对账并推送。 */
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        if (!CommandConfig.swAmmoPollEnabled()) return;
        int interval = CommandConfig.swAmmoPollIntervalTicks();
        if (interval <= 0) return;

        tickCounter++;
        if (tickCounter % interval != 0) return;

        pollAndPush(server);
    }

    /** 玩家进服：立即推送一次主网络快照（HUD 初始即有数据，无需等轮询）。 */
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(sp);
        if (net != null) pushSnapshotToPlayer(sp, net);
    }

    /** 主手物品变化检测（SW 版"切枪"）：节流检测主手物品变化，变化时推送主网络快照。 */
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.isDeadOrDying()) return;

        handCheckCounter++;
        if (handCheckCounter % HAND_CHECK_INTERVAL != 0) return;

        UUID uuid = sp.getUUID();
        ItemStack now = sp.getMainHandItem();
        ItemStack last = lastMainHand.get(uuid);
        boolean changed = last == null || !ItemStack.isSameItemSameComponents(last, now);
        lastMainHand.put(uuid, now.copy());
        if (!changed) return;

        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(sp);
        if (net != null) pushSnapshotToPlayer(sp, net);
    }

    /**
     * 立即向指定玩家推送其网络当前快照（全量或增量；无变化不推）。
     * 扣弹/切枪/进服统一入口。
     */
    public static void pushSnapshotToPlayer(ServerPlayer player, DimensionsNet net) {
        if (player == null || net == null) return;
        SwAmmoTracker tracker = SwAmmoTracker.getOrCreate(net);
        SwAmmoTracker.DeltaResult result = tracker.drain(net);
        if (result == null) return;

        if (result.full()) {
            PacketHandler.sendToPlayer(player, new SuperbAmmoStatusResponsePacket(
                    net.getId(), result.netName(), result.energy(), result.enchantSeparation(),
                    result.ammo(), null));
        } else {
            PacketHandler.sendToPlayer(player, new SuperbAmmoDeltaS2CPacket(
                    net.getId(), false, false, result.ammo(), result.energy(),
                    result.netName(), result.enchantSeparation()));
        }
    }

    /** 汇总在线玩家所在的主网络，逐个对账差分并推送（全量或增量）给对应玩家。 */
    private void pollAndPush(MinecraftServer server) {
        Set<Integer> netIds = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            DimensionsNet primary = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (primary != null) netIds.add(primary.getId());
        }
        if (netIds.isEmpty()) return;

        for (int netId : netIds) {
            DimensionsNet net = DimensionsNet.getNetFromId(netId);
            if (net == null) {
                SwAmmoTracker.removeById(netId);
                continue;
            }

            SwAmmoTracker tracker = SwAmmoTracker.getOrCreate(net);
            SwAmmoTracker.DeltaResult result = tracker.drain(net);
            if (result == null) continue;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                DimensionsNet primary = DimensionsNet.getPrimaryNetFromPlayer(player);
                if (primary == null || primary.getId() != netId) continue;

                if (result.full()) {
                    PacketHandler.sendToPlayer(player, new SuperbAmmoStatusResponsePacket(
                            netId, result.netName(), result.energy(), result.enchantSeparation(),
                            result.ammo(), null));
                } else {
                    PacketHandler.sendToPlayer(player, new SuperbAmmoDeltaS2CPacket(
                            netId, false, false, result.ammo(), result.energy(),
                            result.netName(), result.enchantSeparation()));
                }
            }
        }
    }

    /** 清空主手检测缓存（玩家登出时调用）。 */
    public static void onPlayerLoggedOut(UUID uuid) {
        if (uuid != null) lastMainHand.remove(uuid);
    }
}
