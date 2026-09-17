package com.solr98.beyondintegration.network;
import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 附魔分离开关切换包（客户端 → 服务端，空载荷）。
 * 仅限网络管理员/所有者调用，服务端 handle 翻转附魔分离状态并标记网络脏数据。
 * TYPE: beyond_integration:toggle_enchant_separation；STREAM_CODEC 为无字段的 unit 编解码器。
 */
public record ToggleEnchantSeparationPacket() implements CustomPacketPayload {
    public static final Type<ToggleEnchantSeparationPacket> TYPE = new Type<>(ResourceLocation.parse(BeyondIntegration.MODID + ":toggle_enchant_separation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleEnchantSeparationPacket> STREAM_CODEC = StreamCodec.unit(new ToggleEnchantSeparationPacket());

    public static void handle(final ToggleEnchantSeparationPacket packet, final IPayloadContext context) {
        // 服务端处理：权限校验后翻转附魔分离开关
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            var net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null || !(net instanceof EnchantSeparationAccessor acc)) return;
            if (!net.isManager(player) && !net.isOwner(player)) {
                player.sendSystemMessage(Component.translatable("message.beyond_integration.cannot_toggle_enchant_sep"));
                return;
            }
            acc.beyond$setEnchantSeparationEnabled(!acc.beyond$isEnchantSeparationEnabled());
            net.setDirty();
            // 回发同步包：客户端独立状态（EnchantSeparationState）据此回显，不依赖 SW 快照
            PacketHandler.sendToPlayer(player,
                    new EnchantSeparationSyncPacket(net.getId(), acc.beyond$isEnchantSeparationEnabled()));
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

