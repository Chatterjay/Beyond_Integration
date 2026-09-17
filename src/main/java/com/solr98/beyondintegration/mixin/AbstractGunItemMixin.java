package com.solr98.beyondintegration.mixin;
import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.client.TaczAmmoCache;
import com.solr98.beyondintegration.handler.FakePlayerNetMarker;
import com.solr98.beyondintegration.handler.TaczAmmoExtractor;
import com.tacz.guns.api.item.IGun;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.init.BDDataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 注入目标：TacZ 的 {@code AbstractGunItem}（枪械物品基类）。
 * 目的：扩展枪械"能否装填/是否有库存弹药/弹药提取"的判断逻辑，使 FakePlayer、
 * 网络终端（Beyond Dimensions）以及东方女仆也能使用网络中的弹药。
 */
@Mixin(targets = "com.tacz.guns.api.item.gun.AbstractGunItem", remap = false)
public class AbstractGunItemMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    // 拦截 canReload：原逻辑判定不可装填时，再根据射击者类型（FakePlayer/服务端玩家/客户端/女仆）检查网络弹药
    @Inject(method = "canReload", at = @At("RETURN"), cancellable = true)
    private void beyond$onCanReload(LivingEntity shooter, ItemStack gunItem, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null || iGun.useInventoryAmmo(gunItem)) return;

        if (shooter instanceof FakePlayer fp) {
            if (FakePlayerNetMarker.isMarked(fp)) {
                LOGGER.debug("canReload: FakePlayer has network marker");
                cir.setReturnValue(true);
            }
        } else if (shooter instanceof ServerPlayer sp) {
            if (TaczAmmoExtractor.countAmmoFromAll(sp, gunItem) > 0) {
                LOGGER.debug("canReload: player {} has network ammo", sp.getName().getString());
                cir.setReturnValue(true);
            }
        } else if (shooter.level().isClientSide()) {
            clientCheck(gunItem, cir);
        } else {
            if (TaczAmmoExtractor.countAmmoFromMaid(shooter, gunItem) > 0) {
                LOGGER.debug("canReload: maid has network ammo");
                cir.setReturnValue(true);
            }
        }
    }

    // 拦截 hasInventoryAmmo：与 canReload 相同思路，判定库存弹药时把网络弹药计入
    @Inject(method = "hasInventoryAmmo", at = @At("RETURN"), cancellable = true)
    private void beyond$onHasInventoryAmmo(LivingEntity shooter, ItemStack gun, boolean needCheckAmmo, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null || !iGun.useInventoryAmmo(gun)) return;

        if (shooter instanceof FakePlayer fp) {
            if (FakePlayerNetMarker.isMarked(fp)) {
                LOGGER.debug("hasInventoryAmmo: FakePlayer has network marker");
                cir.setReturnValue(true);
            }
        } else if (shooter instanceof ServerPlayer sp) {
            if (TaczAmmoExtractor.countAmmoFromAll(sp, gun) > 0) {
                LOGGER.debug("hasInventoryAmmo: player {} has network ammo", sp.getName().getString());
                cir.setReturnValue(true);
            }
        } else if (shooter.level().isClientSide()) {
            clientCheck(gun, cir);
        } else {
            if (TaczAmmoExtractor.countAmmoFromMaid(shooter, gun) > 0) {
                LOGGER.debug("hasInventoryAmmo: maid has network ammo");
                cir.setReturnValue(true);
            }
        }
    }

    // 拦截 findAndExtractInventoryAmmo：原逻辑从物品栏提取弹药不足时，补充从网络（FakePlayer 绑定网或物品栏内终端网）直接消耗
    @Inject(method = "findAndExtractInventoryAmmo", at = @At("RETURN"), cancellable = true)
    private void beyond$onFindAndExtract(IItemHandler itemHandler, ItemStack gunItem, int needAmmoCount, CallbackInfoReturnable<Integer> cir) {
        int found = cir.getReturnValue();
        if (found >= needAmmoCount) return;
        int stillNeed = needAmmoCount - found;
        if (stillNeed <= 0) return;

        FakePlayer fp = FakePlayerNetMarker.getFakePlayerFromHandler(itemHandler);
        if (fp != null && FakePlayerNetMarker.isMarked(fp)) {
            DimensionsNet net = FakePlayerNetMarker.getNet(fp);
            if (net != null) {
                int fromNet = TaczAmmoExtractor.consumeAmmoDirectly(gunItem, stillNeed, net);
                if (fromNet > 0) {
                    LOGGER.debug("findAndExtract: FakePlayer consumed {} from net#{} (need={})", fromNet, net.getId(), stillNeed);
                    cir.setReturnValue(found + fromNet);
                    return;
                }
            }
        }

        DimensionsNet net = findTerminalInHandler(itemHandler);
        if (net != null) {
            int fromNet = TaczAmmoExtractor.consumeAmmoDirectly(gunItem, stillNeed, net);
            if (fromNet > 0) {
                LOGGER.debug("findAndExtract: consumed {} from net#{} (need={}, found={})",
                        fromNet, net.getId(), stillNeed, found);
                cir.setReturnValue(found + fromNet);
            } else {
                LOGGER.debug("findAndExtract: net#{} has ammo but consume returned 0", net.getId());
            }
        }
    }

    // 在物品栏容器中查找携带网络 ID 数据组件的终端物品（如维度网络终端），返回其对应的网络
    private static DimensionsNet findTerminalInHandler(IItemHandler inv) {
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            int netId = stack.getOrDefault(BDDataComponents.NET_ID_DATA, -1);
            if (netId >= 0) {
                DimensionsNet net = DimensionsNet.getNetFromId(netId);
                if (net != null) {
                    LOGGER.debug("findTerminalInHandler: found net#{} terminal in slot {}", netId, i);
                    return net;
                }
            }
        }
        return null;
    }

    // 客户端兜底检查：从本地弹药缓存获取弹药 ID，无缓存时先请求服务端快照，再决定是否允许装填
    private static void clientCheck(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
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

