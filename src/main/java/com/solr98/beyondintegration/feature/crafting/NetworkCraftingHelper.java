package com.solr98.beyondintegration.feature.crafting;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import com.tacz.guns.crafting.GunSmithTableRecipe;

/**
 * 网络合成辅助工具类（纯静态）：提供将网络原料数量与玩家背包数量
 * 合并计算、供合成界面展示的辅助方法。
 */
public final class NetworkCraftingHelper {
    private NetworkCraftingHelper() { throw new AssertionError("No instances"); }

    /**
     * 按配方各原料槽位，从 countProvider 提供的网络计数中计算出
     * 每种原料的网络可用数量数组（索引与配方输入一一对应）。
     */
    public static int[] calcNetworkCounts(String recipeId, GunSmithTableRecipe recipe,
                                           java.util.function.Function<String, Long> countProvider) {
        var inputs = recipe.getInputs();
        if (inputs == null || inputs.isEmpty()) return new int[0];
        int[] counts = new int[inputs.size()];
        for (int i = 0; i < inputs.size(); i++)
            counts[i] = (int) Math.min(countProvider.apply(recipeId + "|" + i), Integer.MAX_VALUE);
        return counts;
    }

    /**
     * 将网络各原料数量合并到玩家计数映射中（按槽位索引对应相加，
     * 结果封顶到 Integer.MAX_VALUE）。
     */
    public static void mergeNetworkCounts(Int2IntArrayMap playerCounts, int[] networkCounts) {
        int max = Math.min(networkCounts.length, playerCounts.size());
        for (int i = 0; i < max; i++) {
            int net = networkCounts[i];
            if (net > 0) { long before = playerCounts.get(i); long after = Math.min(before + net, Integer.MAX_VALUE); playerCounts.put(i, (int) after); }
        }
    }
}

