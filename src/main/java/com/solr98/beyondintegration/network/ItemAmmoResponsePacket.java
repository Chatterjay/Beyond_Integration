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
 * ITEM 型弹药"现查现用"响应包（服务端 → 客户端）。
 * 响应 {@link RequestItemAmmoPacket}，返回指定网络内各物品弹药的计数；
 * 由 handle 更新客户端 {@link SuperbAmmoCache}。
 * TYPE: beyond_integration:item_ammo_response；STREAM_CODEC 使用 RegistryFriendlyByteBuf 序列化。
 */
public record ItemAmmoResponsePacket(int netId, Map<String, Long> counts) implements CustomPacketPayload {
    public static final Type<ItemAmmoResponsePacket> TYPE = new Type<>(
            ResourceLocation.parse(BeyondIntegration.MODID + ":item_ammo_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemAmmoResponsePacket> STREAM_CODEC = new StreamCodec<>() {
        @Override public @NotNull ItemAmmoResponsePacket decode(RegistryFriendlyByteBuf buf) {
            int netId = buf.readVarInt();
            int size = buf.readVarInt();
            Map<String, Long> counts = new HashMap<>(size);
            for (int i = 0; i < size; i++) counts.put(buf.readUtf(), buf.readVarLong());
            return new ItemAmmoResponsePacket(netId, counts);
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, ItemAmmoResponsePacket p) {
            buf.writeVarInt(p.netId);
            buf.writeVarInt(p.counts.size());
            p.counts.forEach((k, v) -> { buf.writeUtf(k); buf.writeVarLong(v); });
        }
    };

    public static void handle(final ItemAmmoResponsePacket packet, final IPayloadContext context) {
        // 将服务端返回的物品计数写入客户端缓存 SuperbAmmoCache
        context.enqueueWork(() -> SuperbAmmoCache.INSTANCE.updateItemCounts(packet.netId, packet.counts));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
