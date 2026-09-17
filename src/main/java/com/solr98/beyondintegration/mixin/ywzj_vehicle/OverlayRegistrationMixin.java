package com.solr98.beyondintegration.mixin.ywzj_vehicle;

import com.solr98.beyondintegration.client.NetworkOverlay;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.ywzj.vehicle.client.ClientSetupHandler;

/**
 * HUD 注册 Mixin：注入遥装甲载具的 ClientSetupHandler.onRegisterHud，
 * 在聊天栏图层下方注册本模组的载具网络信息覆盖层 NetworkOverlay。
 */
@Mixin(ClientSetupHandler.class)
public class OverlayRegistrationMixin {

    /** 本模组网络信息覆盖层的注册 ID */
    private static final ResourceLocation BCE_NETWORK_OVERLAY = ResourceLocation.parse("ywzj_vehicle:beyond_network");

    /** 在聊天栏图层下方注册 NetworkOverlay，渲染载具绑定的网络信息 */
    @Inject(method = "onRegisterHud", at = @At("TAIL"), remap = false)
    private static void beyond$registerNetworkOverlay(RegisterGuiLayersEvent event, CallbackInfo ci) {
        event.registerBelow(VanillaGuiLayers.CHAT, BCE_NETWORK_OVERLAY, NetworkOverlay.INSTANCE);
    }
}

