package com.solr98.beyondintegration.mixin;

import com.mojang.logging.LogUtils;
import com.wintercogs.beyonddimensions.common.init.BDDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 哨戒臂方块 Mixin（Pseudo）：注入哨戒机械臂模组的 SentryArmBlock.useItemOn，
 * 手持维度网终端右键哨戒臂时将其绑定的维度网络写入弹药箱，使哨戒可从网络中补充弹药。
 */
@Pseudo
@Mixin(targets = "euphy.upo.sentrymechanicalarm.content.SentryArmBlock", remap = false)
public class SentryArmBlockMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 反射缓存的哨戒臂方块实体类；加载失败时为 null（哨戒模组未安装） */
    @Unique
    private static Class<?> beyond$sentryClass;
    /** 反射缓存的 getHeldItem 方法 */
    @Unique
    private static java.lang.reflect.Method beyond$getHeldItem;
    /** 反射缓存的 addAmmoBox 方法 */
    @Unique
    private static java.lang.reflect.Method beyond$addAmmoBox;
    /** 静态初始化：用反射缓存哨戒臂方块实体类及其 getHeldItem/addAmmoBox 方法 */
    static {
        try {
            beyond$sentryClass = Class.forName("euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity");
            beyond$getHeldItem = beyond$sentryClass.getMethod("getHeldItem");
            beyond$addAmmoBox = beyond$sentryClass.getMethod("addAmmoBox", ItemStack.class);
        } catch (Exception e) {
            beyond$sentryClass = null;
        }
    }

    /**
     * 方块交互回调：手持维度网终端右键哨戒臂时，检查其持枪状态后
     * 将终端（含网络 ID）作为弹药箱附加到哨戒臂。
     */
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void beyond$onUseItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                      Player player, InteractionHand hand, BlockHitResult hit,
                                      CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (beyond$sentryClass == null) return;
        int netId = stack.getOrDefault(BDDataComponents.NET_ID_DATA, -1);
        if (netId < 0) return;

        try {
            BlockEntity be = level.getBlockEntity(pos);
            if (!beyond$sentryClass.isInstance(be)) return;

            ItemStack held = (ItemStack) beyond$getHeldItem.invoke(be);
            if (held.isEmpty()) {
                if (!level.isClientSide) {
                    LOGGER.debug("Sentry at {} has no gun, cannot bind net#{}", pos, netId);
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("message.beyond_integration.sentry_no_gun"), true);
                }
                cir.setReturnValue(ItemInteractionResult.SUCCESS);
                return;
            }

            boolean ok = (boolean) beyond$addAmmoBox.invoke(be, stack);
            if (ok) {
                LOGGER.debug("Bound net#{} to sentry at {}", netId, pos);
                if (!level.isClientSide && !player.isCreative()) stack.shrink(1);
            } else {
                LOGGER.debug("Sentry at {} ammo box slots full, cannot bind net#{}", pos, netId);
                if (!level.isClientSide)
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("sentry.tooltip.ammobox_1"), true);
            }
            cir.setReturnValue(ItemInteractionResult.SUCCESS);
        } catch (Exception e) {
            LOGGER.error("Error in sentry binding", e);
        }
    }
}
