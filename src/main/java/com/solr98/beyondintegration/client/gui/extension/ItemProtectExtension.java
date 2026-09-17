package com.solr98.beyondintegration.client.gui.extension;

import com.solr98.beyondintegration.api.IDimensionsNetGUIExtension;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.payload.ProtectItemPayload;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * 物品附魔分离保护扩展。
 * 按住 Ctrl 右键点击背包物品可切换"保护分离"标记（写入 CUSTOM_DATA），
 * 同时发送服务端载荷，用于防止该物品被附魔分离处理。
 */
public class ItemProtectExtension implements IDimensionsNetGUIExtension {
    @Override public int priority() { return 2; } // 渲染优先级

    @Override
    public boolean onMouseClicked(DimensionsNetGUI<?> gui, double mx, double my, int button) {
        // 仅响应 Ctrl+右键
        if (button != 1 || !Screen.hasControlDown()) return false;
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        // 遍历菜单槽位，定位玩家背包中被点击的槽
        for (var slot : gui.getMenu().slots) {
            if (slot.container != player.getInventory()) continue;
            int sx = gui.getGuiLeft() + slot.x, sy = gui.getGuiTop() + slot.y;
            if (mx < sx || mx >= sx + 18 || my < sy || my >= sy + 18) continue;
            int slotIndex = slot.getSlotIndex();
            ItemStack stack = player.getInventory().getItem(slotIndex);
            if (stack.isEmpty()) return false;

            var customData = stack.get(DataComponents.CUSTOM_DATA);
            boolean isProtected = customData != null && customData.copyTag().getBoolean("beyond_integration:protect_sep"); // 读取现有保护标记

            // 切换保护标记（写入本地物品组件 + 通知服务端）
            if (isProtected) {
                stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> data.update(t -> t.remove("beyond_integration:protect_sep")));
            } else {
                stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> data.update(t -> t.putBoolean("beyond_integration:protect_sep", true)));
            }

            PacketHandler.sendToServer(new ProtectItemPayload(slotIndex));
            // 保护切换点击音效（对齐工作站按钮的 UI_BUTTON_CLICK 反馈）
            Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            player.displayClientMessage(Component.translatable(isProtected ? "message.beyond_integration.protect.removed" : "message.beyond_integration.protect.added", stack.getDisplayName()), true);
            return true;
        }
        return false;
    }
}
