package com.solr98.beyondintegration.handler;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.init.BDDataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 假玩家网络标记工具：记录假玩家绑定的维度网络 ID，
 * 并提供"物品栏/能力处理器 → 假玩家"的反向查找缓存。
 */
public class FakePlayerNetMarker {
    /** 物品栏/能力处理器 → 假玩家的缓存映射（弱引用值，防止泄漏） */
    private static final Map<IItemHandler, FakePlayer> capCache = Collections.synchronizedMap(new WeakHashMap<>());
    // 弱引用：哨兵臂卸载后 FakePlayer 不再被强引用，条目自动回收，避免长期驻留
    /** 全部已标记的假玩家集合（弱引用键） */
    private static final Map<FakePlayer, Boolean> allFakePlayers = Collections.synchronizedMap(new WeakHashMap<>());
    /** 处理器 → 假玩家的显式查找缓存 */
    private static final Map<IItemHandler, FakePlayer> handlerToFakePlayer = Collections.synchronizedMap(new WeakHashMap<>());

    /** 标记假玩家绑定到指定网络，并缓存其物品栏/能力处理器 */
    public static void mark(FakePlayer fp, int netId) {
        ((IFakePlayerNetId) fp).beyond$setNetId(netId);
        cacheHandler(fp);
    }

    /** 解除假玩家的网络绑定（netId 置 -1） */
    public static void unmark(FakePlayer fp) {
        ((IFakePlayerNetId) fp).beyond$setNetId(-1);
    }

    /** 清理假玩家的全部缓存条目（卸载时调用） */
    public static void clearFor(FakePlayer fp) {
        allFakePlayers.remove(fp);
        synchronized (handlerToFakePlayer) {
            handlerToFakePlayer.entrySet().removeIf(e -> e.getValue() == fp);
        }
    }

    /** 获取假玩家绑定的网络 ID */
    public static int getNetId(FakePlayer fp) {
        return ((IFakePlayerNetId) fp).beyond$getNetId();
    }

    /** 获取假玩家绑定的维度网络（未绑定返回 null） */
    @Nullable
    public static DimensionsNet getNet(FakePlayer fp) {
        int id = getNetId(fp);
        return id >= 0 ? DimensionsNet.getNetFromId(id) : null;
    }

    /** 假玩家是否已绑定网络 */
    public static boolean isMarked(FakePlayer fp) {
        return ((IFakePlayerNetId) fp).beyond$getNetId() >= 0;
    }

    /** 根据传入的箱子列表中的网络 ID 组件标记假玩家 */
    public static void markFromBoxes(FakePlayer fp, List<ItemStack> boxes) {
        allFakePlayers.put(fp, true);
        // 找到第一个携带网络 ID 组件的箱子并绑定
        for (ItemStack stack : boxes) {
            if (stack.isEmpty()) continue;
            int netId = stack.getOrDefault(BDDataComponents.NET_ID_DATA, -1);
            if (netId >= 0) {
                mark(fp, netId);
                return;
            }
        }
        // 无有效网络 ID 则解除绑定
        unmark(fp);
    }

    /** 缓存假玩家的物品栏与实体能力处理器映射 */
    private static void cacheHandler(FakePlayer fp) {
        if (fp.getInventory() instanceof IItemHandler handler) {
            capCache.put(handler, fp);
            handlerToFakePlayer.put(handler, fp);
        }
        var cap = fp.getCapability(Capabilities.ItemHandler.ENTITY);
        if (cap != null) {
            capCache.put(cap, fp);
            handlerToFakePlayer.put(cap, fp);
        }
        allFakePlayers.put(fp, true);
    }

    /** 由物品栏/能力处理器反向查找所属假玩家（逐级查缓存，未命中则全量扫描） */
    @Nullable
    public static FakePlayer getFakePlayerFromHandler(IItemHandler handler) {
        if (handler instanceof Inventory inv && inv.player instanceof FakePlayer fp) {
            return fp;
        }
        FakePlayer cached = capCache.get(handler);
        if (cached != null) return cached;
        FakePlayer direct = handlerToFakePlayer.get(handler);
        if (direct != null) return direct;
        for (FakePlayer fp : allFakePlayers.keySet()) {
            if (fp.getInventory() instanceof IItemHandler h && h == handler) {
                handlerToFakePlayer.put(handler, fp);
                return fp;
            }
            var cap = fp.getCapability(Capabilities.ItemHandler.ENTITY);
            if (cap != null && cap == handler) {
                handlerToFakePlayer.put(handler, fp);
                return fp;
            }
        }
        return null;
    }
}

