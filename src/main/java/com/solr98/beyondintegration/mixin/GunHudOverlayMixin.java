package com.solr98.beyondintegration.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.solr98.beyondintegration.client.TaczAmmoCache;
import com.solr98.beyondintegration.handler.TaczAmmoExtractor;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Map;

/**
 * 注入目标：TacZ 的 {@code GunHudOverlay}（客户端枪械 HUD 覆盖层）。
 * 目的：在原弹药计数基础上，将各维度网络中的弹药数量合并进 HUD 显示，
 * 并展示网络名称与总弹药数（无限网络显示 infinity）。
 */
@Mixin(targets = "com.tacz.guns.client.gui.overlay.GunHudOverlay", remap = false)
public class GunHudOverlayMixin {
    // 当前枪械弹药在各网络中的数量缓存（网络ID -> 数量），以及对应的弹药 ID
    @Unique private static Map<Integer, Integer> beyond$networkAmmoMap = Map.of();
    @Unique private static ResourceLocation beyond$lastAmmoId = null;

    // 拦截 handleInventoryAmmo：计算原库存弹药后，刷新网络弹药数量缓存并请求快照
    @Inject(method = "handleInventoryAmmo", at = @At("RETURN"))
    private static void beyond$onHandleInventoryAmmo(ItemStack stack, Inventory inventory, CallbackInfo ci) {
        ResourceLocation ammoId = TaczAmmoExtractor.getAmmoIdClient(stack);
        if (ammoId == null) return;
        beyond$networkAmmoMap = TaczAmmoCache.getAllNetworkCounts(ammoId);
        beyond$lastAmmoId = ammoId;
        TaczAmmoCache.requestQuick(ammoId);
    }

    // 在原 HUD 渲染后追加绘制网络名称与网络弹药总数
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"), remap = false)
    private void beyond$onRender(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof IGun)) return;
        if (beyond$networkAmmoMap.isEmpty() || beyond$lastAmmoId == null) return;

        int netId = -1;
        long displayCount = 0;
        boolean infinite = false;
        for (var entry : beyond$networkAmmoMap.entrySet()) {
            int raw = entry.getValue();
            if (entry.getKey() >= 0 && raw > 0) {
                netId = entry.getKey();
                if (raw == Integer.MAX_VALUE) { infinite = true; displayCount = 0; }
                else displayCount += raw;
                break;
            }
        }
        if (netId < 0) return;

        Font font = mc.font;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        float s = 0.8f;
        pose.scale(s, s, 1);

        float colonX = (mc.getWindow().getGuiScaledWidth() - 45) / s;
        float y = (mc.getWindow().getGuiScaledHeight() - 53) / s;

        Component netNameComp = TaczAmmoCache.getNetworkDisplayName(netId, beyond$lastAmmoId);
        String countStr = infinite ? "infinity" : String.valueOf((int) Math.min(displayCount, Integer.MAX_VALUE));

        String nameStr = netNameComp.getString();
        String colonStr = ": ";

        int nameEndX = (int) colonX;
        int colonDrawX = (int) colonX;
        int countStartX = (int) colonX + font.width(colonStr);

        graphics.drawString(font, nameStr, nameEndX - font.width(nameStr), (int) y, 0x55FFFF, false);
        graphics.drawString(font, colonStr, colonDrawX, (int) y, 0x55FFFF, false);
        graphics.drawString(font, countStr, countStartX, (int) y, 0x55FFFF, false);
        pose.popPose();
    }
}

