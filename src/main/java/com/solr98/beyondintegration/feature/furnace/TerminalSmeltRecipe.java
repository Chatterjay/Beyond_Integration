package com.solr98.beyondintegration.feature.furnace;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.init.ModRecipes;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import com.wintercogs.beyonddimensions.common.item.NetTerminalItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;

/**
 * 熔炉烧终端定制配方（BD 修改，配置 {@code bd_tweaks.furnace_terminal_smelt_all}）：
 * <ul>
 *   <li>{@code matches}：仅当配置开启、输入为已绑定网络的 BD 网络终端时匹配
 *       （配置关闭/未绑定终端不匹配 → 原版不可烧）；</li>
 *   <li>{@code assemble}：产物 = <b>输入终端副本（继承数据组件，保留网络绑定）</b>，
 *       因此烧炼后终端不消耗，只是从输入槽移动到输出槽，玩家可取回复用；
 *       并打一次性标记 {@code beyond_integration:furnace_terminal} 供 NetedItemMixin 保护绑定；</li>
 *   <li>批量烧炼由取出事件触发（{@code FurnaceTerminalEventHandler}）。</li>
 * </ul>
 *
 * <p>继承 {@link SmeltingRecipe}（而非直接 AbstractCookingRecipe）：
 * 部分模组会假设 {@code RecipeType.SMELTING} 下的配方均为 SmeltingRecipe 并直接强转。</p>
 */
public class TerminalSmeltRecipe extends SmeltingRecipe {

    /** 一次性标记：熔炉烧终端产物（NetedItemMixin 据此跳过 BD 自动绑定/解绑） */
    public static final String FURNACE_TERMINAL_TAG = "beyond_integration:furnace_terminal";

    public TerminalSmeltRecipe(String group, CookingBookCategory category, Ingredient ingredient,
                               ItemStack result, float experience, int cookingTime) {
        super(group, category, ingredient, result, experience, cookingTime);
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        if (!CommandConfig.furnaceTerminalSmeltAllEnabled()) return false;
        ItemStack stack = input.item();
        return !stack.isEmpty()
                && stack.getItem() instanceof NetTerminalItem
                && NetedItem.getNet(stack) != null;
    }

    /** 产物 = 输入终端副本：继承数据组件（NetId 网络绑定），实现终端"不消耗" */
    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        ItemStack out = input.item().copy();
        out.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY,
                data -> data.update(tag -> tag.putBoolean(FURNACE_TERMINAL_TAG, true)));
        return out;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.TERMINAL_SMELT.get();
    }
}
