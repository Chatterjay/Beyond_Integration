package com.solr98.beyondintegration.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 熔炉烧终端产物保护（BD 修改，配合 {@code terminal_smelt} 配方）：
 * BD {@code NetedItem.onCraftedBy} 会在物品"取出/合成"时自动调用 setNet——
 * 已绑定物品会走解绑分支（清 NetId 并提示"终端解绑"），导致熔炉取出终端产物时绑定丢失、
 * 批量烧炼无法触发。这里对带一次性标记 {@code beyond_integration:furnace_terminal}
 * 的熔炉产物跳过该逻辑（移除标记，保留原 NetId）。
 */
@Mixin(value = com.wintercogs.beyonddimensions.common.item.NetedItem.class, remap = false)
public class NetedItemMixin {

    @Inject(method = "onCraftedBy", at = @At("HEAD"), cancellable = true)
    private void beyondintegration$keepNetIdForFurnaceTerminal(ItemStack stack, Level level, Player player,
                                                               CallbackInfo ci) {
        if (level.isClientSide()) return; // 与原逻辑一致：仅服务端处理
        if (stack.hasTag() && stack.getTag().getBoolean("beyond_integration:furnace_terminal")) {
            stack.getTag().remove("beyond_integration:furnace_terminal");
            ci.cancel(); // 跳过 BD 的自动绑定/解绑，保留终端网络绑定
        }
    }
}
