package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.handler.TaczAmmoExtractor;
import com.solr98.beyondintegration.handler.SentryNetIdAccessor;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 哨戒臂换弹 Mixin（Pseudo）：注入哨戒机械臂模组的 SentryArmBlockEntity.performInstantReload，
 * 哨戒臂瞬时换弹且弹药不从自身库存取用时，从绑定的维度网络补充弹药并处理枪机状态。
 */
@Pseudo
@Mixin(targets = "euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity", remap = false)
public class SentryArmReloadMixin {

    /**
     * 瞬时换弹回调：若哨戒武器不用背包弹药，则从绑定维度网络提取弹药填入弹匣，
     * 并按枪机类型（闭膛待击）处理膛内子弹状态。
     */
    @Inject(method = "performInstantReload", at = @At("HEAD"), cancellable = true)
    private void onReload(net.neoforged.neoforge.common.util.FakePlayer fakePlayer,
                           com.tacz.guns.api.item.IGun iGun,
                           net.minecraft.world.item.ItemStack gunStack,
                           CallbackInfoReturnable<Boolean> cir) {
        try {
            if (iGun.useInventoryAmmo(gunStack)) return;
            DimensionsNet net = getTerminalNetwork();
            if (net == null) return;

            var gunIndexOpt = TimelessAPI.getCommonGunIndex(iGun.getGunId(gunStack));
            if (gunIndexOpt.isEmpty()) return;

            int maxAmmo = gunIndexOpt.get().getGunData().getAmmoAmount();
            int currentAmmo = iGun.getCurrentAmmoCount(gunStack);
            int need = maxAmmo - currentAmmo;
            if (need <= 0) return;

            int networkCount = TaczAmmoExtractor.countAmmoInNetwork(gunStack, net);
            if (networkCount == Integer.MAX_VALUE) need = Math.min(need, 9000);
            else if (networkCount > 0) need = Math.min(need, Math.min(networkCount, 9000));
            else return;

            int fromNet = TaczAmmoExtractor.consumeAmmoDirectly(gunStack, need, net);
            if (fromNet <= 0) return;

            iGun.setCurrentAmmoCount(gunStack, currentAmmo + fromNet);
            Bolt bolt = gunIndexOpt.get().getGunData().getBolt();
            if (bolt != Bolt.OPEN_BOLT && !iGun.hasBulletInBarrel(gunStack) && iGun.getCurrentAmmoCount(gunStack) > 0) {
                iGun.reduceCurrentAmmoCount(gunStack);
                iGun.setBulletInBarrel(gunStack, true);
            }
            cir.setReturnValue(true);
        } catch (Exception ignored) {}
    }

    /** 读取哨戒臂上持久化的网络 ID，并解析为维度网络实例；未绑定或客户端返回 null */
    @Unique
    private DimensionsNet getTerminalNetwork() {
        BlockEntity be = (BlockEntity) (Object) this;
        if (be.getLevel() == null || be.getLevel().isClientSide) return null;
        if (!(be instanceof SentryNetIdAccessor accessor)) return null;
        int netId = accessor.getSentryNetId();
        if (netId < 0) return null;
        return DimensionsNet.getNetFromId(netId);
    }
}
