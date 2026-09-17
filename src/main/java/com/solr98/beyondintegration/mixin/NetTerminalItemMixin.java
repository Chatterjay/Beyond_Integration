package com.solr98.beyondintegration.mixin;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.init.BDDataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 维度网终端 Mixin：注入 BD 的 NetTerminalItem.use，
 * 手持终端对准 SuperbWarfare 载具使用时，将载具绑定到终端指向的维度网络。
 */
@Mixin(targets = "com.wintercogs.beyonddimensions.common.item.NetTerminalItem", remap = false)
public class NetTerminalItemMixin {

    /**
     * 终端使用回调：若玩家视线 8 格内瞄准 SuperbWarfare 载具，
     * 则消耗本次使用并把该网络绑定到载具缓存。
     */
    @Inject(method = "use", at = @At("HEAD"), cancellable = true, remap = true)
    private void onUse(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack stack = player.getItemInHand(hand);
        int netId = stack.getOrDefault(BDDataComponents.NET_ID_DATA, -1);
        if (netId < 0) return;

        Entity vehicle = findVehicle(player);
        if (vehicle == null) return;

        cir.setReturnValue(InteractionResultHolder.sidedSuccess(stack, level.isClientSide()));
        if (!level.isClientSide()) {
            DimensionsNet net = DimensionsNet.getNetFromId(netId);
            if (net != null && vehicle instanceof com.solr98.beyondintegration.handler.INetCachedVehicle icv) {
                icv.getNetCache().attach(netId, null);
            }
        }
    }

    /** 沿玩家视线方向（8 格内）查找被准星命中的 SuperbWarfare 载具实体 */
    private static Entity findVehicle(Player player) {
        Vec3 from = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 to = from.add(look.scale(8));
        AABB box = new AABB(from, to).inflate(2);
        for (Entity e : player.level().getEntities(player, box, e -> {
            if (e == player) return false;
            try {
                Class<?> vc = Class.forName("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity");
                return vc.isInstance(e);
            } catch (Exception ex) {
                return false;
            }
        })) {
            var hit = e.getBoundingBox().clip(from, to).orElse(null);
            if (hit != null) return e;
        }
        return null;
    }
}

