package com.solr98.beyondintegration.jei;

import com.solr98.beyondintegration.init.DimensionsCutMenu;
import com.solr98.beyondintegration.init.ModMenus;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * 切石配方转移处理器（JEI）：点击配方时在切石菜单配方列表中查找对应配方并选中
 * （StonecutterMenu 风格，无需发送物品）。
 */
public class CutRecipeTransferHandler implements IRecipeTransferHandler<DimensionsCutMenu, RecipeHolder<StonecutterRecipe>> {
    // 目标容器：切石菜单
    @Override public Class<? extends DimensionsCutMenu> getContainerClass() { return DimensionsCutMenu.class; }
    // 目标菜单类型
    @Override public Optional<MenuType<DimensionsCutMenu>> getMenuType() { return Optional.of((MenuType) ModMenus.CUT.get()); }
    // 处理的配方类型：切石
    @Override public RecipeType<RecipeHolder<StonecutterRecipe>> getRecipeType() { return RecipeTypes.STONECUTTING; }

    // 转移主逻辑：按配方实例匹配菜单内配方列表索引并点击选中
    @Override
    public @Nullable IRecipeTransferError transferRecipe(DimensionsCutMenu menu, RecipeHolder<StonecutterRecipe> recipe, IRecipeSlotsView slotsView, Player player, boolean maxTransfer, boolean doTransfer) {
        if (doTransfer) {
            List<StonecutterRecipe> recipes = menu.getRecipes();
            StonecutterRecipe value = recipe.value();
            int idx = -1;
            for (int i = 0; i < recipes.size(); i++) {
                if (recipes.get(i) == value) { idx = i; break; }
            }
            if (idx >= 0) menu.clickMenuButton(player, idx);
        }
        return null;
    }
}
