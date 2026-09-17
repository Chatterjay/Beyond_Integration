package com.solr98.beyondintegration.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;
import org.ywzj.vehicle.vehicle.LocalVehiclePlayer;
import org.ywzj.vehicle.vehicle.part.PartUnit;
import org.ywzj.vehicle.vehicle.part.WeaponUnit;
import org.ywzj.vehicle.vehicle.weapon.AbstractVehicleWeapon;

import java.text.NumberFormat;
import java.util.Optional;

/**
 * 载具网络信息 HUD 叠加层。
 * 玩家驾驶云之载具且载具已绑定网络时，在屏幕左下角
 * 渲染网络名、能量与当前武器对应弹药的网络总存量。
 */
public class NetworkOverlay implements LayeredDraw.Layer {

    public static final NetworkOverlay INSTANCE = new NetworkOverlay(); // 全局单例

    @Override
    /** 每帧渲染 HUD 内容 */
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        var mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        if (!LocalVehiclePlayer.instance.onVehicle()) return; // 未在载具上不渲染

        var cache = YwzjVehicleCache.INSTANCE;
        if (!cache.hasData() || cache.getNetId() < 0) return; // 无网络数据不渲染

        AbstractVehicle vehicle = LocalVehiclePlayer.instance.getVehicle();
        var font = mc.font;
        int screenHeight = guiGraphics.guiHeight();
        int x = 4;
        int y = screenHeight - 35; // 固定锚定在左下角

        String netName = cache.getNetworkName();
        int netId = cache.getNetId();
        Component netLine = !netName.isEmpty()
                ? Component.translatable("hud.beyond_integration.network_title.name", netName, netId)
                : Component.translatable("hud.beyond_integration.network_title", "Net#" + netId);
        guiGraphics.drawString(font, netLine, x, y - 29, 0x55FFFF, true);

        long netEnergy = cache.getEnergy();
        if (netEnergy > 0) {
            String energyStr = Component.translatable("hud.beyond_integration.energy_line",
                    NumberFormat.getIntegerInstance().format(netEnergy)).getString();
            guiGraphics.drawString(font, energyStr, x, y - 19, 0xFFAA00, true);
        }

        PartUnit<?> opUnit = vehicle.getOwnOperatorUnit(player);
        if (!(opUnit instanceof WeaponUnit weaponUnit)) return; // 非武器位不渲染弹药行

        Optional<AbstractVehicleWeapon<?>> weaponOpt = weaponUnit.getCurrentWeapon();
        if (weaponOpt.isEmpty()) return;
        AbstractVehicleWeapon<?> weapon = weaponOpt.get();
        var ammoType = weapon.getData().getReload().getAmmo();

        // 汇总网络中与当前武器弹药类型匹配的全部存量
        long netCount = 0;
        if (ammoType != null) {
            for (var entry : cache.getAmmoMap().entrySet()) {
                var item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(entry.getKey()));
                if (item != null && item != Items.AIR && ammoType.test(item.getDefaultInstance())) {
                    netCount += entry.getValue();
                }
            }
        }

        String ammoStr = weapon.getDisplayName().getString() + " : " + NumberFormat.getIntegerInstance().format(netCount);
        guiGraphics.drawString(font, ammoStr, x, y - 9, 0x55FFFF, true); // 武器名 + 网络弹药总量
    }
}

