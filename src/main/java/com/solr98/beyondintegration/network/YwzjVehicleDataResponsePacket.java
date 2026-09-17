package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.client.YwzjVehicleCache;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * 一物质造(载具)数据响应包（服务端 → 客户端）。
 * 响应载具相关数据请求，携带网络能量、名称与弹药全量计数，
 * 由 handle 更新客户端 {@link YwzjVehicleCache}。
 * TYPE: beyond_integration:ywzj_vehicle_data_response；STREAM_CODEC 按 map 逐条序列化。
 */
public record YwzjVehicleDataResponsePacket(int netId, long energy, String networkName, Map<String, Long> ammoMap) implements CustomPacketPayload {
    public static final Type<YwzjVehicleDataResponsePacket> TYPE = new Type<>(
            ResourceLocation.parse(BeyondIntegration.MODID + ":ywzj_vehicle_data_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, YwzjVehicleDataResponsePacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull YwzjVehicleDataResponsePacket decode(RegistryFriendlyByteBuf buf) {
            int netId = buf.readInt();
            long energy = buf.readLong();
            String networkName = buf.readUtf();
            int ammoSize = buf.readVarInt();
            Map<String, Long> ammoMap = new HashMap<>();
            for (int i = 0; i < ammoSize; i++) {
                ammoMap.put(buf.readUtf(), buf.readVarLong());
            }
            return new YwzjVehicleDataResponsePacket(netId, energy, networkName, ammoMap);
        }
        @Override
        public void encode(RegistryFriendlyByteBuf buf, YwzjVehicleDataResponsePacket p) {
            buf.writeInt(p.netId);
            buf.writeLong(p.energy);
            buf.writeUtf(p.networkName);
            buf.writeVarInt(p.ammoMap.size());
            for (var entry : p.ammoMap.entrySet()) {
                buf.writeUtf(entry.getKey());
                buf.writeVarLong(entry.getValue());
            }
        }
    };

    public static void handle(final YwzjVehicleDataResponsePacket packet, final IPayloadContext context) {
        // 客户端处理：将载具能量与弹药数据写入 YwzjVehicleCache
        context.enqueueWork(() -> YwzjVehicleCache.INSTANCE.update(packet.netId, packet.energy, packet.networkName, packet.ammoMap));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

