package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.client.GunSmithNetMode;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

/**
 * 与 taczaddon 的配方原料计数显示兼容（MixinPlugin 按版本门控；模式互斥）：
 *
 * - 非网络模式（addon 模式）：计数 = 背包 + 附近容器，addon 原样处理；
 * - 网络模式：计数 = 背包 + 网络（不含 addon 附近容器）。
 *
 * taczaddon 通过 ClientPayloadHandler.handleContainerReader 把服务端容器快照写入
 * 工作台界面的"虚拟容器"（VirtualContainerLoader），随后 getPlayerIngredientCount
 * 会以"背包 + 虚拟容器"展示。本 Mixin 在快照入口拦截：网络模式下清空虚拟容器并
 * 取消写入，使计数恒为"背包 + 网络"（BI 的 RETURN 叠加网络数量生效）。
 */
@Mixin(targets = "com.mafuyu404.taczaddon.client.ClientPayloadHandler", remap = false)
public abstract class TaczaddonExternalSourceMixin {

    @Inject(method = "handleContainerReader", at = @At("HEAD"), cancellable = true, remap = false)
    private static void beyond$hideExternalInNetMode(ArrayList<ItemStack> items, CallbackInfo ci) {
        if (!GunSmithNetMode.isNetworkMode()) return;
        try {
            Object screen = Minecraft.getInstance().screen;
            if (screen instanceof com.tacz.guns.client.gui.GunSmithTableScreen tableScreen) {
                if (screen instanceof com.mafuyu404.taczaddon.init.VirtualContainerLoader loader) {
                    loader.taczaddon$setVirtualContainer(new ArrayList<>());
                    tableScreen.updateIngredientCount();
                }
            }
        } catch (Throwable ignored) {}
        ci.cancel();
    }
}
