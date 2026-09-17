package com.solr98.beyondintegration.mixin;
import com.atsuishio.superbwarfare.client.overlay.RenderContext;
import com.atsuishio.superbwarfare.data.gun.Ammo;
import com.atsuishio.superbwarfare.data.gun.AmmoConsumer;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.item.gun.GunItem;
import com.solr98.beyondintegration.client.SuperbAmmoCache;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.text.NumberFormat;

/**
 * 注入目标：Superb Warfare 的 {@code AmmoBarOverlay}（客户端弹药条 HUD 渲染器）。
 * 目的：在弹药条上方追加显示网络信息（网络名称、ID、能量）及网络中的弹药/物品数量，
 * 让玩家在持有枪械且已连接维度网络时能直观看到网络弹药余量。
 */
@Mixin(targets = "com.atsuishio.superbwarfare.client.overlay.AmmoBarOverlay", remap = false)
public class AmmoBarOverlayMixin {
    // 在原有弹药条渲染完成后，追加绘制网络名称/能量/弹药信息
    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void onRender(RenderContext ctx, CallbackInfo ci) {
        // 仅手持枪械、未骑乘、且缓存已有网络数据时绘制
        var player = ctx.getPlayer();
        if (player == null || player.getVehicle() != null) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof GunItem)) return;
        if (!SuperbAmmoCache.INSTANCE.hasData()) return;

        Font font = ctx.getMc().font;
        GuiGraphics gg = ctx.getGuiGraphics();
        int right = ctx.getScreenWidth() - 12;
        int y = ctx.getScreenHeight() - 60;

        int netId = SuperbAmmoCache.INSTANCE.getNetId();
        String netName = SuperbAmmoCache.INSTANCE.getNetworkName();
        String nl = !netName.isEmpty() ? netName + " (Net#" + netId + ")" : "(Net#" + netId + ")";
        gg.drawString(font, nl, right - font.width(nl), y - 29, 0x55FFFF, true);

        long en = SuperbAmmoCache.INSTANCE.getNetworkEnergy();
        if (en >= 0) { String es = NumberFormat.getIntegerInstance().format(en) + " FE"; gg.drawString(font, es, right - font.width(es), y - 19, 0xFFAA00, true); }

        // 依据枪械当前弹药消耗方式（玩家弹药/物品）分别显示网络余量
        GunData data = GunData.from(stack);
        if (data == null) return;
        var consumer = data.selectedAmmoConsumer();
        if (consumer == null) return;
        var ct = consumer.getType();
        String as = "";
        if (ct == AmmoConsumer.AmmoConsumeType.PLAYER_AMMO) {
            Ammo at = consumer.getPlayerAmmoType();
            if (at != null) {
                long c = SuperbAmmoCache.INSTANCE.getCount(at.serializationName);
                boolean inf = SuperbAmmoCache.INSTANCE.hasInfinite();
                if (c > 0 || inf) {
                    String cs = inf ? "INF" : NumberFormat.getIntegerInstance().format(c);
                    as = cs + " : " + Component.translatable(at.translationKey).getString();
                }
            }
        } else if (ct == AmmoConsumer.AmmoConsumeType.ITEM) {
            String raw = consumer.stack().isEmpty() ? null : BuiltInRegistries.ITEM.getKey(consumer.stack().getItem()).toString();
            if (raw != null) {
                String itemKey = "ITEM:" + raw;
                long c = SuperbAmmoCache.INSTANCE.getItemCount(itemKey, false);
                if (c < 0) {
                    // 现查现用（换枪检测在客户端 tick 统一触发，此处兜底）
                    int curNetId = SuperbAmmoCache.INSTANCE.getNetId();
                    if (curNetId >= 0) {
                        SuperbAmmoCache.INSTANCE.requestItems(curNetId,
                                java.util.Collections.singletonList(itemKey), false);
                    }
                    c = 0;
                }
                if (c > 0) {
                    var item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(raw));
                    if (item != null && item != Items.AIR) as = NumberFormat.getIntegerInstance().format(c) + " : " + item.getName(ItemStack.EMPTY).getString();
                }
            }
        }
        if (!as.isEmpty()) gg.drawString(font, as, right - font.width(as), y - 9, 0x55FFFF, true);
    }
}

