package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.client.SuperbAmmoCache;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * SW 固定项增量/全量推送包（服务端 → 客户端）。
 * full=true 时 ammo 为全量、energy 为绝对值；否则 ammo 为增量、energy 为 delta。
 * 由 handle 调用客户端 {@link SuperbAmmoCache} 应用变更。
 * TYPE: beyond_integration:superb_ammo_delta；STREAM_CODEC 按 map 逐条序列化。
 */
public record SuperbAmmoDeltaS2CPacket(int netId, boolean isVehicle, boolean full,
                                       Map<String, Long> ammo, long energy, String netName,
                                       boolean enchantSeparation) implements CustomPacketPayload {
    public static final Type<SuperbAmmoDeltaS2CPacket> TYPE = new Type<>(
            ResourceLocation.parse(BeyondIntegration.MODID + ":superb_ammo_delta"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SuperbAmmoDeltaS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override public @NotNull SuperbAmmoDeltaS2CPacket decode(RegistryFriendlyByteBuf buf) {
            int netId = buf.readVarInt();
            boolean isVehicle = buf.readBoolean();
            boolean full = buf.readBoolean();
            String netName = buf.readUtf();
            long energy = buf.readVarLong();
            boolean enchantSep = buf.readBoolean();
            int size = buf.readVarInt();
            Map<String, Long> ammo = new HashMap<>(size);
            for (int i = 0; i < size; i++) ammo.put(buf.readUtf(), buf.readVarLong());
            return new SuperbAmmoDeltaS2CPacket(netId, isVehicle, full, ammo, energy, netName, enchantSep);
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, SuperbAmmoDeltaS2CPacket p) {
            buf.writeVarInt(p.netId);
            buf.writeBoolean(p.isVehicle);
            buf.writeBoolean(p.full);
            buf.writeUtf(p.netName);
            buf.writeVarLong(p.energy);
            buf.writeBoolean(p.enchantSeparation);
            buf.writeVarInt(p.ammo.size());
            p.ammo.forEach((k, v) -> { buf.writeUtf(k); buf.writeVarLong(v); });
        }
    };

    public static void handle(final SuperbAmmoDeltaS2CPacket packet, final IPayloadContext context) {
        // 客户端处理：将增量/全量数据应用到 SuperbAmmoCache
        context.enqueueWork(() -> SuperbAmmoCache.INSTANCE.applyDelta(
                packet.netId, packet.isVehicle, packet.full, packet.ammo,
                packet.energy, packet.netName, packet.enchantSeparation));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
