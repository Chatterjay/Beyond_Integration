package com.solr98.beyondintegration.jei;

import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.payload.ExtractNetworkItemPacket;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * JEI 集成客户端助手：查询当前打开的 BD 终端网络库存，并请求从网络提取物品。
 * 仅在打开 BD 终端界面（{@link DimensionsNetGUI}）时有效，未打开时数量视为 0（不显示角标/不响应点击）。
 * <p>
 * 数量缓存按<b>逻辑刻</b>更新：同一游戏刻内多次查询（多帧渲染/tooltip/点击判定）只查一次存储，
 * 新刻自动清空缓存（容量不超过当前可见物品数，无长期内存增长）。
 */
public final class BeyondJeiNetworkHelper {

    /** 当前刻的数量缓存（key → 数量）；每逻辑刻清空重建 */
    private static final Map<ItemStackKey, Long> COUNT_CACHE = new HashMap<>();
    /** 缓存对应的逻辑刻（-1 表示尚未建立；世界未加载时也视为同一刻） */
    private static long cachedTick = Long.MIN_VALUE;

    private BeyondJeiNetworkHelper() {}

    /** 当前客户端逻辑刻（未进入世界返回 -1） */
    private static long currentTick() {
        var level = Minecraft.getInstance().level;
        return level == null ? -1L : level.getGameTime();
    }

    /** 当前打开的 BD 终端菜单（未打开返回 null） */
    public static DimensionsNetMenu currentNetMenu() {
        var screen = Minecraft.getInstance().screen;
        if (screen instanceof DimensionsNetGUI<?> gui) {
            try {
                return gui.getMenu();
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /** 查询当前终端网络中该存储键的数量（按逻辑刻缓存；未打开终端返回 0） */
    public static long getNetworkCount(ItemStackKey key) {
        if (key == null || key.isEmpty()) return 0;
        long tick = currentTick();
        if (tick != cachedTick) {
            COUNT_CACHE.clear();
            cachedTick = tick;
        }
        Long cached = COUNT_CACHE.get(key);
        if (cached != null) return cached;
        long count = queryNetworkCount(key);
        COUNT_CACHE.put(key, count);
        return count;
    }

    /** 查询当前终端网络中该物品的数量（按逻辑刻缓存；未打开终端返回 0） */
    public static long getNetworkCount(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        return getNetworkCount(new ItemStackKey(stack.copyWithCount(1)));
    }

    /** 实际查询网络库存（未打开终端/异常返回 0） */
    private static long queryNetworkCount(ItemStackKey key) {
        DimensionsNetMenu menu = currentNetMenu();
        if (menu == null || menu.clientNetStorage == null) return 0;
        try {
            return menu.clientNetStorage.getStackByKey(key).amount();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** 请求从网络提取物品到背包（服务端校验权限与库存） */
    public static void requestExtract(ItemStack stack, int amount) {
        if (stack == null || stack.isEmpty() || amount <= 0) return;
        PacketHandler.sendToServer(new ExtractNetworkItemPacket(stack.copyWithCount(1), amount));
    }
}
