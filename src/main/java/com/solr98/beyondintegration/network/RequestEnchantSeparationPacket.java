package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 附魔分离状态查询请求包（C2S）：客户端请求服务端返回当前网络的附魔分离开关状态，服务端以 EnchantSeparationSyncPacket 应答 */
public class RequestEnchantSeparationPacket {

    /** 空参构造：该请求包不携带数据 */
    public RequestEnchantSeparationPacket() {}

    /** 无数据可写 */
    public static void encode(RequestEnchantSeparationPacket msg, FriendlyByteBuf buf) {}

    /** 无数据可读，直接还原空包 */
    public static RequestEnchantSeparationPacket decode(FriendlyByteBuf buf) {
        return new RequestEnchantSeparationPacket();
    }

    /** 服务端执行：读取网络附魔分离启用状态并回发 EnchantSeparationSyncPacket */
    public static void handle(RequestEnchantSeparationPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) return;
            boolean enabled = net instanceof EnchantSeparationAccessor ea
                    && ea.beyond$isEnchantSeparationEnabled();
            PacketHandler.sendToPlayer(player, new EnchantSeparationSyncPacket(enabled));
        });
        ctx.get().setPacketHandled(true);
    }
}
