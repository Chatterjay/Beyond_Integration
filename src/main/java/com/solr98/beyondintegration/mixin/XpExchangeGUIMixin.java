package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.CommandConfig;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 经验棒界面（BD {@code XpExchangeGUI}）：最大等级显示与输入框长度按 BI 配置上限调整。
 */
@Mixin(value = com.wintercogs.beyonddimensions.client.gui.XpExchangeGUI.class, remap = false)
public class XpExchangeGUIMixin {

    /** renderLabels 第 3 个 drawString（最大等级行）替换为配置值 */
    @ModifyArg(method = "renderLabels", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I",
            ordinal = 2), index = 1)
    private Component beyondintegration$maxLevelLabel(Component original) {
        if (!CommandConfig.xpRodTweaksEnabled()) return original; // 总开关关闭：显示 BD 原值
        return Component.translatable("menu.label.beyonddimensions.xp_exchange.max_level",
                CommandConfig.xpRodMaxTargetLevel());
    }

    /** 输入框长度上限：至少覆盖配置最大值的位数（默认 21863 为 5 位，原版固定 6） */
    @ModifyArg(method = "init", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/EditBox;setMaxLength(I)V"), index = 0)
    private int beyondintegration$fieldMaxLength(int original) {
        if (!CommandConfig.xpRodTweaksEnabled()) return original; // 总开关关闭：保持 BD 原长度
        return Math.max(original, Integer.toString(CommandConfig.xpRodMaxTargetLevel()).length());
    }
}
