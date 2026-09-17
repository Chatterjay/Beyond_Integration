package com.solr98.beyondintegration.network.payload;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.feature.workstation.WorkstationActivation;
import com.solr98.beyondintegration.network.PacketHandler;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * 工作台献祭激活请求包（C2S）：客户端点击未激活的工作站按钮时发送，
 * 服务端从网络存储扣除献祭物品并激活该工作台（网络级），随后回发状态同步包。
 */
public record ActivateWorkstationPayload(String id) implements CustomPacketPayload {
    public static final Type<ActivateWorkstationPayload> TYPE = new Type<>(
            ResourceLocation.parse(BeyondIntegration.MODID + ":activate_workstation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ActivateWorkstationPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ActivateWorkstationPayload::id, ActivateWorkstationPayload::new);

    public static void handle(final ActivateWorkstationPayload packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            String id = packet.id();
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
                        PacketHandler.sendToPlayer(player, new WorkstationActivationSyncPayload(true, ids));
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
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
