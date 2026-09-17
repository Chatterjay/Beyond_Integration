package com.solr98.beyondintegration.handler;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 悬停快速存入网络（服务端）。
 *
 * <p>参考原版收纳袋（BundleItem#overrideStackedOnOther / #overrideOtherStackedOnMe）的实现方式：
 * 由原版槽位点击协议（{@code ServerboundContainerClickPacket} → {@code AbstractContainerMenu.doClick}）
 * 触发，客户端只需正常点击；网络凭证（NetId）从服务端权威物品读取，客户端无法指定任意网络，
 * 且点击包本身经过菜单一致性/stateId 校验。</p>
 *
 * <p>两个方向（均为左键点击）：
 * <ul>
 *   <li>正向（movableSlot == null）：槽位中的网络物品作凭证，把光标物品存入其网络，余量留光标；</li>
 *   <li>反向（movableSlot != null）：光标的网络物品作凭证，把槽位物品存入其网络。
 *       取物走 {@link Slot#safeTake}（校验 mayPickup 并触发 onTake，等价玩家手动取物），
 *       未存入的余量归还玩家背包（不放回原槽位，避免结果槽 onTake 重入导致的刷取）。</li>
 * </ul>
 * 触发条件由 {@code NetedItemClickMixin} 判定。</p>
 */
public final class HoverInsertHandler {

    private HoverInsertHandler() {}

    /**
     * 尝试把 movable 物品存入 credential 物品绑定的网络。
     *
     * @param player      服务端玩家
     * @param credential  网络凭证物品（读 NetId 决定目标网络），不会被消耗
     * @param movable     将被存入的物品
     * @param movableSlot 非 null 表示 movable 位于该槽位（反向操作）；null 表示 movable 为光标物品
     * @return true 表示已处理（含失败/无权限提示），调用方应返回 true 以跳过原版交换
     */
    public static boolean tryInsert(Player player, ItemStack credential, ItemStack movable,
                                    @Nullable Slot movableSlot) {
        int netId = NetedItem.getNetId(credential);
        if (netId < 0) return false;

        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) return false;

        // 仅网络成员/管理员/所有者可向网络存入物品（目标网络来自服务端权威物品，防越权）
        if (!net.isOwner(player) && !net.isManager(player)
                && !net.getPlayers().contains(player.getUUID())) {
            player.displayClientMessage(Component.translatable(
                    "message.beyond_integration.hover_insert.no_permission", netId), true);
            return true;
        }

        // 反向：按原版取物语义从槽位取出（mayPickup 校验 + onTake：结果槽合成消耗/经验等）
        ItemStack source = movableSlot != null
                ? movableSlot.safeTake(movable.getCount(), movable.getCount(), player)
                : movable;
        if (source.isEmpty()) return false;

        Component name = source.getHoverName();
        int before = source.getCount();
        // insert 返回未接收的余量；黑名单/附魔分离等 BD 插入拦截链在 UnifiedStorage 内部生效
        long remaining = net.getUnifiedStorage().insert(new ItemStackKey(source.copy()), before, false).amount();
        long left = Math.max(0L, Math.min(remaining, before));
        int inserted = before - (int) left;

        if (inserted <= 0) {
            // 反向取出的物品归还玩家，不留在原槽位（防止结果槽 onTake 重入刷取）
            if (movableSlot != null) returnToPlayer(player, source);
            player.displayClientMessage(Component.translatable(
                    "message.beyond_integration.hover_insert.failed", netId), true);
            return true;
        }

        if (movableSlot != null) {
            if (left > 0) {
                ItemStack rest = source.copy();
                rest.setCount((int) left);
                returnToPlayer(player, rest);
            }
        } else {
            source.shrink(inserted);
            player.containerMenu.setCarried(source.isEmpty() ? ItemStack.EMPTY : source);
        }
        player.containerMenu.broadcastChanges();
        player.displayClientMessage(Component.translatable(
                "message.beyond_integration.hover_insert.success", netId, name, inserted), true);
        return true;
    }

    /** 把物品归还玩家：优先放入背包，背包满时掉落到脚下 */
    private static void returnToPlayer(Player player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }
}
