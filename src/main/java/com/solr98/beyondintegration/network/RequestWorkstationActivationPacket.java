package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.workstation.WorkstationActivation;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** 工作台激活状态查询包（C2S）：打开 BD 终端时请求当前网络的献祭激活状态，服务端以 WorkstationActivationSyncPacket 应答 */
public class RequestWorkstationActivationPacket {

    /** 空参构造：该请求包不携带数据 */
    public RequestWorkstationActivationPacket() {}

    /** 无数据可写 */
    public static void encode(RequestWorkstationActivationPacket msg, FriendlyByteBuf buf) {}

    /** 无数据可读，直接还原空包 */
    public static RequestWorkstationActivationPacket decode(FriendlyByteBuf buf) {
        return new RequestWorkstationActivationPacket();
    }

    /** 服务端执行：读取开关与当前网络已激活工作台列表并回发同步包 */
    public static void handle(RequestWorkstationActivationPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            boolean enabled = CommandConfig.isWorkstationActivationEnabled();
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            List<String> ids = net == null ? List.of() : WorkstationActivation.activatedIds(net);
            PacketHandler.sendToPlayer(player, new WorkstationActivationSyncPacket(enabled, ids));
        });
        ctx.get().setPacketHandled(true);
    }
}
