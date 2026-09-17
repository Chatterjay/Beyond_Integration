package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** 客户端缓存过期时的全量拉取请求（仅固定项；ITEM 弹药走 RequestItemAmmoPacket 现查） */
public class RequestSuperbAmmoStatusPacket {

    /** 空构造：本包无字段 */
    public RequestSuperbAmmoStatusPacket() {}

    /** 编码：无字段，为空操作 */
    public static void encode(RequestSuperbAmmoStatusPacket msg, FriendlyByteBuf buf) {}

    /** 解码：无字段，直接返回新实例 */
    public static RequestSuperbAmmoStatusPacket decode(FriendlyByteBuf buf) {
        return new RequestSuperbAmmoStatusPacket();
    }

    /** 服务端处理：汇总主网络的弹药、能量、附魔分离开关与名称，回发 SuperbAmmoStatusResponsePacket */
    public static void handle(RequestSuperbAmmoStatusPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) return;

            Map<String, Long> fullMap = new HashMap<>();
            if (net instanceof SuperbAmmoAccessor acc) {
                fullMap.putAll(acc.getSuperbAmmo());
            }

            long energy = net.getUnifiedStorage().getStackByKey(EnergyStackKey.INSTANCE).amount();
            boolean enchantSep = net instanceof EnchantSeparationAccessor ea
                    && ea.beyond$isEnchantSeparationEnabled();
            String netName = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "";
            PacketHandler.sendToPlayer(player, new SuperbAmmoStatusResponsePacket(
                    net.getId(), netName, energy, enchantSep, fullMap, null));
        });
        ctx.get().setPacketHandled(true);
    }
}
