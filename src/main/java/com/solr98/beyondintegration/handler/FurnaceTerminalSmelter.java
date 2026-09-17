package com.solr98.beyondintegration.handler;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import com.wintercogs.beyonddimensions.common.item.NetTerminalItem;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * 熔炉烧终端 → 批量烧炼（BD 修改，配置 {@code bd_tweaks.furnace_terminal_smelt_all}）。
 *
 * <p>把 BD 网络终端（{@link NetTerminalItem}，需已绑定网络）放入熔炉输入槽，
 * 熔炉正常消耗燃料并"烧炼"终端（沿用原版无配方默认时长 200 tick）；
 * 烧炼完成时由 {@code AbstractFurnaceBlockEntityMixin} 拦截：
 * 消耗 1 个终端，并把该终端绑定网络内的所有可烧炼物品按当前炉配方类型
 * （熔炉=Smelting / 高炉=Blasting / 烟熏=Smoking）一次性转换为产物；
 * 配方经验按 1 XP = 20 mB 存入网络经验流体。</p>
 *
 * <p>容量安全：先实际插入产物（模拟容量不足时跳过该条目），再按比例扣除输入，
 * 扣除不足时按比例取回产物；附魔分离/黑名单等 BD 插入拦截链照常生效。</p>
 */
public final class FurnaceTerminalSmelter {

    private FurnaceTerminalSmelter() {}

    /** 是否为 BD 网络终端物品（不要求已绑定网络） */
    public static boolean isNetTerminal(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof NetTerminalItem;
    }

    /** 可触发批量烧炼的网络终端（BD 终端物品 + 已绑定网络） */
    public static boolean isTerminal(ItemStack stack) {
        return isNetTerminal(stack) && NetedItem.getNet(stack) != null;
    }

    /**
     * 批量烧炼网络内全部可烧炼物品。
     *
     * @param net        终端绑定的网络
     * @param level      服务端世界（用于配方查询）
     * @param recipeType 当前炉的配方类型（SMELTING/BLASTING/SMOKING）
     * @return 实际转换的物品条目数
     */
    public static int smeltAll(DimensionsNet net, Level level,
                               RecipeType<? extends AbstractCookingRecipe> recipeType) {
        if (net == null || level == null || level.isClientSide()) return 0;

        var storage = net.getUnifiedStorage();
        var registryAccess = level.registryAccess();
        var recipeManager = level.getRecipeManager();

        // 快照：处理过程会修改存储，避免并发修改
        List<KeyAmount> snapshot = new ArrayList<>(storage.getStorage());
        double totalXp = 0;
        int converted = 0;

        for (KeyAmount ka : snapshot) {
            if (!(ka.key() instanceof ItemStackKey inputKey)) continue;
            ItemStack input = inputKey.getReadOnlyStack();
            if (input.isEmpty()) continue;
            long inputCount = ka.amount();
            if (inputCount <= 0) continue;

            AbstractCookingRecipe recipe =
                    recipeManager.getRecipeFor(recipeType, new SimpleContainer(input), level).orElse(null);
            if (recipe == null) continue;
            ItemStack result = recipe.getResultItem(registryAccess);
            if (result.isEmpty()) continue;

            ItemStackKey productKey = new ItemStackKey(result.copyWithCount(result.getCount()));
            long productTotal = inputCount * result.getCount();

            // 先插入产物（容量不足则本条目跳过），再按比例扣输入；扣除不足则回滚产物
            long left = storage.insert(productKey, productTotal, false).amount();
            long inserted = productTotal - left;
            if (inserted <= 0) continue;

            long inputsToUse = inserted / result.getCount();
            if (inputsToUse <= 0) {
                storage.extract(productKey, inserted, false, false);
                continue;
            }
            KeyAmount removed = storage.extract(inputKey, inputsToUse, false, false);
            long used = removed.amount();
            if (used < inputsToUse) {
                long rollback = (inputsToUse - used) * result.getCount();
                if (rollback > 0) storage.extract(productKey, rollback, false, false);
            }
            if (used <= 0) continue;

            totalXp += recipe.getExperience() * used;
            converted++;
        }

        // 配方经验入网络经验流体（1 XP = 20 mB）
        long mb = Math.round(totalXp * 20.0);
        if (mb > 0) {
            storage.insert(com.solr98.beyondintegration.feature.enchant.EnchantmentBookSeparatorHandler.xpFluidKey(),
                    mb, false);
        }
        return converted;
    }
}
