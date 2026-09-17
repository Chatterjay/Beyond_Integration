package com.solr98.beyondintegration.mixin;
import com.solr98.beyondintegration.client.TaczAmmoCache;
import com.solr98.beyondintegration.handler.TaczAmmoExtractor;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 注入目标：TacZ 的 {@code GunAnimationStateContext}（枪械动画状态机上下文，客户端）。
 * 目的：修正动画状态机对"是否有弹药可消耗"的判断，使使用网络弹药的枪械
 * 在客户端本地缓存显示有弹药时也能正常触发换弹等动画。
 */
@Mixin(targets = "com.tacz.guns.client.animation.statemachine.GunAnimationStateContext", remap = false)
public class GunAnimationStateContextMixin {
    // Shadow：当前手持的枪械物品
    @Shadow(remap = false) private net.minecraft.world.item.ItemStack currentGunItem;

    // 拦截 hasAmmoToConsume：原判断为无弹药时，兜底检查客户端网络弹药缓存
    @Inject(method = "hasAmmoToConsume", at = @At("RETURN"), cancellable = true)
    private void beyond$onHasAmmoToConsume(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;

        ResourceLocation ammoId = TaczAmmoExtractor.getAmmoIdClient(currentGunItem);
        if (ammoId != null) {
            if (!TaczAmmoCache.hasData(ammoId)) {
                TaczAmmoCache.requestQuick(ammoId);
                return;
            }
            if (TaczAmmoCache.getCount(ammoId) > 0) {
                cir.setReturnValue(true);
            }
        } else {
            cir.setReturnValue(true);
        }
    }
}

