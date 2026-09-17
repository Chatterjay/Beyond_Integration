package com.solr98.beyondintegration.network.payload;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.init.ICleanableWorkstation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 清空工作站材料请求包（客户端 → 服务端）。
 * toStorage=true 时材料优先放入网络存储，false 时优先返回背包；
 * 由 handle 调用当前打开工作站的 cleanSlots 执行清空。
 * TYPE: beyond_integration:clean_workstation；STREAM_CODEC 仅读写布尔标志。
 */
// 清空工作站材料：toStorage=true→网络优先，false→背包优先
public record CleanWorkstationPayload(boolean toStorage) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CleanWorkstationPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.parse(BeyondIntegration.MODID + ":clean_workstation"));
    public static final StreamCodec<FriendlyByteBuf, CleanWorkstationPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> b.writeBoolean(p.toStorage()),
            b -> new CleanWorkstationPayload(b.readBoolean()));

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CleanWorkstationPayload p, IPayloadContext ctx) {
        // 服务端处理：确认玩家打开的是可清理工作站后执行清空
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp && sp.containerMenu instanceof ICleanableWorkstation ws)
                ws.cleanSlots(p.toStorage());
        });
    }
}
