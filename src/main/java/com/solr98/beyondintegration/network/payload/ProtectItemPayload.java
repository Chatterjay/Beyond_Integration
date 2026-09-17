package com.solr98.beyondintegration.network.payload;

import com.solr98.beyondintegration.BeyondIntegration;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 物品保护(附魔分离)切换请求包（客户端 → 服务端）。
 * 对指定背包槽位（-1 为主手）物品的 CUSTOM_DATA 添加/移除保护标记
 * "beyond_integration:protect_sep"，handle 校验后执行并提示玩家。
 * TYPE: beyond_integration:protect_item；STREAM_CODEC 仅读写槽位索引。
 */
public record ProtectItemPayload(int slotIndex) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ProtectItemPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.parse(BeyondIntegration.MODID + ":protect_item"));
    public static final StreamCodec<FriendlyByteBuf, ProtectItemPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public ProtectItemPayload decode(FriendlyByteBuf b) { return new ProtectItemPayload(b.readInt()); }
        @Override public void encode(FriendlyByteBuf b, ProtectItemPayload p) { b.writeInt(p.slotIndex()); }
    };

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ProtectItemPayload p, IPayloadContext ctx) {
        // 服务端处理：校验槽位与物品后切换保护标记并同步容器
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                int slot = p.slotIndex();
                if (slot >= sp.getInventory().getContainerSize()) { sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.beyond_integration.protect.no_item"), true); return; }
                ItemStack stack = slot >= 0 ? sp.getInventory().getItem(slot) : sp.getMainHandItem();
                if (stack.isEmpty()) { sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.beyond_integration.protect.no_item"), true); return; }
                var customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                boolean isProtected = customData != null && customData.copyTag().getBoolean("beyond_integration:protect_sep");
                if (isProtected) {
                    stack.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY, data -> data.update(t -> t.remove("beyond_integration:protect_sep")));
                } else {
                    stack.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY, data -> data.update(t -> t.putBoolean("beyond_integration:protect_sep", true)));
                }
                sp.inventoryMenu.broadcastChanges();
                sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(isProtected ? "message.beyond_integration.protect.removed" : "message.beyond_integration.protect.added", stack.getDisplayName()), true);
            }
        });
    }
}

