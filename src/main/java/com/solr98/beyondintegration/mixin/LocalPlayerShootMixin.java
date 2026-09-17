package com.solr98.beyondintegration.mixin;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 注入目标：TacZ 的 {@code LocalPlayerShoot}（客户端玩家射击逻辑）。
 * 目的：使用背包/网络弹药模式的枪械在客户端弹药计数为 0 时，
 * 先把计数置为 1 再放行，避免动画/射击判定因本地无弹药而中断。
 */
@Mixin(targets = "com.tacz.guns.client.gameplay.LocalPlayerShoot", remap = false)
public class LocalPlayerShootMixin {

    // 拦截 doShoot：开火前确保库存弹药模式枪械的客户端计数至少为 1
    @Inject(method = "doShoot", at = @At("HEAD"))
    private void beyond$ensureMaxCount(GunDisplayInstance display, IGun iGun,
            ItemStack mainHandItem, GunData gunData, long delay, float chargeProgress, CallbackInfo ci) {
        if (iGun.useInventoryAmmo(mainHandItem) && iGun.getCurrentAmmoCount(mainHandItem) <= 0) {
            iGun.setCurrentAmmoCount(mainHandItem, 1);
        }
    }
}

