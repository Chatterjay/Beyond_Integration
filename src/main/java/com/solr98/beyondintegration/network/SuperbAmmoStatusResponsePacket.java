package com.solr98.beyondintegration.network;
import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.client.SuperbAmmoCache;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SW 固定项全量快照包（服务端 → 客户端，双向注册）。
 * ammoList：载具可用弹药列表（"ITEM:<注册名>" 或弹药 serializationName），玩家侧为 null。
 * 由 handle 更新客户端 {@link SuperbAmmoCache}；fromNet 供服务端便捷构造。
 * TYPE: beyond_integration:superb_ammo_status_response；STREAM_CODEC 以 isVehicle 标志控制弹药列表读写。
 */
public record SuperbAmmoStatusResponsePacket(int netId, String networkName, long energy,
                                             boolean enchantSeparation, Map<String, Long> ammoMap,
                                             List<String> ammoList) implements CustomPacketPayload {
    public static final Type<SuperbAmmoStatusResponsePacket> TYPE = new Type<>(
            ResourceLocation.parse(BeyondIntegration.MODID + ":superb_ammo_status_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SuperbAmmoStatusResponsePacket> STREAM_CODEC = new StreamCodec<>() {
        @Override public @NotNull SuperbAmmoStatusResponsePacket decode(RegistryFriendlyByteBuf buf) {
            int netId = buf.readVarInt();
            boolean isVehicle = buf.readBoolean();
            String networkName = buf.readUtf();
            long energy = buf.readVarLong();
            boolean enchantSep = buf.readBoolean();
            int size = buf.readVarInt();
            Map<String, Long> ammo = new HashMap<>(size);
            for (int i = 0; i < size; i++) ammo.put(buf.readUtf(), buf.readVarLong());
            List<String> ammoList = null;
            if (isVehicle) {
                int listSize = buf.readVarInt();
                ammoList = new ArrayList<>(listSize);
                for (int i = 0; i < listSize; i++) ammoList.add(buf.readUtf());
            }
            return new SuperbAmmoStatusResponsePacket(netId, networkName, energy, enchantSep, ammo, ammoList);
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, SuperbAmmoStatusResponsePacket p) {
            buf.writeVarInt(p.netId);
            boolean isVehicle = p.ammoList != null;
            buf.writeBoolean(isVehicle);
            buf.writeUtf(p.networkName);
            buf.writeVarLong(p.energy);
            buf.writeBoolean(p.enchantSeparation);
            buf.writeVarInt(p.ammoMap.size());
            p.ammoMap.forEach((k, v) -> { buf.writeUtf(k); buf.writeVarLong(v); });
            if (isVehicle) {
                buf.writeVarInt(p.ammoList.size());
                p.ammoList.forEach(buf::writeUtf);
            }
        }
    };

    public static void handle(final SuperbAmmoStatusResponsePacket packet, final IPayloadContext context) {
        // 客户端处理：将全量快照写入 SuperbAmmoCache（含载具弹药列表）
        context.enqueueWork(() -> SuperbAmmoCache.INSTANCE.update(
                packet.netId, packet.ammoList != null, packet.networkName, packet.ammoMap,
                packet.energy, packet.enchantSeparation, packet.ammoList));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** 由网络对象构造玩家侧快照包（读取附魔分离开关，弹药列表置空） */
    public static SuperbAmmoStatusResponsePacket fromNet(DimensionsNet net, Map<String, Long> ammo,
                                                         long energy, String name) {
        boolean enchantSep = net instanceof com.solr98.beyondintegration.handler.EnchantSeparationAccessor ea
                && ea.beyond$isEnchantSeparationEnabled();
        return new SuperbAmmoStatusResponsePacket(net.getId(), name, energy, enchantSep, ammo, null);
    }
}
