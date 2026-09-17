package com.solr98.beyondintegration.network.payload;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.command.util.NetworkPermission;
import com.solr98.beyondintegration.command.util.NetworkUtils;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * JEI 点击取物品请求包（C2S）：客户端在 JEI 物品条目上 Shift+点击时发送，
 * 服务端校验网络成员权限与库存后，从网络提取指定数量到玩家背包（溢出部分掉落）。
 */
public record ExtractNetworkItemPacket(ItemStack stack, int amount) implements CustomPacketPayload {
    public static final Type<ExtractNetworkItemPacket> TYPE = new Type<>(
            ResourceLocation.parse(BeyondIntegration.MODID + ":extract_network_item"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExtractNetworkItemPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.STREAM_CODEC, ExtractNetworkItemPacket::stack,
                    ByteBufCodecs.VAR_INT, ExtractNetworkItemPacket::amount,
                    ExtractNetworkItemPacket::new);

    /** 服务端执行：权限校验 → 从网络提取 → 入背包（溢出掉落） */
    public static void handle(final ExtractNetworkItemPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ItemStack stack = packet.stack();
            if (stack.isEmpty() || packet.amount() <= 0) return;
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) return;
            // 权限：需为该网络成员（owner/manager/member）
            if (NetworkUtils.getPlayerPermissionLevel(player, net) == NetworkPermission.NONE) return;

            ItemStack template = stack.copyWithCount(1);
            ItemStackKey key = new ItemStackKey(template);
            // 数量上限保护：不超过一组（按物品最大堆叠）
            int want = Math.min(packet.amount(), Math.max(1, template.getMaxStackSize()));
            KeyAmount extracted = net.getUnifiedStorage().extract(key, want, false, false);
            long remaining = extracted.amount();
            if (remaining <= 0) return;

            // 放入背包，放不下的掉落（add 会修改传入 stack 保留剩余）
            while (remaining > 0) {
                int give = (int) Math.min(remaining, template.getMaxStackSize());
                ItemStack giveStack = template.copyWithCount(give);
                player.getInventory().add(giveStack);
                if (!giveStack.isEmpty()) {
                    player.drop(giveStack, false);
                }
                remaining -= give;
            }
            net.setDirty();
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
