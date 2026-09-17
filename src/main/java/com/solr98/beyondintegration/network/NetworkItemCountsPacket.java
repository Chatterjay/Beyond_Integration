package com.solr98.beyondintegration.network;
import com.solr98.beyondintegration.client.CraftToast;
import com.solr98.beyondintegration.client.NetworkItemCache;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import java.util.HashMap;
import java.util.Map;

/**
 * 网络物品数量全量推送包（服务端 → 客户端）。
 * 携带网络内物品计数（key 为 "配方ID|槽位"）、网络信息及合成结果；
 * 由 handle 更新客户端 {@link NetworkItemCache} 并在有成品时弹出合成提示 {@link CraftToast}。
 * TYPE: beyond_integration:network_item_counts；STREAM_CODEC 按 replace 标志决定是否覆盖旧缓存。
 */
public record NetworkItemCountsPacket(Map<String, Long> counts, boolean replace, boolean hasNetwork, int netId,
                                       String netName, ItemStack resultItem, int resultCount) implements CustomPacketPayload {
    public static final Type<NetworkItemCountsPacket> TYPE = new Type<>(ResourceLocation.parse("beyond_integration:network_item_counts"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkItemCountsPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override public @NotNull NetworkItemCountsPacket decode(RegistryFriendlyByteBuf buf) {
            boolean replace = buf.readBoolean();
            boolean hasNetwork = buf.readBoolean();
            int netId = buf.readVarInt();
            String netName = buf.readUtf(256);
            int size = buf.readVarInt();
            Map<String, Long> counts = new HashMap<>();
            for (int i = 0; i < size; i++) counts.put(buf.readUtf(), buf.readVarLong());
            boolean hasResult = buf.readBoolean();
            ItemStack resultItem = hasResult ? ItemStack.STREAM_CODEC.decode(buf) : ItemStack.EMPTY;
            int resultCount = hasResult ? buf.readVarInt() : 0;
            return new NetworkItemCountsPacket(counts, replace, hasNetwork, netId, netName, resultItem, resultCount);
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, NetworkItemCountsPacket p) {
            buf.writeBoolean(p.replace);
            buf.writeBoolean(p.hasNetwork);
            buf.writeVarInt(p.netId);
            buf.writeUtf(p.netName, 256);
            buf.writeVarInt(p.counts.size());
            p.counts.forEach((k, v) -> { buf.writeUtf(k); buf.writeVarLong(v); });
            boolean hasResult = !p.resultItem.isEmpty();
            buf.writeBoolean(hasResult);
            if (hasResult) {
                ItemStack.STREAM_CODEC.encode(buf, p.resultItem);
                buf.writeVarInt(p.resultCount);
            }
        }
    };

    // 便捷构造：无合成结果时使用空 ItemStack 与数量 0
    public NetworkItemCountsPacket(Map<String, Long> counts, boolean replace, boolean hasNetwork, int netId, String netName) {
        this(counts, replace, hasNetwork, netId, netName, ItemStack.EMPTY, 0);
    }

    public static void handle(final NetworkItemCountsPacket packet, final IPayloadContext context) {
        // 客户端处理：覆盖物品缓存，若有合成结果则显示合成提示
        context.enqueueWork(() -> {
            NetworkItemCache.setAll(packet.counts, packet.hasNetwork, packet.netId, packet.netName);
            if (!packet.resultItem.isEmpty())
                CraftToast.show(packet.resultItem, packet.resultCount);
        });
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}

