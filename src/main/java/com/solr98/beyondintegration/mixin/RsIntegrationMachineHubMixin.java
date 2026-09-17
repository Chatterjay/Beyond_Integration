package com.solr98.beyondintegration.mixin;

import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.ScreenEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * rs_integration（RI）BD 界面左侧栏手动按钮主动兼容。
 * RI 会在 BD 左侧栏同列（x = guiLeft-18）手动渲染两个 16×16 按钮
 * （机器中心 y = top+6+144、维度共振盘再下一格），与 BD 侧栏按钮位置重叠。
 * 这里在 BD 界面屏蔽其渲染与点击，改由本模组以 BD 侧栏按钮形式接管（见 DimensionsNetGUIMixin）。
 * RI 未安装时（@Pseudo）本 mixin 自动跳过。
 */
@Pseudo
@Mixin(targets = "com.huanghuang.rsintegration.machine.BeyondDimensionsMachineHubClient", remap = false)
public class RsIntegrationMachineHubMixin {

    /** 屏蔽机器中心按钮渲染（仅 BD 界面） */
    @Inject(method = "renderMachineCenterEntry", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void beyond$hideMachineCenter(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (screen instanceof DimensionsNetGUI) ci.cancel();
    }

    /** 屏蔽维度共振盘按钮渲染（仅 BD 界面） */
    @Inject(method = "renderResonanceEntry", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void beyond$hideResonance(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (screen instanceof DimensionsNetGUI) ci.cancel();
    }

    /** 屏蔽机器中心按钮点击（BD 界面由本模组接管） */
    @Inject(method = "handleMachineCenterClick", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void beyond$blockMachineCenterClick(ScreenEvent.MouseButtonPressed.Pre event, CallbackInfoReturnable<Boolean> cir) {
        if (event.getScreen() instanceof DimensionsNetGUI) cir.setReturnValue(false);
    }

    /** 屏蔽维度共振盘按钮点击（BD 界面由本模组接管） */
    @Inject(method = "handleResonanceClick", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void beyond$blockResonanceClick(ScreenEvent.MouseButtonPressed.Pre event, CallbackInfoReturnable<Boolean> cir) {
        if (event.getScreen() instanceof DimensionsNetGUI) cir.setReturnValue(false);
    }
}
