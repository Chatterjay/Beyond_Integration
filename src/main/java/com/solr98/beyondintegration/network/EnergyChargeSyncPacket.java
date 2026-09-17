package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.client.EnergyChargeState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 自动充电开关同步包（S2C）：服务端将指定网络的自动充电启用状态同步给客户端，
 * 客户端写入独立状态 EnergyChargeState（不依赖 SW 弹药快照）。
 * 由切换应答（ToggleEnergyChargePacket）与状态请求应答（RequestEnergyChargePacket）触发。
 */
public record EnergyChargeSyncPacket(int netId, boolean enabled) implements CustomPacketPayload {
    public static final Type<EnergyChargeSyncPacket> TYPE = new Type<>(
            ResourceLocation.parse(BeyondIntegration.MODID + ":energy_charge_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EnergyChargeSyncPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EnergyChargeSyncPacket::netId,
            ByteBufCodecs.BOOL, EnergyChargeSyncPacket::enabled,
            EnergyChargeSyncPacket::new);

    public static void handle(final EnergyChargeSyncPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().isClientbound()) {
                EnergyChargeState.INSTANCE.set(packet.netId(), packet.enabled());
            }
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
