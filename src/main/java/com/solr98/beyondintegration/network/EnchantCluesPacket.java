package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.gui.DimensionsEnchantGUI;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 附魔台单槽"将获得"列表（S2C，悬停预览/升级标记数据）。
 */
public record EnchantCluesPacket(int containerId, int slot, List<EnchantmentInstance> clues) {
    public static void encode(EnchantCluesPacket m, FriendlyByteBuf b) {
        b.writeInt(m.containerId);
        b.writeInt(m.slot);
        b.writeInt(m.clues.size());
        for (EnchantmentInstance ei : m.clues) {
            b.writeInt(BuiltInRegistries.ENCHANTMENT.getId(ei.enchantment));
            b.writeInt(ei.level);
        }
    }

    public static EnchantCluesPacket decode(FriendlyByteBuf b) {
        int containerId = b.readInt();
        int slot = b.readInt();
        int n = b.readInt();
        List<EnchantmentInstance> clues = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Enchantment ench = BuiltInRegistries.ENCHANTMENT.byId(b.readInt());
            int level = b.readInt();
            if (ench != null) clues.add(new EnchantmentInstance(ench, level));
        }
        return new EnchantCluesPacket(containerId, slot, clues);
    }

    public static void handle(EnchantCluesPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof DimensionsEnchantGUI gui && gui.getMenu().containerId == m.containerId) {
                gui.acceptClues(m.slot(), m.clues());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}