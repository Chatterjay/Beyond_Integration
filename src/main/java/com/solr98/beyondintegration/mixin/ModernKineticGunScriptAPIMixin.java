package com.solr98.beyondintegration.mixin;
import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.client.TaczAmmoCache;
import com.solr98.beyondintegration.handler.FakePlayerNetMarker;
import com.solr98.beyondintegration.handler.TaczAmmoExtractor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.init.BDDataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 注入目标：TacZ 的 {@code ModernKineticGunScriptAPI}（现代动能枪脚本 API，即 Kriss/矢量枪的弹药接口）。
 * 目的：让脚本枪械的弹药消耗与判定同样支持维度网络，覆盖 FakePlayer、服务端玩家
 * （含物品栏终端网络）与女仆三种场景，并支持客户端本地缓存兜底。
 */
@Mixin(targets = "com.tacz.guns.item.ModernKineticGunScriptAPI", remap = false)
public class ModernKineticGunScriptAPIMixin {
    // Shadow：脚本枪当前射击者与枪械物品
    @Shadow(remap = false) private LivingEntity shooter;
    @Shadow(remap = false) private ItemStack itemStack;

    private static final Logger LOGGER = LogUtils.getLogger();

    // 拦截 consumeAmmoFromPlayer：原消耗不足时按实体类型从网络补充消耗
    @Inject(method = "consumeAmmoFromPlayer", at = @At("RETURN"), cancellable = true)
    private void beyond$onConsumeAmmoFromPlayer(int neededAmount, CallbackInfoReturnable<Integer> cir) {
        int found = cir.getReturnValue();
        if (found >= neededAmount) return;
        int stillNeed = neededAmount - found;

        if (shooter instanceof FakePlayer fp) {
            DimensionsNet net = FakePlayerNetMarker.getNet(fp);
            if (net != null) {
                int fromNet = TaczAmmoExtractor.consumeAmmoDirectly(itemStack, stillNeed, net);
                if (fromNet > 0) {
                    LOGGER.debug("consumeAmmoFromPlayer: FakePlayer consumed {} from network (need={})", fromNet, stillNeed);
                    cir.setReturnValue(found + fromNet);
                }
            }
        } else if (shooter instanceof ServerPlayer sp) {
            int fromNet = TaczAmmoExtractor.tryConsumeFromAll(sp, itemStack, stillNeed);
            if (fromNet <= 0) {
                fromNet = consumeFromInventoryTerminal(sp, itemStack, stillNeed);
            }
            if (fromNet > 0) {
                LOGGER.debug("consumeAmmoFromPlayer: consumed {} from network (need={})", fromNet, stillNeed);
                cir.setReturnValue(found + fromNet);
            }
        } else {
            int fromNet = TaczAmmoExtractor.tryConsumeFromMaid(shooter, itemStack, stillNeed);
            if (fromNet > 0) {
                cir.setReturnValue(found + fromNet);
            }
        }
    }

    // 拦截 hasAmmoToConsume：原判定无弹药时，检查网络（FakePlayer 标记/玩家网络/女仆/客户端缓存）中是否有弹药
    @Inject(method = "hasAmmoToConsume", at = @At("RETURN"), cancellable = true)
    private void beyond$onHasAmmoToConsume(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;

        if (shooter instanceof FakePlayer fp) {
            if (FakePlayerNetMarker.isMarked(fp)) {
                LOGGER.debug("hasAmmoToConsume: FakePlayer has network marker");
                cir.setReturnValue(true);
                return;
            }
        }

        if (shooter instanceof ServerPlayer sp) {
            if (TaczAmmoExtractor.countAmmoFromAll(sp, itemStack) > 0) {
                cir.setReturnValue(true);
            }
        } else if (shooter.level().isClientSide()) {
            clientAmmoCheck(itemStack, cir);
        } else if (TaczAmmoExtractor.countAmmoFromMaid(shooter, itemStack) > 0) {
            cir.setReturnValue(true);
        }
    }

    // 在服务端玩家物品栏中查找携带网络 ID 的终端物品，并从中直接消耗弹药
    @Unique
    private static int consumeFromInventoryTerminal(ServerPlayer player, ItemStack gunStack, int need) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            int netId = stack.getOrDefault(BDDataComponents.NET_ID_DATA, -1);
            if (netId >= 0) {
                DimensionsNet net = DimensionsNet.getNetFromId(netId);
                if (net != null) {
                    int taken = TaczAmmoExtractor.consumeAmmoDirectly(gunStack, need, net);
                    if (taken > 0) {
                        LOGGER.debug("consumeFromInventoryTerminal: took {} from net#{} via slot {}", taken, netId, i);
                        return taken;
                    }
                }
            }
        }
        return 0;
    }

    // 客户端兜底检查：无本地缓存时请求快照，缓存显示有弹药即判定可消耗
    @Unique
    private static void clientAmmoCheck(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        ResourceLocation ammoId = TaczAmmoExtractor.getAmmoIdClient(stack);
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

