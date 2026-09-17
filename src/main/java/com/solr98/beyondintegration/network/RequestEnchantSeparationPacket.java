package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 附魔分离开关状态请求包（C2S，空载荷）：打开维度网络界面时请求当前网络的
 * 附魔分离启用状态，服务端以 EnchantSeparationSyncPacket 应答。
 * 独立于 SW 弹药状态请求，仅 BD+BI 时界面开关也能正确回显。
 */
public record RequestEnchantSeparationPacket() implements CustomPacketPayload {
    public static final Type<RequestEnchantSeparationPacket> TYPE = new Type<>(
            ResourceLocation.parse(BeyondIntegration.MODID + ":request_enchant_separation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestEnchantSeparationPacket> STREAM_CODEC =
            StreamCodec.unit(new RequestEnchantSeparationPacket());

    public static void handle(final RequestEnchantSeparationPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) return;
            boolean enabled = net instanceof EnchantSeparationAccessor ea
                    && ea.beyond$isEnchantSeparationEnabled();
            PacketHandler.sendToPlayer(player, new EnchantSeparationSyncPacket(net.getId(), enabled));
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
