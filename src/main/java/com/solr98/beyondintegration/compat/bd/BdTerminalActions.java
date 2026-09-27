package com.solr98.beyondintegration.compat.bd;

import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * BD 终端四类批量操作的服务端实现（行为移植自 EmiLink 的 {@code BDProxy}）。
 *
 * <p>只包含 BD 终端自身的操作：</p>
 * <ol>
 *   <li><b>从网络提取物品</b> — {@link #extractOneStack} / {@link #extractAllMatching}</li>
 *   <li><b>终端与玩家背包之间的批量转移</b> — {@link #depositMatching}</li>
 *   <li><b>合成结果的批量合成</b> — {@link #batchCraft}</li>
 *   <li><b>合成网格清理并返还网络</b> — {@link #cleanCraftGridToNetwork}</li>
 * </ol>
 *
 * <p><b>刻意不包含</b> "从物品管理器（EMI）点击取物" 路径：那属于物品管理器侧的功能，
 * 本模组由 JEI 集成 {@code ExtractNetworkItemPacket} 承担，不在此重复实现。</p>
 *
 * <p>所有方法都做了失败兜底：背包放不下的部分一律退回网络 / 结果槽，绝不凭空销毁物品。</p>
 */
public final class BdTerminalActions {

    /** Space+点击合成结果槽时的单次批量合成上限（对齐 EmiLink 的 massCraft 上限）。 */
    public static final int MAX_BATCH_CRAFT = 512;
    /** 批量提取的循环轮数保护，避免网络与背包状态异常时死循环。 */
    private static final int MAX_EXTRACT_ROUNDS = 4096;
    /** 玩家主背包/快捷栏的槽位数（不含盔甲与副手）。 */
    private static final int PLAYER_MAIN_SLOTS = 36;
    /** 快捷栏槽位数。 */
    private static final int HOTBAR_SLOTS = 9;

    /** 转移模式：主背包（9..35）。 */
    public static final int MODE_MAIN = 1;
    /** 转移模式：快捷栏（0..8）。 */
    public static final int MODE_HOTBAR = 2;
    /** 转移模式：整个背包（0..35）。 */
    public static final int MODE_ALL = 0;

    private BdTerminalActions() {}

    // ────────────────────────── 1) 从网络提取物品 ──────────────────────────

    /**
     * 从当前打开的网络终端提取"一组"目标物品到玩家背包（对齐 EmiLink：BD 网络槽 Shift+点击）。
     * 背包放不下的部分退回网络。
     *
     * @return 实际进入背包的数量
     */
    public static long extractOneStack(Player player, ItemStack target) {
        if (player == null || target == null || target.isEmpty()) return 0;
        AbstractUnorderedStackHandler storage = storageOf(player.containerMenu);
        if (storage == null) return 0;

        long moved = extractOneStackInternal(player, target, storage);
        if (moved > 0) syncAfterInventoryChange(player);
        return moved;
    }

    /**
     * 从网络反复提取与目标相同的物品，直到网络没有更多或背包再也放不下
     * （对齐 EmiLink：BD 网络槽 Space+点击 = 提取同类全部）。
     *
     * @return 实际进入背包的总数量
     */
    public static long extractAllMatching(Player player, ItemStack target) {
        if (player == null || target == null || target.isEmpty()) return 0;
        AbstractUnorderedStackHandler storage = storageOf(player.containerMenu);
        if (storage == null) return 0;

        long total = 0;
        for (int round = 0; round < MAX_EXTRACT_ROUNDS; round++) {
            if (!hasRoomFor(player, target)) break;
            long moved = extractOneStackInternal(player, target, storage);
            if (moved <= 0) break;
            total += moved;
        }
        if (total > 0) syncAfterInventoryChange(player);
        return total;
    }

    /** 提取一组到背包；背包不足的部分退回网络。不含物品栏同步。 */
    private static long extractOneStackInternal(Player player, ItemStack target, AbstractUnorderedStackHandler storage) {
        ItemStackKey key = new ItemStackKey(target);
        long batch = Math.max(1L, key.getVanillaMaxStackSize());
        // 第 4 个参数 fuzzy=true：按 isSame 命中网络里的实际条目，兼容组件数据（附魔/NBT）差异
        KeyAmount extracted = storage.extract(key, batch, false, true);
        if (extracted.isEmpty() || extracted.amount() <= 0) return 0;

        ItemStack taken = extracted.key() instanceof ItemStackKey takenKey
                ? takenKey.copyStackWithCount(extracted.amount())
                : ItemStack.EMPTY;
        if (taken.isEmpty()) return 0;

        long moved = insertIntoPlayerInventory(player, taken);
        long leftover = extracted.amount() - moved;
        if (leftover > 0) {
            // 背包放不下：把剩余部分原样退回网络，避免物品凭空消失
            storage.insert(key, leftover, false);
        }
        return moved;
    }

    // ────────────────── 2) 终端与玩家背包之间的批量转移 ──────────────────

    /**
     * 把玩家背包中与目标相同的物品批量存入网络（对齐 EmiLink：BD 终端 Space+点击）。
     *
     * @param mode {@link #MODE_MAIN} 主背包(9..35)，{@link #MODE_HOTBAR} 快捷栏(0..8)，
     *             {@link #MODE_ALL} 全部(0..35)
     * @return 实际存入网络的数量
     */
    public static long depositMatching(Player player, ItemStack target, int mode) {
        if (player == null || target == null || target.isEmpty()) return 0;
        AbstractUnorderedStackHandler storage = storageOf(player.containerMenu);
        if (storage == null) return 0;

        Inventory inventory = player.getInventory();
        int start = switch (mode) {
            case MODE_HOTBAR -> 0;
            case MODE_MAIN -> HOTBAR_SLOTS;
            default -> 0;
        };
        int end = switch (mode) {
            case MODE_HOTBAR -> HOTBAR_SLOTS;
            default -> PLAYER_MAIN_SLOTS;
        };

        long deposited = 0;
        for (int i = start; i < end; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, target)) continue;

            // insert 返回的是"没能放进去的剩余量"
            long left = storage.insert(new ItemStackKey(stack), stack.getCount(), false).amount();
            int moved = stack.getCount() - (int) Math.min(Integer.MAX_VALUE, Math.max(0L, left));
            if (moved <= 0) continue;

            deposited += moved;
            stack.setCount((int) Math.min(Integer.MAX_VALUE, Math.max(0L, left)));
            inventory.setItem(i, stack.isEmpty() ? ItemStack.EMPTY : stack);
        }

        if (deposited > 0) syncAfterInventoryChange(player);
        return deposited;
    }

    // ──────────────────── 3) 合成结果的批量合成 ────────────────────

    /**
     * 在合成结果槽上连续合成（对齐 EmiLink：BD 合成界面 Space+点击结果槽）。
     *
     * <p>BD 的即时补料机制会在每次取走结果时从网络补齐原料，因此不需要客户端往返；
     * 循环会在结果槽为空、原料耗尽（结果槽不再刷新）或背包放不下时提前结束。</p>
     *
     * @param limit 本次最多合成次数，内部按 {@link #MAX_BATCH_CRAFT} 截断
     * @return 实际合成次数
     */
    public static int batchCraft(Player player, int limit) {
        if (player == null || limit <= 0) return 0;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return 0;
        int resultIndex = findResultSlot(menu);
        if (resultIndex < 0) return 0;

        int cap = Math.min(limit, MAX_BATCH_CRAFT);
        int crafted = 0;
        for (int i = 0; i < cap; i++) {
            Slot result = menu.slots.get(resultIndex);
            if (!result.hasItem()) break;
            if (!menu.getCarried().isEmpty()) break; // 光标不干净时不动手，避免误吞光标物品

            // PICKUP 走原版取物路径：ResultSlot.onTake 会触发 BD 的即时补料（从网络补原料），
            // 因此每次取走后结果槽会在同一 tick 内被重新填满。
            menu.clicked(resultIndex, 0, ClickType.PICKUP, player);

            ItemStack carried = menu.getCarried();
            if (carried.isEmpty()) break;

            insertIntoPlayerInventory(player, carried);
            if (!carried.isEmpty()) {
                // 背包已满：把光标物品放回结果槽并结束，绝不丢弃
                menu.setCarried(ItemStack.EMPTY);
                result.set(carried);
                break;
            }
            menu.setCarried(ItemStack.EMPTY);
            crafted++;

            // 补料失败（网络/背包里的原料已耗尽）时结果槽会保持为空，这就是收敛条件；
            // 注意不能用"结果槽内容是否变化"判断——补料成功时内容本就与取走前相同。
            if (!menu.slots.get(resultIndex).hasItem()) break;
        }

        if (crafted > 0) {
            player.getInventory().setChanged();
            menu.broadcastChanges();
            broadcastPlayerInventory(player);
        }
        return crafted;
    }

    // ──────────────── 4) 合成网格清理并返还网络 ────────────────

    /**
     * 清空当前打开的合成网格，并把网格内容优先返还网络（网络放不下的再回背包、最后掉落）。
     * 同时支持本模组的合成工作站与 BD 原生合成终端。
     *
     * @return 当前界面是否为可清理的合成界面
     */
    public static boolean cleanCraftGridToNetwork(Player player) {
        if (player == null) return false;
        AbstractContainerMenu menu = player.containerMenu;
        // 本模组的工作站合成菜单
        if (menu instanceof com.solr98.beyondintegration.init.DimensionsCraftMenu biCraft) {
            biCraft.cleanCraftSlots(true);
            return true;
        }
        // BD 原生合成终端（EmiLink 的 DimensionsCraftMenuMixin 在此强制 toStorageFirst=true）
        if (menu instanceof com.wintercogs.beyonddimensions.common.menu.DimensionsCraftMenu bdCraft) {
            bdCraft.cleanCraftSlots(true);
            return true;
        }
        return false;
    }

    // ────────────────────────── 内部工具 ──────────────────────────

    /** 取当前菜单绑定的网络存储；非 BD 网络菜单返回 null。 */
    private static AbstractUnorderedStackHandler storageOf(AbstractContainerMenu menu) {
        return menu instanceof DimensionsNetMenu netMenu ? netMenu.storage : null;
    }

    /** 找到合成结果槽在 slots 中的下标（本模组与 BD 的结果槽都继承 ResultSlot）。 */
    private static int findResultSlot(AbstractContainerMenu menu) {
        for (int i = 0; i < menu.slots.size(); i++) {
            if (menu.slots.get(i) instanceof ResultSlot) return i;
        }
        return -1;
    }

    /**
     * 把物品放进玩家主背包/快捷栏：先叠加到已有同类堆，再放入空槽。
     * 传入的 stack 会被就地削减为"没能放进去的剩余量"。
     *
     * @return 实际放入的数量
     */
    private static long insertIntoPlayerInventory(Player player, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        Inventory inventory = player.getInventory();
        int initial = stack.getCount();

        // 1) 先填满已有的同类堆
        for (int i = 0; i < PLAYER_MAIN_SLOTS && !stack.isEmpty(); i++) {
            ItemStack slotStack = inventory.getItem(i);
            if (slotStack.isEmpty() || !ItemStack.isSameItemSameComponents(slotStack, stack)) continue;
            int space = Math.min(slotStack.getMaxStackSize(), stack.getMaxStackSize()) - slotStack.getCount();
            if (space <= 0) continue;
            int add = Math.min(stack.getCount(), space);
            slotStack.grow(add);
            stack.shrink(add);
            inventory.setItem(i, slotStack);
        }

        // 2) 再放入空槽
        for (int i = 0; i < PLAYER_MAIN_SLOTS && !stack.isEmpty(); i++) {
            if (!inventory.getItem(i).isEmpty()) continue;
            int add = Math.min(stack.getCount(), stack.getMaxStackSize());
            inventory.setItem(i, stack.copyWithCount(add));
            stack.shrink(add);
        }

        return initial - stack.getCount();
    }

    /** 判断背包里是否还放得下该物品（用于批量提取的提前收敛）。 */
    private static boolean hasRoomFor(Player player, ItemStack template) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < PLAYER_MAIN_SLOTS; i++) {
            ItemStack slotStack = inventory.getItem(i);
            if (slotStack.isEmpty()) return true;
            if (!ItemStack.isSameItemSameComponents(slotStack, template)) continue;
            if (slotStack.getCount() < Math.min(slotStack.getMaxStackSize(), template.getMaxStackSize())) return true;
        }
        return false;
    }

    /** 背包变化后的统一同步：标记脏位 + 菜单广播 + 玩家物品栏全量同步。 */
    private static void syncAfterInventoryChange(Player player) {
        player.getInventory().setChanged();
        if (player.containerMenu != null) {
            player.containerMenu.broadcastChanges();
        }
        broadcastPlayerInventory(player);
    }

    /** 全量同步玩家物品栏：差量同步在快速连续操作时可能漏发个别槽位。 */
    private static void broadcastPlayerInventory(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.inventoryMenu.broadcastFullState();
        }
    }
}
