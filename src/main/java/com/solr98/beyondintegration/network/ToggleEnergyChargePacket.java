package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.handler.EnergyChargeAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S：客户端请求切换"主网络"的自动充电开关，
 * 服务端校验玩家为网络的经理/所有者后取反开关，并回发同步包。
 */
public class ToggleEnergyChargePacket {

    /** 空构造：本包无字段 */
    public ToggleEnergyChargePacket() {}

    /** 编码：无字段，为空操作 */
    public static void encode(ToggleEnergyChargePacket msg, FriendlyByteBuf buf) {}

    /** 解码：无字段，直接返回新实例 */
    public static ToggleEnergyChargePacket decode(FriendlyByteBuf buf) {
        return new ToggleEnergyChargePacket();
    }

    /** 服务端处理：权限校验通过后取反开关状态、标记网络已修改并回发 EnergyChargeSyncPacket */
    public static void handle(ToggleEnergyChargePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) return;
            if (!net.isManager(player) && !net.isOwner(player)) {
                player.sendSystemMessage(Component.translatable(
                        "message.beyond_integration.cannot_toggle_energy_charge"));
                return;
            }
            if (net instanceof EnergyChargeAccessor acc) {
                acc.beyond$setEnergyChargeEnabled(!acc.beyond$isEnergyChargeEnabled());
                net.setDirty();
                PacketHandler.sendToPlayer(player, new EnergyChargeSyncPacket(acc.beyond$isEnergyChargeEnabled()));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
