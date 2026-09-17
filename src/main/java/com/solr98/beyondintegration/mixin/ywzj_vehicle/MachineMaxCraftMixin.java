package com.solr98.beyondintegration.mixin.ywzj_vehicle;

import com.solr98.beyondintegration.handler.MenuNetIdHelper;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.ywzj.vehicle.network.message.ClientMachineMaxAction;
import org.ywzj.vehicle.recipe.VehiclePrintingIngredient;
import org.ywzj.vehicle.recipe.VehiclePrintingRecipe;

import java.util.List;

/**
 * 机器最大合成 Mixin：注入遥装甲载具的 ClientMachineMaxAction.hasIngredients，
 * 服务端判定最大合成时，若玩家背包材料不足则从维度网络补齐材料并拉入背包，
 * 实现"一键最大合成"也能使用网络中的材料。
 */
@Mixin(ClientMachineMaxAction.class)
public class MachineMaxCraftMixin {


    /** 判断配方材料是否充足：背包不足时检查网络并拉取材料入包，满足则允许最大合成 */
    @Inject(method = "hasIngredients", at = @At("HEAD"), cancellable = true)
    private static void beyond$checkNetworkForCraft(ServerPlayer player, VehiclePrintingRecipe recipe, CallbackInfoReturnable<Boolean> cir) {
        if (hasEnoughInInventory(player, recipe)) return;

        DimensionsNet net = findNetwork(player);
        if (net == null) return;

        for (VehiclePrintingIngredient input : recipe.getInputs()) {
            Ingredient ingredient = input.ingredient();
            int needed = input.count();
            for (ItemStack stack : ingredient.getItems()) {
                long available = net.getUnifiedStorage().getStackByKey(new ItemStackKey(stack)).amount();
                needed -= (int) Math.min(available, needed);
                if (needed <= 0) break;
            }
            if (needed > 0) return;
        }

        // Network has all materials, pull them into player inventory
        for (VehiclePrintingIngredient input : recipe.getInputs()) {
            Ingredient ingredient = input.ingredient();
            int needed = input.count();
            for (ItemStack stack : ingredient.getItems()) {
                if (needed <= 0) break;
                var key = new ItemStackKey(stack);
                long available = net.getUnifiedStorage().getStackByKey(key).amount();
                long extract = Math.min(available, needed);
                if (extract <= 0) continue;

                ItemStack pulled = stack.copyWithCount((int) extract);
                int countBefore = pulled.getCount();
                player.getInventory().add(pulled);
                int consumed = countBefore - pulled.getCount();
                if (consumed > 0) {
                    net.getUnifiedStorage().extract(key, consumed, false, false);
                    needed -= consumed;
                }
            }
        }

        net.setDirty();
        cir.setReturnValue(true);
    }

    /** 检查玩家背包（不含网络）是否已满足全部配方材料 */
    private static boolean hasEnoughInInventory(ServerPlayer player, VehiclePrintingRecipe recipe) {
        List<ItemStack> inventoryCopy = player.getInventory().items.stream()
                .filter(s -> !s.isEmpty()).map(ItemStack::copy).toList();

        for (VehiclePrintingIngredient input : recipe.getInputs()) {
            int needed = input.count();
            Ingredient ingredient = input.ingredient();
            for (ItemStack stack : inventoryCopy) {
                if (ingredient.test(stack)) {
                    int take = Math.min(stack.getCount(), needed);
                    stack.shrink(take);
                    needed -= take;
                }
                if (needed <= 0) break;
            }
            if (needed > 0) return false;
        }
        return true;
    }

    /** 解析玩家所在维度网络：优先主网络，否则回退到当前打开菜单对应的网络 */
    private static DimensionsNet findNetwork(ServerPlayer player) {
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net != null) return net;
        return MenuNetIdHelper.getNetFromMenu(player);
    }
}

