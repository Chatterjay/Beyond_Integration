package com.solr98.beyondintegration.feature.crafting;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.api.ICraftingIntegration;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.init.ModRecipe;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import org.slf4j.Logger;
import java.util.*;

/**
 * TACZ（枪械模组）合成集成实现：实现 ICraftingIntegration 接口，
 * 支持查询枪械工作台配方、校验配方可用性，并执行从玩家背包与维度网络
 * 收集原料的批量合成，产物可选择放入玩家背包或直接存入网络。
 */
public class TaczCraftManager implements ICraftingIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 返回本集成对应的模组 ID。 */
    @Override public String modId() { return "tacz"; }

    /** 获取所有枪械工作台合成配方 ID 列表。 */
    @Override
    public Collection<ResourceLocation> getRecipeIds(RecipeManager manager) {
        return manager.getAllRecipesFor(ModRecipe.GUN_SMITH_TABLE_CRAFTING.get()).stream().map(r -> r.id()).toList();
    }

    /** 校验指定配方 ID 是否对应有效的枪械工作台合成配方。 */
    @Override
    public boolean canCraft(ResourceLocation recipeId, RecipeManager manager) {
        return manager.byKey(recipeId).filter(r -> r.value() instanceof GunSmithTableRecipe).isPresent();
    }

    /**
     * 执行批量合成：优先消耗玩家背包原料，不足部分从维度网络统一存储中提取；
     * 任一原料不足则回滚本次合成的已提取物品，最后统计各原料剩余总数。
     */
    @Override
    public CraftResult executeCraft(ServerPlayer player, DimensionsNet net, ResourceLocation recipeId, int count, boolean toNetwork) {
        var storage = net.getUnifiedStorage();
        var optRecipe = player.level().getRecipeManager().byKey(recipeId);
        if (optRecipe.isEmpty() || !(optRecipe.get().value() instanceof GunSmithTableRecipe recipe)) {
            return new CraftResult(0, ItemStack.EMPTY, Collections.emptyMap());
        }

        var inputs = recipe.getInputs();
        if (inputs == null || inputs.isEmpty()) return new CraftResult(0, ItemStack.EMPTY, Collections.emptyMap());

        // 记录每种原料在网络中的总量，用于客户端显示
        Map<String, Long> networkCounts = new HashMap<>();
        // 单次合成数量上限为 64
        int maxCraft = Math.min(count, 64);
        int crafted = 0;

        // 逐次尝试合成：每次先取玩家背包，再补取网络存储
        craftLoop:
        for (int c = 0; c < maxCraft; c++) {
            List<ItemStack> extracted = new ArrayList<>();
            for (int i = 0; i < inputs.size(); i++) {
                var entry = inputs.get(i);
                if (entry == null) continue;
                Ingredient ingredient = entry.getIngredient();
                int needPerCraft = Math.max(1, entry.getCount());

                ItemStack found = null;
                int total = 0;

                for (int j = 0; j < 36 && total < needPerCraft; j++) {
                    ItemStack inv = player.getInventory().getItem(j);
                    if (inv.isEmpty() || !ingredient.test(inv)) continue;
                    int take = Math.min(needPerCraft - total, inv.getCount());
                    inv.shrink(take);
                    if (inv.isEmpty()) player.getInventory().setItem(j, ItemStack.EMPTY);
                    if (found == null) found = inv.copyWithCount(take);
                    else found.grow(take);
                    total += take;
                }

                if (total < needPerCraft && storage != null) {
                    // 背包不足时从网络统一存储中按原料匹配补充提取
                    int fromNet = needPerCraft - total;
                    try {
                        var bucket = storage.getBucket(ItemStackKey.ID);
                        if (bucket.isPresent()) {
                            var b = bucket.get();
                            for (int si = 0; si < b.size() && fromNet > 0; si++) {
                                var rawKey = b.get(si);
                                if (!(rawKey instanceof ItemStackKey isk) || !ingredient.test(isk.getReadOnlyStack())) continue;
                                long avail = storage.getStackByKey(isk).amount();
                                long take = Math.min(avail, fromNet);
                                if (take > 0) {
                                    var ext = storage.extract(isk, take, false, false);
                                    if (ext.amount() > 0) {
                                        if (found == null) found = isk.getReadOnlyStack().copyWithCount((int) ext.amount());
                                        else found.grow((int) ext.amount());
                                        total += (int) ext.amount();
                                        fromNet -= (int) ext.amount();
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.warn("TaczCraft: failed to extract from network", e);
                    }
                }

                if (total < needPerCraft) {
                    // 原料不足：将本次已提取物品返还背包/网络，并终止本次合成
                    for (ItemStack s : extracted) { player.getInventory().add(s); if (!s.isEmpty() && storage != null) { long left = storage.insert(new ItemStackKey(s), s.getCount(), false).amount(); s.setCount((int) left); } if (!s.isEmpty()) player.drop(s, false); }
                    break craftLoop;
                }

                if (found != null) extracted.add(found);
            }

            if (extracted.size() < inputs.size()) break;
            crafted++;
        }

        if (crafted <= 0) return new CraftResult(0, ItemStack.EMPTY, Collections.emptyMap());

        // 生成合成结果（数量为成功合成次数），按需放入背包或网络
        var result = recipe.getResultItem(player.level().registryAccess()).copy();
        result.setCount(crafted);

        if (toNetwork) {
            // 产物直接存入网络
            storage.insert(new ItemStackKey(result), crafted, false);
        } else {
            // 产物放入玩家背包，放不下则掉落
            player.getInventory().add(result);
            if (!result.isEmpty()) player.drop(result, false);
        }

        // 统计各原料当前总数（背包 + 网络），用于界面展示
        for (int i = 0; i < inputs.size(); i++) {
            var entry = inputs.get(i);
            if (entry == null) continue;
            Ingredient ing = entry.getIngredient();
            long total = 0;
            for (int j = 0; j < 36; j++) { ItemStack inv = player.getInventory().getItem(j); if (!inv.isEmpty() && ing.test(inv)) total += inv.getCount(); }
            if (storage != null) {
                try {
                    var bucket = storage.getBucket(ItemStackKey.ID);
                    if (bucket.isPresent()) {
                        var b = bucket.get();
                        for (int si = 0; si < b.size(); si++) {
                            var rawKey = b.get(si);
                            if (rawKey instanceof ItemStackKey isk && ing.test(isk.getReadOnlyStack()))
                                total += net.getUnifiedStorage().getStackByKey(isk).amount();
                        }
                    }
                } catch (Exception ignored) {}
            }
            networkCounts.put(recipeId + "|" + i, total);
        }

        return new CraftResult(crafted, result, networkCounts);
    }
}
