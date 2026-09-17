package com.solr98.beyondintegration.mixin;

import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * rs_integration（RI）AutoEatButton 零尺寸渲染保护。
 * 本模组接管 RI 按钮后会在折叠时把隐藏按钮高度置 0（避免在 BD 侧栏占位），
 * 而 RI 的 renderWidget 会用按钮宽高做纹理平铺（blitRepeating），尺寸为 0 时除零崩溃。
 * 这里在尺寸无效时直接取消渲染（正常尺寸不受影响）。
 * RI 未安装时（@Pseudo）本 mixin 自动跳过。
 */
@Pseudo
@Mixin(targets = "com.huanghuang.rsintegration.autoeat.client.AutoEatClientEvents$AutoEatButton", remap = false)
public class RsAutoEatButtonGuardMixin {

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void beyond$guardZeroSize(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        net.minecraft.client.gui.components.AbstractWidget self =
                (net.minecraft.client.gui.components.AbstractWidget) (Object) this;
        if (self.getHeight() <= 0 || self.getWidth() <= 0) ci.cancel();
    }
}
