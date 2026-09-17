package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.command.util.NetworkUtils;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * JEI 点击取物品请求包（C2S）：客户端在 JEI 物品条目上 Shift+点击时发送，
 * 服务端校验网络成员权限与库存后，从网络提取指定数量到玩家背包（溢出部分掉落）。
 */
public class ExtractNetworkItemPacket {

    /** 目标物品（仅取 1 个作为模板，含 NBT） */
    private final ItemStack stack;
    /** 请求提取数量 */
    private final int amount;

    public ExtractNetworkItemPacket(ItemStack stack, int amount) {
        this.stack = stack == null ? ItemStack.EMPTY : stack.copyWithCount(1);
        this.amount = amount;
    }

    /** 写入物品与数量 */
    public static void encode(ExtractNetworkItemPacket msg, FriendlyByteBuf buf) {
        buf.writeItem(msg.stack);
        buf.writeVarInt(msg.amount);
    }

    /** 读取物品与数量 */
    public static ExtractNetworkItemPacket decode(FriendlyByteBuf buf) {
        return new ExtractNetworkItemPacket(buf.readItem(), buf.readVarInt());
    }

    /** 服务端执行：权限校验 → 从网络提取 → 入背包（溢出掉落） */
    public static void handle(ExtractNetworkItemPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || msg.stack.isEmpty() || msg.amount <= 0) return;
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) return;
            // 权限：需为该网络成员（owner/manager/member）
            if ("none".equals(NetworkUtils.getPlayerPermissionLevel(player, net))) return;

            ItemStack template = msg.stack.copyWithCount(1);
            ItemStackKey key = new ItemStackKey(template);
            // 数量上限保护：不超过一组（按物品最大堆叠）
            int want = Math.min(msg.amount, Math.max(1, template.getMaxStackSize()));
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
        ctx.get().setPacketHandled(true);
    }
}
