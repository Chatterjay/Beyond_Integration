package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.workstation.WorkstationActivation;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * 工作台献祭激活请求包（C2S）：客户端点击未激活的工作站按钮时发送，
 * 服务端从网络存储扣除献祭物品并激活该工作台（网络级），随后回发状态同步包。
 */
public class ActivateWorkstationPacket {

    /** 工作台稳定 ID（anvil/cut/grind/smith/enchant） */
    private final String id;

    public ActivateWorkstationPacket(String id) {
        this.id = id == null ? "" : id;
    }

    /** 写入工作台 ID */
    public static void encode(ActivateWorkstationPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.id);
    }

    /** 读取工作台 ID */
    public static ActivateWorkstationPacket decode(FriendlyByteBuf buf) {
        return new ActivateWorkstationPacket(buf.readUtf());
    }

    /** 服务端执行：尝试献祭激活并反馈结果（成功时推送最新激活状态） */
    public static void handle(ActivateWorkstationPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            String id = msg.id;
            if (!WorkstationActivation.isActivatable(id)) return;

            Component modeName = Component.translatable("gui.beyond_integration.mode." + id);
            WorkstationActivation.Result result = WorkstationActivation.tryActivate(player, id);
            switch (result) {
                case SUCCESS -> {
                    player.displayClientMessage(Component.translatable(
                            "message.beyond_integration.workstation.activated",
                            WorkstationActivation.costName(id), modeName), false);
                    DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
                    if (net != null) {
                        List<String> ids = WorkstationActivation.activatedIds(net);
                        PacketHandler.sendToPlayer(player, new WorkstationActivationSyncPacket(true, ids));
                    }
                }
                case NO_ITEM -> player.displayClientMessage(Component.translatable(
                        "message.beyond_integration.workstation.no_item",
                        WorkstationActivation.costName(id), modeName), true);
                case NO_NETWORK -> player.displayClientMessage(Component.translatable(
                        "error.not_in_network"), true);
                default -> { /* ALREADY / DISABLED / INVALID：静默（客户端状态会由同步包纠正） */ }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
