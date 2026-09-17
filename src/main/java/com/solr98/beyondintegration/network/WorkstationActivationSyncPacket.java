package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.WorkstationActivationCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 工作台激活状态同步包（S2C）：
 * 下发献祭激活开关与当前网络已激活的工作台 ID 列表，供客户端工作站按钮显示锁定状态。
 */
public class WorkstationActivationSyncPacket {

    /** 献祭激活是否启用（false 时客户端不做锁定显示） */
    private final boolean enabled;
    /** 已激活的工作台 ID 列表 */
    private final List<String> activated;

    public WorkstationActivationSyncPacket(boolean enabled, List<String> activated) {
        this.enabled = enabled;
        this.activated = activated == null ? List.of() : new ArrayList<>(activated);
    }

    /** 写入开关与已激活列表 */
    public static void encode(WorkstationActivationSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.enabled);
        buf.writeVarInt(msg.activated.size());
        for (String id : msg.activated) buf.writeUtf(id);
    }

    /** 读取开关与已激活列表 */
    public static WorkstationActivationSyncPacket decode(FriendlyByteBuf buf) {
        boolean enabled = buf.readBoolean();
        int size = buf.readVarInt();
        List<String> ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) ids.add(buf.readUtf());
        return new WorkstationActivationSyncPacket(enabled, ids);
    }

    /** 客户端收到后更新本模组激活状态缓存 */
    public static void handle(WorkstationActivationSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                WorkstationActivationCache.update(msg.enabled, msg.activated);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
