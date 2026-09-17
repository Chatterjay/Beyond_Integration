package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.handler.XpDirectGranter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 经验棒 keep 模式：配置 {@code xp_rod.grant_mode=DIRECT} 时，
 * 由 BI 直设式给予（等级计算 + 设定 + 经验补差）接管本 tick；
 * BATCH 模式（默认）仍走 BD 原逻辑（BI 已将其改为分批 giveExperiencePoints，上限 238609312）。
 */
@Mixin(value = com.wintercogs.beyonddimensions.common.item.XpExchangeItem.class, remap = false)
public class XpExchangeItemMixin {

    @Inject(method = "keepXpLevel", at = @At("HEAD"), cancellable = true)
    private void beyondintegration$directLevelGrant(ItemStack stack, Player player, Level level, CallbackInfo ci) {
        if (level.isClientSide()) return;
        if (XpDirectGranter.tryGrant(stack, player)) ci.cancel();
    }
}
