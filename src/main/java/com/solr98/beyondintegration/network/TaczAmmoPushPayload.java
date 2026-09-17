package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.client.TaczAmmoCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Tacz 弹药推送包（服务端 → 客户端）。
 * 服务端主动推送某网络各弹药 ID 的数量变化，客户端 handle 更新 {@link TaczAmmoCache}。
 * TYPE: beyond_integration:tacz_ammo_push；STREAM_CODEC 按 map 逐条读写字符串与数量。
 */
public record TaczAmmoPushPayload(int netId, String netName, Map<String, Integer> ammo) implements CustomPacketPayload {

    public static final Type<TaczAmmoPushPayload> TYPE = new Type<>(ResourceLocation.parse(BeyondIntegration.MODID + ":tacz_ammo_push"));
    public static final StreamCodec<FriendlyByteBuf, TaczAmmoPushPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public @NotNull TaczAmmoPushPayload decode(FriendlyByteBuf buf) {
            int netId = buf.readVarInt();
            String netName = buf.readUtf(256);
            int size = buf.readVarInt();
            Map<String, Integer> ammo = new HashMap<>(size);
            for (int i = 0; i < size; i++) {
                ammo.put(buf.readUtf(256), buf.readVarInt());
            }
            return new TaczAmmoPushPayload(netId, netName, ammo);
        }
        @Override public void encode(FriendlyByteBuf buf, TaczAmmoPushPayload p) {
            buf.writeVarInt(p.netId);
            buf.writeUtf(p.netName, 256);
            buf.writeVarInt(p.ammo.size());
            for (var entry : p.ammo.entrySet()) {
                buf.writeUtf(entry.getKey(), 256);
                buf.writeVarInt(entry.getValue());
            }
        }
    };

    public static void handle(final TaczAmmoPushPayload packet, final IPayloadContext context) {
        // 仅客户端处理：将服务端推送的弹药数据应用到 TaczAmmoCache
        context.enqueueWork(() -> {
            if (context.player().level().isClientSide())
                TaczAmmoCache.applyPush(packet.netId, packet.netName, packet.ammo);
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
