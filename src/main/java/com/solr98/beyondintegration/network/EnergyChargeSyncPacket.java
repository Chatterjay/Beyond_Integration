package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.SuperbAmmoCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 自动充电开关同步包（S2C）：服务端将当前网络的自动充电启用状态同步给客户端，供 UI 显示使用 */
public class EnergyChargeSyncPacket {

    /** 自动充电是否启用 */
    private final boolean enabled;

    public EnergyChargeSyncPacket(boolean enabled) {
        this.enabled = enabled;
    }

    /** 写入启用标志到缓冲区 */
    public static void encode(EnergyChargeSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.enabled);
    }

    /** 从缓冲区读取启用标志并还原数据包 */
    public static EnergyChargeSyncPacket decode(FriendlyByteBuf buf) {
        return new EnergyChargeSyncPacket(buf.readBoolean());
    }

    /** 客户端收到后更新当前网络的自动充电状态 */
    public static void handle(EnergyChargeSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                SuperbAmmoCache.setEnergyCharge(msg.enabled);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
