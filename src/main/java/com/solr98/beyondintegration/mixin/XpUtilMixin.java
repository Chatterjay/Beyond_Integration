package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.handler.XpGrantHelper;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 经验棒经验发放分批化（BD {@code XpUtil.clampXpToGive}）：
 * 单次可发放量改为"每 tick 一批"（BI 配置 {@code xp_rod.grant_batch_size}），
 * 由经验棒 keep 模式的 tick 驱动自然分批，避免单次逼近 int 边界时的溢出与 float 精度问题。
 * 保留玩家状态安全校验与总量/等级余量限制（逻辑见 {@link XpGrantHelper}）。
 */
@Mixin(value = com.wintercogs.beyonddimensions.util.XpUtil.class, remap = false)
public class XpUtilMixin {

    @Inject(method = "clampXpToGive", at = @At("HEAD"), cancellable = true)
    private static void beyondintegration$batchedGrant(Player player, long requestedXp, CallbackInfoReturnable<Integer> cir) {
        if (!CommandConfig.xpRodTweaksEnabled()) return; // 总开关关闭：走 BD 原逻辑
        cir.setReturnValue(XpGrantHelper.pendingPerTick(player, requestedXp));
    }
}
