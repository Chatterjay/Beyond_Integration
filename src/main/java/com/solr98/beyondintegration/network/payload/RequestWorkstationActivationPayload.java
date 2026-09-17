package com.solr98.beyondintegration.network.payload;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.workstation.WorkstationActivation;
import com.solr98.beyondintegration.network.PacketHandler;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** 工作台激活状态查询包（C2S，空载荷）：打开 BD 终端时请求当前网络的献祭激活状态，服务端以 WorkstationActivationSyncPayload 应答 */
public record RequestWorkstationActivationPayload() implements CustomPacketPayload {
    public static final Type<RequestWorkstationActivationPayload> TYPE = new Type<>(
            ResourceLocation.parse(BeyondIntegration.MODID + ":request_workstation_activation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestWorkstationActivationPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestWorkstationActivationPayload());

    public static void handle(final RequestWorkstationActivationPayload packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            boolean enabled = CommandConfig.isWorkstationActivationEnabled();
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            List<String> ids = net == null ? List.of() : WorkstationActivation.activatedIds(net);
            PacketHandler.sendToPlayer(player, new WorkstationActivationSyncPayload(enabled, ids));
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
