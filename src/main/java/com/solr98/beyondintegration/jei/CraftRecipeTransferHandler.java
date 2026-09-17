package com.solr98.beyondintegration.jei;

import com.solr98.beyondintegration.init.DimensionsCraftMenu;
import com.solr98.beyondintegration.init.ModMenus;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.payload.RecipeFillPayload;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 合成配方转移处理器（JEI）：解析配方输入候选，优先选择网络或背包中存在的物品，
 * 生成 IStackKey 列表并通过 RecipeFillPayload 发送给服务端填充合成格。
 */
public class CraftRecipeTransferHandler implements IRecipeTransferHandler<DimensionsCraftMenu, RecipeHolder<CraftingRecipe>> {
    // 目标容器：合成菜单
    @Override public Class<? extends DimensionsCraftMenu> getContainerClass() { return DimensionsCraftMenu.class; }
    // 目标菜单类型
    @Override public Optional<MenuType<DimensionsCraftMenu>> getMenuType() { return Optional.of((MenuType) ModMenus.CRAFT.get()); }
    // 处理的配方类型：合成
    @Override public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() { return RecipeTypes.CRAFTING; }

    // 转移主逻辑：收集输入候选 → 选可用物品 → 发送填充包；缺失物品时返回 COSMETIC 错误提示
    @Override
    public @Nullable IRecipeTransferError transferRecipe(DimensionsCraftMenu menu, RecipeHolder<CraftingRecipe> recipe, IRecipeSlotsView slotsView, Player player, boolean maxTransfer, boolean doTransfer) {
        List<IStackKey<?>> keys = new ArrayList<>();
        List<Long> amounts = new ArrayList<>();
        boolean missing = false;

        for (IRecipeSlotView sv : slotsView.getSlotViews(RecipeIngredientRole.INPUT)) {
            List<ItemStack> candidates = sv.getIngredients(VanillaTypes.ITEM_STACK)
                    .filter(Objects::nonNull).filter(s -> !s.isEmpty()).collect(Collectors.toList());
            if (candidates.isEmpty()) { keys.add(new ItemStackKey(ItemStack.EMPTY)); amounts.add(0L); continue; }
            // 多候选（如石头/圆石）优先选网络或背包里存在的
            ItemStack chosen = candidates.get(0);
            for (ItemStack c : candidates) {
                if (isAvailable(menu, player, c)) { chosen = c; break; }
            }
            keys.add(new ItemStackKey(chosen));
            amounts.add(1L);
            if (!isAvailable(menu, player, chosen)) missing = true;
        }

        if (doTransfer) PacketHandler.sendToServer(new RecipeFillPayload(keys, amounts));
        return missing ? new IRecipeTransferError() { @Override public Type getType() { return Type.COSMETIC; } } : null;
    }

    // 物品是否可用：客户端网络存储有存量或背包中存在相同物品
    private boolean isAvailable(DimensionsCraftMenu menu, Player player, ItemStack stack) {
        if (menu.clientNetStorage != null) {
            long amt = menu.clientNetStorage.getStackByKey(new ItemStackKey(stack)).amount();
            if (amt > 0) return true;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (ItemStack.isSameItemSameComponents(player.getInventory().getItem(i), stack)) return true;
        }
        return false;
    }
}
