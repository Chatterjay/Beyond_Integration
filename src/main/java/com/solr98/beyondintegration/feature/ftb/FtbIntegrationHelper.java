package com.solr98.beyondintegration.feature.ftb;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.compat.RsIntegrationCompat;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * FTB Quests 集成助手：
 * <ol>
 *   <li>网络物品计入非消耗型物品任务（含存量，见 {@code FtbPlayerInventorySummaryMixin}）；</li>
 *   <li>消耗型任务提交时背包不足部分从网络补足（{@code FtbItemTaskMixin}）；</li>
 *   <li>任务奖励直接发放进 BD 网络（{@code FtbItemRewardMixin}）。</li>
 * </ol>
 * 总开关：服务端配置 {@code ftb_integration.enable}；检测到 rs_integration（RI）时运行时让路禁用
 * （同时 MixinPlugin 在 RI 加载时不应用相关 mixin）。
 */
public final class FtbIntegrationHelper {

    /** 网络物品快照缓存（按玩家 UUID），带逻辑刻 TTL 避免高频全量扫描 */
    private static final Map<UUID, CachedItems> ITEM_CACHE = new HashMap<>();
    /** 缓存有效期（逻辑刻） */
    private static final long CACHE_TTL_TICKS = 20L;
    /** 单次快照最多枚举的网络物品种类（防止超大网络拖慢任务扫描） */
    private static final int MAX_ITEM_TYPES = 8192;

    private record CachedItems(List<KeyAmount> items, long tick) {}

    private FtbIntegrationHelper() {}

    /** 集成是否启用（配置开关 + RI 让路） */
    public static boolean isEnabled() {
        try {
            return CommandConfig.ftbIntegrationEnabled() && !RsIntegrationCompat.isLoaded();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 玩家主网络的物品快照（未在网络/异常返回空列表）。
     * 按逻辑刻缓存（{@link #CACHE_TTL_TICKS}），避免 FTB 背包扫描高频触发全量枚举。
     */
    public static List<KeyAmount> networkItems(ServerPlayer player) {
        if (player == null) return List.of();
        long tick = player.level().getGameTime();
        CachedItems cached = ITEM_CACHE.get(player.getUUID());
        if (cached != null && tick - cached.tick() < CACHE_TTL_TICKS) {
            return cached.items();
        }
        List<KeyAmount> items = new ArrayList<>();
        try {
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net != null) {
                var storage = net.getUnifiedStorage();
                for (KeyAmount ka : storage.getStorage()) {
                    if (ka == null || ka.amount() <= 0) continue;
                    items.add(ka);
                    if (items.size() >= MAX_ITEM_TYPES) break;
                }
            }
        } catch (Throwable ignored) {}
        ITEM_CACHE.put(player.getUUID(), new CachedItems(items, tick));
        return items;
    }

    /** 玩家退出时清理缓存 */
    public static void clearCache(UUID playerId) {
        ITEM_CACHE.remove(playerId);
    }

    /**
     * 从网络提取匹配物品到玩家背包（最多 amount），返回实际提取数量。
     * 用于消耗型任务提交时补足背包缺口。
     * <p>
     * 仅提取背包可容纳的数量：FTB 原生 {@code submitTask} 只从背包扣料，
     * 若补足时物品落地将无法被扣除（进度不涨且物品脱离网络），因此不产生掉落物。
     */
    public static long supplyToInventory(ServerPlayer player, Predicate<ItemStack> matcher, long amount) {
        if (player == null || matcher == null || amount <= 0) return 0;
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) return 0;
        var storage = net.getUnifiedStorage();
        long supplied = 0;
        try {
            // 复制列表避免遍历期间存储变化
            List<KeyAmount> snapshot = new ArrayList<>(storage.getStorage());
            for (KeyAmount ka : snapshot) {
                if (supplied >= amount) break;
                if (!(ka.key() instanceof ItemStackKey ik)) continue;
                ItemStack stack = ik.getReadOnlyStack();
                if (stack.isEmpty() || !matcher.test(stack)) continue;
                // 只取背包放得下的部分，超出部分保留在网络
                long room = inventoryRoomFor(player, stack);
                long want = Math.min(Math.min(amount - supplied, ka.amount()), room);
                if (want <= 0) continue;
                KeyAmount extracted = storage.extract(ik, want, false, false);
                long got = extracted.amount();
                if (got <= 0) continue;
                long placed = giveToInventory(player, stack, got);
                if (placed < got) {
                    // 防御性回插（理论不会发生）：放置失败部分返还网络，避免物品丢失
                    storage.insert(ik, got - placed, false);
                }
                supplied += placed;
            }
        } catch (Throwable ignored) {}
        if (supplied > 0) net.setDirty();
        return supplied;
    }

    /** 统计背包（含盔甲/副手，与 FTB 提交扣料范围一致）还能容纳多少个该物品 */
    private static long inventoryRoomFor(ServerPlayer player, ItemStack template) {
        long room = 0;
        int max = template.getMaxStackSize();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (slot.isEmpty()) {
                room += max;
            } else if (ItemStack.isSameItemSameTags(slot, template)) {
                room += Math.max(0, max - slot.getCount());
            }
        }
        return room;
    }

    /** 把物品放入背包（不产生掉落物），返回实际放入数量 */
    private static long giveToInventory(ServerPlayer player, ItemStack template, long amount) {
        long placed = 0;
        while (placed < amount) {
            int give = (int) Math.min(amount - placed, template.getMaxStackSize());
            ItemStack giveStack = template.copyWithCount(give);
            player.getInventory().add(giveStack);
            int left = giveStack.getCount();
            if (left >= give) break;  // 完全放不下，避免死循环
            placed += give - left;
        }
        return placed;
    }
}
