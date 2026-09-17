package com.solr98.beyondintegration.mixin.ywzj_vehicle;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.handler.VehicleNetStorage;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.init.BDDataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;

/**
 * 载具网络绑定 Mixin：为遥装甲载具的 AbstractVehicle 混入维度网络绑定支持，
 * 绑定关系经 VehicleNetStorage 按载具 UUID 静态管理并随 NBT 持久化，
 * 支持手持维度网终端右键载具完成绑定。
 */
@Mixin(AbstractVehicle.class)
public class VehicleNetMixin {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** NBT 中保存的绑定网络 ID 键名 */
    private static final String BCE_NET_ID_KEY = "bce_net_id";

    /** 读取存档数据时恢复载具的网络绑定 */
    @Inject(method = "readAdditionalSaveData", at = @At("HEAD"))
    private void beyond$onReadNbt(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains(BCE_NET_ID_KEY)) {
            AbstractVehicle self = (AbstractVehicle) (Object) this;
            int netId = tag.getInt(BCE_NET_ID_KEY);
            VehicleNetStorage.bindVehicle(self.getUUID(), netId);
        }
    }

    /** 写入存档数据时持久化载具的网络绑定 */
    @Inject(method = "addAdditionalSaveData", at = @At("HEAD"))
    private void beyond$onWriteNbt(CompoundTag tag, CallbackInfo ci) {
        AbstractVehicle self = (AbstractVehicle) (Object) this;
        int netId = VehicleNetStorage.getBoundNetId(self.getUUID());
        if (netId >= 0) {
            tag.putInt(BCE_NET_ID_KEY, netId);
        }
    }

    /** 交互回调：手持维度网终端右键载具时，按载具 UUID 完成网络绑定 */
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void beyond$onInteract(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = player.getItemInHand(hand);
        int netId = stack.getOrDefault(BDDataComponents.NET_ID_DATA, -1);
        if (netId < 0) return;
        cir.setReturnValue(InteractionResult.SUCCESS);
        if (!player.level().isClientSide) {
            DimensionsNet net = DimensionsNet.getNetFromId(netId);
            if (net != null) {
                VehicleNetStorage.bindVehicle(((AbstractVehicle) (Object) this).getUUID(), netId);
            }
        }
    }
}

