package com.solr98.beyondintegration.client;
import com.solr98.beyondintegration.network.AmmoCountResponsePacket;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.RequestAmmoCountPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * TACZ 弹药数量客户端缓存（静态工具类）。
 * 缓存弹药 ID → (网络 ID → 条目) 的计数映射，供 HUD 显示；
 * 服务端响应先暂存，按 10 tick 节流统一应用到缓存，降低主线程压力。
 */
public class TaczAmmoCache {
    private static final Map<String, Map<Integer, AmmoCountResponsePacket.NetEntry>> cache = new HashMap<>(); // 弹药 ID → 各网络计数条目
    private static final Set<String> responded = new HashSet<>(); // 已收到响应的弹药 ID 集合
    private static String pendingQuickId = null; // 进行中的快捷请求弹药 ID（去重）
    private static String pendingFullId = null; // 进行中的全量请求弹药 ID（去重）
    private static long lastFullUpdateMs = 0; // 上次全量请求时间
    private static final long FULL_INTERVAL_MS = 6000; // 全量请求最小间隔
    private static long pendingQuickTime = 0; // 快捷请求发起时间（超时复位，防服务端无响应时永久卡死）
    private static final long QUICK_TIMEOUT_MS = 2000; // 快捷请求超时（毫秒）

    // ── 节流暂存（收到更新先暂存，满 10 tick 且读取时再应用）──
    private static final Map<String, Map<Integer, AmmoCountResponsePacket.NetEntry>> pending = new HashMap<>();
    private static final Set<String> pendingResponded = new HashSet<>();
    private static volatile boolean hasPending = false;
    private static volatile int lastAppliedTick = -1;
    private static final int APPLY_INTERVAL_TICKS = 10;

    public static boolean hasData(ResourceLocation ammoId) {
        applyIfDue();
        return responded.contains(ammoId.toString());
    }

    /** 获取弹药总数量（各网络求和） */
    public static int getCount(ResourceLocation ammoId) {
        applyIfDue();
        Map<Integer, AmmoCountResponsePacket.NetEntry> nets = cache.get(ammoId.toString());
        if (nets == null) return 0;
        long sum = nets.values().stream().mapToLong(e -> (long) e.count()).sum();
        return (int) Math.min(sum, Integer.MAX_VALUE);
    }

    /** 获取各网络独立的弹药数量映射 */
    public static Map<Integer, Integer> getAllNetworkCounts(ResourceLocation ammoId) {
        applyIfDue();
        Map<Integer, AmmoCountResponsePacket.NetEntry> nets = cache.get(ammoId.toString());
        if (nets == null) return Collections.emptyMap();
        Map<Integer, Integer> result = new LinkedHashMap<>();
        nets.forEach((id, e) -> result.put(id, e.count()));
        return result;
    }

    /** 获取网络显示名（自定义名优先，其次翻译键，兜底 Net#id） */
    public static Component getNetworkDisplayName(int netId, ResourceLocation ammoId) {
        applyIfDue();
        Map<Integer, AmmoCountResponsePacket.NetEntry> nets = cache.get(ammoId.toString());
        if (nets == null) return Component.literal("Net#" + netId);
        AmmoCountResponsePacket.NetEntry entry = nets.get(netId);
        if (entry == null) return Component.literal("Net#" + netId);
        String name = entry.customName();
        if (name != null && !name.isEmpty())
            return Component.literal(name + " (Net#" + netId + ")");
        return Component.translatable("menu.text.beyonddimensions.net.default_name", netId);
    }

    /** 快捷请求弹药计数（同 ID 去重 + 超时复位：服务端无响应时不会永久卡死） */
    public static void requestQuick(ResourceLocation ammoId) {
        String id = ammoId.toString();
        long now = System.currentTimeMillis();
        if (id.equals(pendingQuickId) && now - pendingQuickTime < QUICK_TIMEOUT_MS) return;
        pendingQuickId = id;
        pendingQuickTime = now;
        PacketHandler.sendToServer(new RequestAmmoCountPacket(ammoId, true));
    }

    /** 全量请求弹药计数（带 6 秒最小间隔） */
    public static void requestFull(ResourceLocation ammoId) {
        long now = System.currentTimeMillis();
        if (now - lastFullUpdateMs < FULL_INTERVAL_MS) return;
        String id = ammoId.toString();
        if (id.equals(pendingFullId)) return;
        pendingFullId = id;
        lastFullUpdateMs = now;
        PacketHandler.sendToServer(new RequestAmmoCountPacket(ammoId, false));
    }

    /** 请求响应（ammoId 级别整组覆盖）：仅暂存，由 applyIfDue 按节流应用 */
    public static void update(ResourceLocation ammoId, Map<Integer, AmmoCountResponsePacket.NetEntry> networks, boolean quick) {
        pending.put(ammoId.toString(), new LinkedHashMap<>(networks));
        if (quick) pendingQuickId = null;
        else pendingFullId = null;
        pendingQuickTime = 0;
        pendingResponded.add(ammoId.toString());
        hasPending = true;
        applyIfDue();
    }

    /** 服务端轮询推送（netId 级别：该网络条目整体替换为推送列表） */
    /** 全量快照写入（某网络的所有条目整体覆盖该网络的条目） */
    public static void applyPush(int netId, String netName, Map<String, Integer> ammo) {
        replaceNetEntries(pending, netId, netName, ammo);
        for (String id : ammo.keySet()) {
            pendingResponded.add(id);
        }
        hasPending = true;
        applyIfDue();
    }

    /** 用指定网络的新条目替换目标缓存中该网络的所有旧条目 */
    private static void replaceNetEntries(Map<String, Map<Integer, AmmoCountResponsePacket.NetEntry>> target,
                                          int netId, String netName, Map<String, Integer> ammo) {
        target.values().forEach(m -> m.entrySet().removeIf(e -> e.getKey() == netId));
        for (var entry : ammo.entrySet()) {
            target.computeIfAbsent(entry.getKey(), k -> new LinkedHashMap<>())
                    .put(netId, new AmmoCountResponsePacket.NetEntry(netId, netName, entry.getValue()));
        }
    }

    /** 若存在暂存且距上次应用满 10 tick（或未在游戏中），则应用暂存数据 */
    private static void applyIfDue() {
        if (!hasPending) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            applyPending();
            return;
        }
        int tick = mc.player.tickCount;
        if (lastAppliedTick >= 0 && tick - lastAppliedTick < APPLY_INTERVAL_TICKS) return;
        applyPending();
    }

    /** 将暂存数据整体合并入正式缓存并清空暂存区 */
    private static void applyPending() {
        for (var entry : pending.entrySet()) {
            cache.put(entry.getKey(), entry.getValue());
        }
        responded.addAll(pendingResponded);
        pending.clear();
        pendingResponded.clear();
        hasPending = false;
        lastAppliedTick = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.tickCount : 0;
    }

    /** 清空全部缓存与请求状态 */
    public static void clear() {
        cache.clear();
        responded.clear();
        pendingQuickId = null;
        pendingFullId = null;
        lastFullUpdateMs = 0;
        pendingQuickTime = 0;
        pending.clear();
        pendingResponded.clear();
        hasPending = false;
        lastAppliedTick = -1;
    }
}
