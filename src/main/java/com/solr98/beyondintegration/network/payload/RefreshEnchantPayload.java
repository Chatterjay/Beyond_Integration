package com.solr98.beyondintegration.network.payload;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.init.DimensionsEnchantMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 附魔台刷新请求包（客户端 → 服务端）。
 * 服务端消耗刷新代价（enchantRefreshLapis，免燃料/创造免费）后局部重排附魔种子并重算三槽。
 * TYPE: beyond_integration:refresh_enchant。
 */
public record RefreshEnchantPayload(int containerId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RefreshEnchantPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.parse(BeyondIntegration.MODID + ":refresh_enchant"));
    public static final StreamCodec<FriendlyByteBuf, RefreshEnchantPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public RefreshEnchantPayload decode(FriendlyByteBuf b) { return new RefreshEnchantPayload(b.readInt()); }
        @Override public void encode(FriendlyByteBuf b, RefreshEnchantPayload p) { b.writeInt(p.containerId()); }
    };

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RefreshEnchantPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp && sp.containerMenu instanceof DimensionsEnchantMenu menu
                    && menu.containerId == p.containerId()) {
                menu.doRefresh(sp);
            }
        });
    }
}