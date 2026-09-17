package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.handler.EnergyChargeAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 自动充电开关状态查询请求包（C2S）：客户端请求服务端返回当前网络的自动充电开关状态，服务端以 EnergyChargeSyncPacket 应答 */
public class RequestEnergyChargePacket {

    /** 空参构造：该请求包不携带数据 */
    public RequestEnergyChargePacket() {}

    /** 无数据可写 */
    public static void encode(RequestEnergyChargePacket msg, FriendlyByteBuf buf) {}

    /** 无数据可读，直接还原空包 */
    public static RequestEnergyChargePacket decode(FriendlyByteBuf buf) {
        return new RequestEnergyChargePacket();
    }

    /** 服务端执行：读取网络自动充电开关状态并回发 EnergyChargeSyncPacket */
    public static void handle(RequestEnergyChargePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) return;
            boolean enabled = !(net instanceof EnergyChargeAccessor acc)
                    || acc.beyond$isEnergyChargeEnabled();
            PacketHandler.sendToPlayer(player, new EnergyChargeSyncPacket(enabled));
        });
        ctx.get().setPacketHandled(true);
    }
}
