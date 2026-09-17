package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.crafting.DimensionsEnchantMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 附魔台刷新请求（C2S）：服务端消耗燃料并重掷三槽候选。
 */
public record RefreshEnchantPacket(int containerId) {
    public static void encode(RefreshEnchantPacket m, FriendlyByteBuf b) { b.writeInt(m.containerId); }
    public static RefreshEnchantPacket decode(FriendlyByteBuf b) { return new RefreshEnchantPacket(b.readInt()); }

    public static void handle(RefreshEnchantPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp != null && sp.containerMenu instanceof DimensionsEnchantMenu menu
                    && menu.containerId == m.containerId) {
                menu.doRefresh(sp);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}