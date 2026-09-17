package com.solr98.beyondintegration.feature.furnace;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.init.ModRecipes;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import com.wintercogs.beyonddimensions.common.item.NetTerminalItem;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;

/**
 * 熔炉烧终端定制配方（BD 修改，配置 {@code bd_tweaks.furnace_terminal_smelt_all}）：
 * <ul>
 *   <li>{@code matches}：仅当配置开启、输入为已绑定网络的 BD 网络终端时匹配
 *       （配置关闭/未绑定终端不匹配 → 原版不可烧）；</li>
 *   <li>{@code assemble}：产物 = <b>输入终端副本（继承 NBT，保留网络绑定）</b>，
 *       因此烧炼后终端不消耗，只是从输入槽移动到输出槽，玩家可取回复用；</li>
 *   <li>批量烧炼由取出事件触发（{@code FurnaceTerminalEventHandler}，玩家从熔炉取出终端时）。</li>
 * </ul>
 *
 * <p>继承 {@link SmeltingRecipe} 而非直接继承 {@code AbstractCookingRecipe}：
 * 部分模组（如 Mekanism）会假设 {@code RecipeType.SMELTING} 下的配方均为 SmeltingRecipe
 * 并直接强转，避免 ClassCastException。</p>
 */
public class TerminalSmeltRecipe extends SmeltingRecipe {

    public TerminalSmeltRecipe(ResourceLocation id, String group, CookingBookCategory category,
                               Ingredient ingredient, ItemStack result, float experience, int cookingTime) {
        super(id, group, category, ingredient, result, experience, cookingTime);
    }

    @Override
    public boolean matches(Container container, Level level) {
        if (!CommandConfig.furnaceTerminalSmeltAllEnabled()) return false;
        ItemStack input = container.getItem(0);
        return !input.isEmpty()
                && input.getItem() instanceof NetTerminalItem
                && NetedItem.getNet(input) != null;
    }

    /**
     * 产物 = 输入终端副本：继承 NBT（NetId 网络绑定），实现终端"不消耗"。
     * 额外打一次性标记 {@code beyond_integration:furnace_terminal}：
     * 取出时由 {@code NetedItemMixin} 跳过 BD 的自动绑定/解绑（否则会清掉 NetId 并提示"终端解绑"）。
     */
    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        ItemStack out = container.getItem(0).copy();
        out.getOrCreateTag().putBoolean("beyond_integration:furnace_terminal", true);
        return out;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.TERMINAL_SMELT.get();
    }
}
