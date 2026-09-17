package com.solr98.beyondintegration.network.payload;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.init.DimensionsAnvilMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 铁砧命名请求包（客户端 → 服务端）。
 * 携带容器 ID 与目标名称（最长 50 字符），handle 校验菜单后调用 rename 应用改名。
 * TYPE: beyond_integration:set_anvil_name；STREAM_CODEC 读写 containerId 与 name。
 */
public record SetAnvilNamePayload(int containerId, String name) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SetAnvilNamePayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.parse(BeyondIntegration.MODID + ":set_anvil_name"));
    public static final StreamCodec<FriendlyByteBuf, SetAnvilNamePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public SetAnvilNamePayload decode(FriendlyByteBuf b) { return new SetAnvilNamePayload(b.readVarInt(), b.readUtf(50)); }
        @Override public void encode(FriendlyByteBuf b, SetAnvilNamePayload p) { b.writeVarInt(p.containerId()); b.writeUtf(p.name(), 50); }
    };

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetAnvilNamePayload p, IPayloadContext ctx) {
        // 服务端处理：确认玩家打开的对应铁砧菜单后执行改名
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp && sp.containerMenu instanceof DimensionsAnvilMenu menu && menu.containerId == p.containerId())
                menu.rename(p.name());
        });
    }
}

