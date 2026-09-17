package com.solr98.beyondintegration.mixin;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.solr98.beyondintegration.handler.VehicleNetCache;
import com.solr98.beyondintegration.handler.INetCachedVehicle;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.init.BDDataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 载具网络绑定 Mixin：为 SuperbWarfare 的 VehicleEntity 混入维度网络缓存
 * （实现 INetCachedVehicle），支持 NBT 持久化绑定关系，并可手持维度网终端
 * 右键载具完成绑定。
 */
@Mixin(targets = "com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity", remap = false)
public class VehicleNetMixin implements INetCachedVehicle {
    /** NBT 中保存的绑定网络 ID 键名 */
    private static final String BCE_NET_ID_KEY = "Net_id";
    /** NBT 中保存的武器弹药类型列表键名 */
    private static final String BCE_AMMO_LIST_KEY = "BCE_AmmoList";

    /** 载具网络缓存（懒加载） */
    @Unique
    private VehicleNetCache beyond$netCache;

    @Override
    public VehicleNetCache getNetCache() {
        VehicleNetCache cache = beyond$netCache;
        if (cache == null) {
            cache = new VehicleNetCache((VehicleEntity) (Object) this);
            beyond$netCache = cache;
        }
        return cache;
    }

    /** 读取存档数据时恢复载具的网络绑定与弹药类型列表 */
    @Inject(method = "readAdditionalSaveData", at = @At("HEAD"))
    private void onReadNbt(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains(BCE_NET_ID_KEY)) {
            List<String> ammoList = new ArrayList<>();
            if (tag.contains(BCE_AMMO_LIST_KEY, net.minecraft.nbt.Tag.TAG_LIST)) {
                ListTag listTag = tag.getList(BCE_AMMO_LIST_KEY, net.minecraft.nbt.Tag.TAG_STRING);
                for (int i = 0; i < listTag.size(); i++) {
                    ammoList.add(listTag.getString(i));
                }
            }
            getNetCache().attach(tag.getInt(BCE_NET_ID_KEY), ammoList);
        }
    }

    /** 写入存档数据时持久化载具的网络绑定与弹药类型列表 */
    @Inject(method = "addAdditionalSaveData", at = @At("HEAD"))
    private void onWriteNbt(CompoundTag tag, CallbackInfo ci) {
        VehicleNetCache cache = getNetCache();
        int netId = cache.getNetId();
        if (netId >= 0) {
            tag.putInt(BCE_NET_ID_KEY, netId);
            ListTag listTag = new ListTag();
            for (String key : cache.getAmmoList()) {
                listTag.add(StringTag.valueOf(key));
            }
            tag.put(BCE_AMMO_LIST_KEY, listTag);
        }
    }

    /** 交互回调：手持维度网终端右键载具时完成网络绑定 */
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true, remap = false)
    private void onInteract(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = player.getItemInHand(hand);
        int netId = stack.getOrDefault(BDDataComponents.NET_ID_DATA, -1);
        if (netId < 0) return;
        cir.setReturnValue(InteractionResult.SUCCESS);
        if (!player.level().isClientSide) {
            DimensionsNet net = DimensionsNet.getNetFromId(netId);
            if (net != null) getNetCache().attach(netId, null);
        }
    }
}
