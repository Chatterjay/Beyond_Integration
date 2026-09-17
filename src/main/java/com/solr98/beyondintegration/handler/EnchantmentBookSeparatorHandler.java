package com.solr98.beyondintegration.handler;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.init.BDFluids;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 附魔书/附魔物品分离处理器：物品进入维度网络前，将多附魔的附魔书
 * 或附魔物品拆分成单附魔书，消耗经验与空白书作为代价。
 */
public class EnchantmentBookSeparatorHandler implements UnifiedStorageBeforeInsertHandler.BeforeInsertHandler {

    /** 日志记录器 */
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 一条附魔记录：附魔 Holder 与其等级 */
    public record Entry(Holder<Enchantment> holder, int level) {}

    @Override
    public @NotNull UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo beforeInsert(
            @NotNull KeyAmount originalInsert, @NotNull KeyAmount tryInsert, DimensionsNet net) {
        // 调试日志：入口信息（网络/物品/开关）
        if (CommandConfig.enchantDebug()) {
            LOGGER.info("[enchant-sep] beforeInsert net={} item={} amount={} netSwitch={}",
                    net != null ? net.getId() : -1,
                    tryInsert.key() instanceof ItemStackKey ik ? ik.getReadOnlyStack().getHoverName().getString() : tryInsert.key().getClass().getSimpleName(),
                    tryInsert.amount(),
                    net instanceof EnchantSeparationAccessor ea && ea.beyond$isEnchantSeparationEnabled());
        }
        // 网络未启用附魔分离功能时放行
        if (!(net instanceof EnchantSeparationAccessor ea) || !ea.beyond$isEnchantSeparationEnabled()) {
            if (CommandConfig.enchantDebug())
                LOGGER.info("[enchant-sep] pass: net switch off or not accessor");
            return pass(tryInsert);
        }
        return handleSeparation(tryInsert, net);
    }

    /** 主分离流程：仅处理多附魔书（附魔物品/神化装备分离已存档移除，见 archived_features/enchant_item_apoth_separation） */
    private UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo handleSeparation(
            KeyAmount tryInsert, DimensionsNet net) {
        // 配置关闭时放行
        if (!CommandConfig.SERVER.enchantSeparation.get()) {
            if (CommandConfig.enchantDebug()) LOGGER.info("[enchant-sep] pass: global enchantSeparation off");
            return pass(tryInsert);
        }
        // 非物品键放行
        if (!(tryInsert.key() instanceof ItemStackKey itemStackKey)) {
            if (CommandConfig.enchantDebug()) LOGGER.info("[enchant-sep] pass: non-item key {}", tryInsert.key().getClass().getSimpleName());
            return pass(tryInsert);
        }

        ItemStack stack = itemStackKey.getReadOnlyStack().copy();
        // 空物品或无网络放行
        if (stack.isEmpty() || net == null) {
            if (CommandConfig.enchantDebug()) LOGGER.info("[enchant-sep] pass: empty stack or null net");
            return pass(tryInsert);
        }
        // 受保护物品（beyond_integration:protect_sep 标记，GUI 右键切换）：跳过自动分离
        var protectData = stack.get(DataComponents.CUSTOM_DATA);
        if (protectData != null && protectData.copyTag().getBoolean("beyond_integration:protect_sep")) {
            if (CommandConfig.enchantDebug()) LOGGER.info("[enchant-sep] pass: protected item");
            return pass(tryInsert);
        }

        // 仅处理附魔书：STORED_ENCHANTMENTS 组件且附魔数 > 1 时拆分
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored == null || stored.size() <= 1) {
            if (CommandConfig.enchantDebug()) LOGGER.info("[enchant-sep] pass: not a multi-enchanted book");
            return pass(tryInsert);
        }
        List<Entry> ench = new ArrayList<>();
        // 收集所有附魔（排除 0 级）
        for (Holder<Enchantment> holder : stored.keySet()) {
            int level = stored.getLevel(holder);
            if (level > 0) ench.add(new Entry(holder, level));
        }
        // 实际有效附魔 ≤ 1 无需拆分
        if (ench.size() <= 1) {
            if (CommandConfig.enchantDebug()) LOGGER.info("[enchant-sep] pass: book has {} effective enchants", ench.size());
            return pass(tryInsert);
        }
        if (CommandConfig.enchantDebug()) LOGGER.info("[enchant-sep] separate book {} ({} enchants)", stack.getHoverName().getString(), ench.size());
        return separateBook(tryInsert, net, ench);
    }

    /** 拆分附魔书：校验资源/容量/输出空间后，消耗经验与空白书并输出单附魔书 */
    private UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo separateBook(
            KeyAmount tryInsert, DimensionsNet net, List<Entry> ench) {
        long count = tryInsert.amount();
        long cost = calcCost(ench, count);
        // 拆成 (附魔数-1) 本额外空白书
        long booksNeeded = (ench.size() - 1) * count;

        if (!hasResources(net, cost, booksNeeded) || !canStore(net, ench, count)) {
            if (CommandConfig.enchantDebug())
                LOGGER.info("[enchant-sep] book pass: resources(xp={},books={}) or capacity insufficient", cost, booksNeeded);
            return pass(tryInsert);
        }
        if (!canOutput(net, ench, count)) {
            if (CommandConfig.enchantDebug()) LOGGER.info("[enchant-sep] book pass: output space insufficient");
            return pass(tryInsert);
        }

        if (CommandConfig.enchantDebug())
            LOGGER.info("[enchant-sep] book done: xp={} books={} enchants={}", cost, booksNeeded, ench.size());
        consumeExperience(net, cost);
        consumeBooks(net, booksNeeded);
        doOutput(net, ench, count);
        return acceptEmpty();
    }


    /** 判断物品是否带附魔分离保护标记（beyond_integration:protect_sep，GUI Ctrl+右键切换） */
    private static boolean isProtected(ItemStack stack) {
        var protectData = stack.get(DataComponents.CUSTOM_DATA);
        return protectData != null && protectData.copyTag().getBoolean("beyond_integration:protect_sep");
    }

    /** 将每个附魔单独生成一本附魔书并插入网络（公开，供手动批量分离复用） */
    public static void doOutput(DimensionsNet net, List<Entry> ench, long count) {
        for (Entry e : ench) {
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            mutable.set(e.holder, e.level);
            book.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
            net.getUnifiedStorage().insert(new ItemStackKey(book), count, false);
        }
    }

    // ═══════════════════════════════════════════
    //  Manual bulk separation
    // ═══════════════════════════════════════════

    /** 手动批量分离网络内所有多附魔书与附魔物品，返回处理结果消息 */
    public static Component separateAll(DimensionsNet net) {
        if (net == null)
            return Component.translatable("message.beyond_integration.enchant_sep.network_not_exist");

        // 查找可分离的附魔书
        List<KeyAmount> books = findEnchantedBooks(net);
        if (books.isEmpty())
            return Component.translatable("message.beyond_integration.enchant_sep.no_items");

        int totalProcessed = 0;
        long totalXpCost = 0;
        long totalBooksNeeded = 0;

        // 逐个处理附魔书
        for (KeyAmount entry : books) {
            if (!(entry.key() instanceof ItemStackKey ik)) continue;
            ItemStack stack = ik.copyStackWithCount(1);
            // 受保护物品（beyond_integration:protect_sep 标记）：跳过手动批量分离
            if (isProtected(stack)) continue;
            ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
            if (stored == null || stored.size() <= 1) continue;
            List<Entry> ench = new ArrayList<>();
            for (Holder<Enchantment> holder : stored.keySet()) {
                int level = stored.getLevel(holder);
                if (level > 0) ench.add(new Entry(holder, level));
            }
            if (ench.size() <= 1) continue;

            long count = entry.amount();
            long cost = calcCost(ench, count);
            long booksNeeded = (ench.size() - 1) * count;
            if (!hasResources(net, cost, booksNeeded)) continue;
            if (!canStore(net, ench, count)) continue;
            if (!canOutput(net, ench, count)) continue;

            consumeExperience(net, cost);
            consumeBooks(net, booksNeeded);

            // 先扣原多附魔书（检查成功，失败则退回已扣并跳过，防止刷书）
            KeyAmount removed = net.getUnifiedStorage().extract(ik, count, false, false);
            if (removed.amount() < count) {
                if (removed.amount() > 0) net.getUnifiedStorage().insert(ik, removed.amount(), false);
                continue;
            }

            doOutput(net, ench, count);
            totalProcessed += count;
            totalXpCost += cost;
            totalBooksNeeded += booksNeeded;
        }


        Component report = Component.translatable("message.beyond_integration.enchant_sep.result", totalProcessed);
        if (totalXpCost > 0) report = report.copy().append(Component.translatable("message.beyond_integration.enchant_sep.xp_cost", totalXpCost / 20));
        if (totalBooksNeeded > 0) report = report.copy().append(Component.translatable("message.beyond_integration.enchant_sep.book_cost", totalBooksNeeded));
        return report;
    }

    // ═══════════════════════════════════════════
    //  Internal helpers
    // ═══════════════════════════════════════════


    /** 扫描网络中所有多附魔附魔书（数量 > 0） */
    private static List<KeyAmount> findEnchantedBooks(DimensionsNet net) {
        List<KeyAmount> result = new ArrayList<>();
        var opt = net.getUnifiedStorage().getBucket(ItemStackKey.ID);
        if (opt.isEmpty()) return result;
        var bucket = opt.get();
        for (int i = 0; i < bucket.size(); i++) {
            var raw = bucket.get(i);
            if (!(raw instanceof ItemStackKey ik)) continue;
            ItemStack s = ik.getReadOnlyStack();
            if (!s.is(Items.ENCHANTED_BOOK)) continue;
            long amount = net.getUnifiedStorage().getStackByKey(ik).amount();
            if (amount > 0) result.add(new KeyAmount(ik, amount));
        }
        return result;
    }


    /** 计算分离总经验代价（单位经验点 ×20），支持按附魔类型倍率加成 */
    public static long calcCost(List<Entry> ench, long count) {
        long total = 0;
        int base = CommandConfig.SERVER.enchantBaseCost.get();
        double lvlMult = CommandConfig.SERVER.enchantLevelMult.get();
        for (Entry e : ench) {
            long xp = (long) ((base + (e.level - 1) * lvlMult) * count);
            double mult = getMultiplier(e.holder);
            total += (long) (xp * mult);
        }
        return total * 20;
    }

    /** 获取指定附魔的代价倍率（enchantHighCostList 中可配置），未配置则用默认倍率 */
    public static double getMultiplier(Holder<Enchantment> holder) {
        var key = holder.getKey();
        if (key == null) return CommandConfig.SERVER.enchantDefaultMult.get();
        ResourceLocation id = key.location();
        for (String entry : CommandConfig.SERVER.enchantHighCostList.get()) {
            String[] p = entry.split(":");
            if (p.length >= 2) {
                String eid = p[0] + ":" + p[1];
                if (eid.equals(id.toString())) {
                    if (p.length >= 3) {
                        try { return Double.parseDouble(p[2]); } catch (Exception ignored) {}
                    }
                    return CommandConfig.SERVER.enchantDefaultMult.get();
                }
            }
        }
        return CommandConfig.SERVER.enchantDefaultMult.get();
    }

    /** 校验网络中经验液体与空白书数量是否足够 */
    public static boolean hasResources(DimensionsNet net, long xpCost, long booksNeeded) {
        if (xpCost > 0) {
            var xp = net.getUnifiedStorage().getStackByKey(xpFluidKey());
            if (xp.amount() < xpCost) return false;
        }
        if (booksNeeded > 0) {
            var bk = net.getUnifiedStorage().getStackByKey(new ItemStackKey(new ItemStack(Items.BOOK)));
            if (bk.amount() < booksNeeded) return false;
        }
        return true;
    }

    /** 校验每种单附魔书的现有存量与单槽容量上限是否足够 */
    public static boolean canStore(DimensionsNet net, List<Entry> ench, long count) {
        for (Entry e : ench) {
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            mutable.set(e.holder, e.level);
            book.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
            var cur = net.getUnifiedStorage().getStackByKey(new ItemStackKey(book));
            long cap = net.getUnifiedStorage().getSlotCapacity(0);
            if (cap <= 0) cap = Long.MAX_VALUE;
            if (cur.amount() + count > cap) return false;
        }
        return true;
    }

    /** 模拟插入全部单附魔书，确认输出空间足够 */
    public static boolean canOutput(DimensionsNet net, List<Entry> ench, long count) {
        for (Entry e : ench) {
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            mutable.set(e.holder, e.level);
            book.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
            KeyAmount remainder = net.getUnifiedStorage().insert(new ItemStackKey(book), count, true);
            if (remainder.amount() > 0) return false;
        }
        return true;
    }

    /** 从网络扣除指定量经验液体 */
    public static void consumeExperience(DimensionsNet net, long amount) {
        if (amount <= 0) return;
        net.getUnifiedStorage().extract(xpFluidKey(), amount, false, false);
    }

    /** 从网络扣除指定数量空白书 */
    public static void consumeBooks(DimensionsNet net, long count) {
        if (count <= 0) return;
        net.getUnifiedStorage().extract(new ItemStackKey(new ItemStack(Items.BOOK)), count, false, false);
    }

    /** 获取经验液体的存储键（BD 经验流体不可用时退回空流体键） */
    public static FluidStackKey xpFluidKey() {
        if (BDFluids.XP_FLUID != null && BDFluids.XP_FLUID.source() != null) {
            return new FluidStackKey(new FluidStack(BDFluids.XP_FLUID.source().get(), 1));
        }
        return new FluidStackKey(new FluidStack(Fluids.EMPTY, 1));
    }

    /** 构造放行结果：原样返回输入 */
    private static UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo pass(KeyAmount input) {
        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(input, false);
    }

    /** 构造"已处理完毕"结果：返回空键，阻止原始物品写入 */
    private static UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo acceptEmpty() {
        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(
                new KeyAmount(new ItemStackKey(new ItemStack(Items.AIR)), 0), false);
    }
}
