package com.solr98.beyondintegration.feature.crafting;

import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配方驱动定向扫描器（通用组件，不依赖 TACZ/SW 类型，供各模组工作台网络合成复用）。
 *
 * 两阶段扫描网络存储：
 * 阶段 1（零桶扫描）：全部输入定向精确键查询——IDENTITY 用默认 NBT 键、PARTIAL_NBT 用候选 NBT 键，
 *   每个 O(1) 哈希，总成本 O(材料种类)；
 * 阶段 2（仅定向不足时）：PARTIAL_NBT 输入中"定向数量 < need"的槽，单次全量遍历
 *   按 matcher.test 聚合变种（forge:partial_nbt 子集匹配语义），其余槽沿用阶段 1 结果。
 *
 * 匹配模式：
 * - IDENTITY：候选为普通 item/tag（无 NBT）→ 定向精确键；网络存在"同物品+自定义 NBT"变种时
 *   漏数为接受边界（原版 Ingredient.test 忽略 NBT 的等价近似，消耗与显示一致）；
 * - PARTIAL_NBT：候选为 forge:partial_nbt（物品 + NBT 子集匹配）→ 先定向候选 NBT 键
 *   （合成产物循环入库的"标准形态"材料直接命中），定向不足时全桶兜底保证正确。
 */
public final class RecipeMaterialScanner {

    /** 匹配模式：决定该输入槽的定向策略 */
    public enum MatchMode {
        /** 无 NBT 候选（item/tag）：定向精确键；带 NBT 变种漏数为接受边界 */
        IDENTITY,
        /** forge:partial_nbt 候选：先定向候选 NBT 键（标准形态命中），不足时全桶兜底 */
        PARTIAL_NBT
    }

    /** 一个配方输入槽的抽象 */
    public record MaterialInput(int slotIdx, List<ItemStack> candidates,
                                MatchMode mode, Ingredient matcher, long need) {}

    /** 扫描结果 */
    public record ScanResult(
            Map<Integer, Long> slotTotals,               // 槽位 → 网络可用总量（含兜底聚合）
            Map<Integer, List<ItemStackKey>> slotKeys,   // 槽位 → 可抽取 key 列表（供消耗阶段 extract）
            boolean fallbackScanned) {}                  // 是否触发了全桶兜底

    private RecipeMaterialScanner() {}

    /** 执行两阶段定向扫描 */
    public static ScanResult scan(UnifiedStorage storage, List<MaterialInput> inputs) {
        Map<Integer, Long> slotTotals = new HashMap<>();
        Map<Integer, List<ItemStackKey>> slotKeys = new HashMap<>();

        // ── 阶段 1：定向精确键（零桶扫描）──
        for (MaterialInput mi : inputs) {
            long total = 0;
            List<ItemStackKey> keys = new ArrayList<>();
            for (ItemStack candidate : mi.candidates()) {
                if (candidate.isEmpty()) continue;
                ItemStackKey key = new ItemStackKey(candidate);
                long amount = storage.getStackByKey(key).amount();
                if (amount > 0) {
                    total += amount;
                    keys.add(key);
                }
            }
            slotTotals.put(mi.slotIdx(), total);
            if (!keys.isEmpty()) slotKeys.put(mi.slotIdx(), keys);
        }

        // ── 阶段 2：PARTIAL_NBT 槽定向不足 need → 单次全量遍历聚合变种 ──
        List<MaterialInput> under = new ArrayList<>();
        for (MaterialInput mi : inputs) {
            if (mi.mode() == MatchMode.PARTIAL_NBT
                    && slotTotals.getOrDefault(mi.slotIdx(), 0L) < mi.need()) {
                under.add(mi);
            }
        }
        if (under.isEmpty()) {
            return new ScanResult(slotTotals, slotKeys, false);
        }

        for (KeyAmount ka : storage.getStorage()) {
            if (!(ka.key() instanceof ItemStackKey ik)) continue;
            ItemStack stored = ik.getReadOnlyStack();
            if (stored.isEmpty()) continue;
            long amount = ka.amount();
            if (amount <= 0) continue;
            for (MaterialInput mi : under) {
                Ingredient matcher = mi.matcher();
                if (matcher == null || !matcher.test(stored)) continue;
                slotTotals.merge(mi.slotIdx(), amount, Long::sum);
                List<ItemStackKey> keys = slotKeys.computeIfAbsent(mi.slotIdx(), k -> new ArrayList<>());
                if (!keys.contains(ik)) keys.add(ik);
            }
        }
        return new ScanResult(slotTotals, slotKeys, true);
    }
}
