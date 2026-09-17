package com.solr98.beyondintegration.network;
import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import java.util.HashMap;

/**
 * Superb Warfare 弹药状态请求包（客户端 → 服务端）。
 * 请求指定网络（netId<0 时回退到菜单/主网络）的弹药、能量与附魔分离状态，
 * 服务端 handle 以 {@link SuperbAmmoStatusResponsePacket} 全量回传。
 * TYPE: beyond_integration:request_superb_ammo_status；STREAM_CODEC 仅读写 netId。
 */
public record RequestSuperbAmmoStatusPacket(int netId) implements CustomPacketPayload {
    public static final Type<RequestSuperbAmmoStatusPacket> TYPE = new Type<>(ResourceLocation.parse(BeyondIntegration.MODID + ":request_superb_ammo_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestSuperbAmmoStatusPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override public @NotNull RequestSuperbAmmoStatusPacket decode(RegistryFriendlyByteBuf buf) { return new RequestSuperbAmmoStatusPacket(buf.readInt()); }
        @Override public void encode(RegistryFriendlyByteBuf buf, RequestSuperbAmmoStatusPacket p) { buf.writeInt(p.netId); }
    };

    public static void handle(final RequestSuperbAmmoStatusPacket packet, final IPayloadContext context) {
        // 服务端处理：定位网络并回发弹药/能量/附魔分离状态全量快照
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            DimensionsNet net = null;
            if (packet.netId >= 0) net = DimensionsNet.getNetFromId(packet.netId);
            if (net == null) net = com.solr98.beyondintegration.handler.MenuNetIdHelper.getNetFromMenu(player);
            if (net == null) net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null || !(net instanceof SuperbAmmoAccessor acc)) return;
            var map = acc.getSuperbAmmo();
            if (map.isEmpty()) return;
            long energy = net.getUnifiedStorage().getStackByKey(EnergyStackKey.INSTANCE).amount();
            boolean enchantSep = net instanceof EnchantSeparationAccessor ea && ea.beyond$isEnchantSeparationEnabled();
            String name = net instanceof com.solr98.beyondintegration.handler.NetworkNameProvider nnp ? nnp.getCustomName() : "";
            PacketDistributor.sendToPlayer(player, new SuperbAmmoStatusResponsePacket(
                    net.getId(), name, energy, enchantSep, new HashMap<>(map), null));
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

