package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.CommandConfig;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 经验棒目标等级上限调整（BD {@code XpExchangeSettings}）。
 * 将 {@code sanitizeTargetLevel} 的 clamp 上限由固定 21863 改为 BI 配置
 * （{@code xp_rod.max_target_level}，默认 21863），覆盖输入框/滚轮/数据读取等全部路径。
 */
@Mixin(value = com.wintercogs.beyonddimensions.common.item.XpExchangeSettings.class, remap = false)
public class XpExchangeSettingsMixin {

    @Inject(method = "sanitizeTargetLevel", at = @At("HEAD"), cancellable = true)
    private static void beyondintegration$customMaxTargetLevel(int targetLevel, CallbackInfoReturnable<Integer> cir) {
        if (!CommandConfig.xpRodTweaksEnabled()) return; // 总开关关闭：走 BD 原逻辑（固定 21863 上限）
        cir.setReturnValue(Mth.clamp(targetLevel, 0, CommandConfig.xpRodMaxTargetLevel()));
    }
}
