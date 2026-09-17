package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.compat.RsIntegrationCompat;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * rs_integration（RI，Forge 侧模组）自动进食按钮主动兼容。
 * RI 会自行把 3 个 64×20 按钮固定到 BD 界面左侧外下方（每帧 setX/setY 覆盖 BD 侧栏布局），
 * 与本模组左侧栏按钮重叠。这里：
 *  1) 屏蔽 RI 自身的按钮安装入口（仅放行本模组 RsIntegrationCompat.installControls 发起的调用）；
 *  2) 屏蔽 RI 对按钮坐标的覆盖（setX/setY 重定向为空操作）。
 * 改由本模组在 DimensionsNetGUIMixin 中把 RI 按钮统一加入 BD 左侧按钮栏。
 * RI 未安装时（@Pseudo）本 mixin 自动跳过。
 */
@Pseudo
@Mixin(targets = "com.huanghuang.rsintegration.autoeat.client.AutoEatClientEvents", remap = false)
public class RsIntegrationAutoEatMixin {

    /** 屏蔽 RI 自身（含其 BD GUI mixin）的单参安装调用（仅限 BD 界面，放行本模组发起的调用） */
    @Inject(method = "installControlsAfterNativeInit(Lnet/minecraft/client/gui/screens/Screen;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void beyond$blockNativeInstall1(Screen screen, CallbackInfo ci) {
        if (!RsIntegrationCompat.isInstalling() && screen instanceof DimensionsNetGUI) ci.cancel();
    }

    /** 屏蔽 RI 自身（含其 BD GUI mixin）的带 adder 安装调用（仅限 BD 界面，本模组发起时放行） */
    @Inject(method = "installControlsAfterNativeInit(Lnet/minecraft/client/gui/screens/Screen;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void beyond$blockNativeInstall2(Screen screen, Consumer<Button> adder, CallbackInfo ci) {
        if (!RsIntegrationCompat.isInstalling() && screen instanceof DimensionsNetGUI) ci.cancel();
    }

    /** 屏蔽 RI 对接管按钮 X 坐标的覆盖（其他界面/未接管按钮保持 RI 原定位） */
    @Redirect(method = "installControls",
            at = @At(value = "INVOKE",
                    target = "Lcom/huanghuang/rsintegration/autoeat/client/AutoEatClientEvents$AutoEatButton;m_252865_(I)V"),
            remap = false, require = 0)
    private static void beyond$skipSetX(Button button, int x) {
        if (!RsIntegrationCompat.isManaged(button)) button.setX(x);
    }

    /** 屏蔽 RI 对接管按钮 Y 坐标的覆盖（其他界面/未接管按钮保持 RI 原定位） */
    @Redirect(method = "installControls",
            at = @At(value = "INVOKE",
                    target = "Lcom/huanghuang/rsintegration/autoeat/client/AutoEatClientEvents$AutoEatButton;m_253211_(I)V"),
            remap = false, require = 0)
    private static void beyond$skipSetY(Button button, int y) {
        if (!RsIntegrationCompat.isManaged(button)) button.setY(y);
    }
}
