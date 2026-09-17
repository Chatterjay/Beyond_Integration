package com.solr98.beyondintegration.mixin;
import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.solr98.beyondintegration.handler.EnergyChargeAccessor;
import com.solr98.beyondintegration.handler.NetworkAmmoData;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.solr98.beyondintegration.handler.TaczCreativeAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.world.level.saveddata.SavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 存档数据弹药同步 Mixin：注入通用 SavedData.setDirty。
 * 当对象是维度网络时，把 SW 虚拟弹药、附魔分离开关、TaCZ 创造模式弹药类型
 * 写入客户端同步缓存 NetworkAmmoData，供 HUD 等客户端逻辑读取。
 */
@Mixin(SavedData.class)
public class SavedDataAmmoSyncMixin {
    /** 维度网络数据被标记脏时，将其 SW 弹药/附魔分离/TaCZ 创造类型快照写入客户端同步缓存 */
    @Inject(method = "setDirty", at = @At("HEAD"))
    private void onSetDirty(CallbackInfo ci) {
        if (!((Object) this instanceof DimensionsNet net)) return;
        if (net.getId() < 0) return;
        int netId = net.getId();
        NetworkAmmoData data = NetworkAmmoData.get();
        if (net instanceof SuperbAmmoAccessor acc) data.setAmmoForNet(netId, acc.getSuperbAmmo());
        if (net instanceof EnchantSeparationAccessor ea) data.setEnchantSeparation(netId, ea.beyond$isEnchantSeparationEnabled());
        if (net instanceof EnergyChargeAccessor eca) data.setEnergyCharge(netId, eca.beyond$isEnergyChargeEnabled());
        if (net instanceof TaczCreativeAccessor tca) {
            data.setCreativeTypesForNet(netId, tca.getTaczCreativeTypes());
            data.setCreativeAllTypeForNet(netId, tca.getCreativeAllType());
        }
    }
}

