package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.handler.HoverInsertHandler;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 原版收纳袋式快速存入网络（服务端），双向支持。
 *
 * <p>注入原版 {@code Item#overrideStackedOnOther} 与 {@code Item#overrideOtherStackedOnMe}——
 * 与收纳袋（BundleItem）完全相同的槽位点击扩展点：左键点击时，无论"网络物品在槽位、物品在光标"
 * 还是"网络物品在光标、物品在槽位"，都将物品插入网络物品绑定的网络，返回 true 使原版交换跳过。</p>
 *
 * <p>安全性对齐原版：操作由原版 {@code ServerboundContainerClickPacket} 触发，
 * 服务端在 {@code AbstractContainerMenu.doClick} 内执行并做菜单一致性/stateId 校验；
 * 目标网络从服务端权威物品的 NetId 数据组件读取，客户端无法指定任意网络。</p>
 */
@Mixin(Item.class)
public class NetedItemClickMixin {

    /** 正向：左键点击槽位中的网络物品（光标持物）→ 光标物品存入该物品绑定的网络 */
    @Inject(method = "overrideOtherStackedOnMe", at = @At("HEAD"), cancellable = true)
    private void beyondintegration$quickStoreCarried(ItemStack slotStack, ItemStack carried, Slot slot,
                                                     ClickAction action, Player player,
                                                     SlotAccess carriedAccess, CallbackInfoReturnable<Boolean> cir) {
        if (action != ClickAction.PRIMARY) return;
        if (carried.isEmpty()) return;
        if (!((Object) this instanceof NetedItem)) return;
        if (player.level().isClientSide()) return;
        int netId = HoverInsertHandler.getNetId(slotStack);
        if (netId < 0) return;
        if (HoverInsertHandler.tryInsert(player, netId, carried, null)) {
            cir.setReturnValue(true);
        }
    }

    /** 反向：光标持网络物品左键点击槽位 → 槽位物品存入光标物品绑定的网络 */
    @Inject(method = "overrideStackedOnOther", at = @At("HEAD"), cancellable = true)
    private void beyondintegration$quickStoreSlot(ItemStack carriedStack, Slot slot, ClickAction action,
                                                  Player player, CallbackInfoReturnable<Boolean> cir) {
        if (action != ClickAction.PRIMARY) return;
        if (!((Object) this instanceof NetedItem)) return;
        if (player.level().isClientSide()) return;

        ItemStack slotStack = slot.getItem();
        if (slotStack.isEmpty()) return;
        // 槽位必须允许取放（mayPickup + mayPlace，如合成结果槽会被拒绝），并走原版取物语义
        if (!slot.allowModification(player)) return;

        int netId = HoverInsertHandler.getNetId(carriedStack);
        if (netId < 0) return;
        if (HoverInsertHandler.tryInsert(player, netId, slotStack, slot)) {
            cir.setReturnValue(true);
        }
    }
}
