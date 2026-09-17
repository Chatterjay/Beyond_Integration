package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.BeyondIntegration;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ITEM 型弹药"现查现用"请求（客户端 → 服务端）。
 * itemKeys 为 "ITEM:<注册名>" 格式。
 * 服务端 handle 按 itemKey 从网络统一存储取数，以 {@link ItemAmmoResponsePacket} 回复。
 * TYPE: beyond_integration:request_item_ammo；STREAM_CODEC 读写 netId 与键列表。
 */
public record RequestItemAmmoPacket(int netId, List<String> itemKeys) implements CustomPacketPayload {
    public static final Type<RequestItemAmmoPacket> TYPE = new Type<>(
            ResourceLocation.parse(BeyondIntegration.MODID + ":request_item_ammo"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestItemAmmoPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override public @NotNull RequestItemAmmoPacket decode(RegistryFriendlyByteBuf buf) {
            int netId = buf.readVarInt();
            int size = buf.readVarInt();
            List<String> keys = new ArrayList<>(size);
            for (int i = 0; i < size; i++) keys.add(buf.readUtf());
            return new RequestItemAmmoPacket(netId, keys);
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, RequestItemAmmoPacket p) {
            buf.writeVarInt(p.netId);
            buf.writeVarInt(p.itemKeys.size());
            p.itemKeys.forEach(buf::writeUtf);
        }
    };

    public static void handle(final RequestItemAmmoPacket packet, final IPayloadContext context) {
        // 服务端处理：从指定网络存储查询各 itemKey 对应物品的堆叠数量并回发
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (packet.itemKeys.isEmpty()) return;

            DimensionsNet net = DimensionsNet.getNetFromId(packet.netId);
            if (net == null) return;

            Map<String, Long> counts = new HashMap<>();
            for (String key : packet.itemKeys) {
                String raw = key.startsWith("ITEM:") ? key.substring(5) : key;
                ResourceLocation loc = ResourceLocation.tryParse(raw);
                if (loc == null) continue;
                ItemStack ref = new ItemStack(BuiltInRegistries.ITEM.get(loc));
                if (ref.isEmpty()) continue;
                long count = net.getUnifiedStorage().getStackByKey(new ItemStackKey(ref)).amount();
                counts.put(key, count);
            }

            PacketDistributor.sendToPlayer(player, new ItemAmmoResponsePacket(packet.netId, counts));
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
