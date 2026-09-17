package com.solr98.beyondintegration.init;

import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.UnorderedStackHandlerRemoveZero;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/**
 * 各工作站菜单的公共基类：继承 BD 网络存储菜单（自带网络存储槽与即时补料），
 * 负责面板高度/存储行数布局、关闭清空时物品归还（cleanSlotsFromContainer）、
 * 自实现原版 moveStackTo 供快速移动使用，并可拦截结果槽的 shift 点击。
 */
public class DimensionsStorageMenu extends DimensionsNetMenu {
    // 关闭/清空时的优先归还方向：true=网络优先，false=背包优先
    public boolean firstCraftReturnDir = false;

    // 设置关闭/清空时的归还方向，并交由 writeAndSendQuickData 同步到服务端 readQuickDataTag
    public void setReturnDir(boolean toStorage) {
        firstCraftReturnDir = toStorage;
        // 由 writeAndSendQuickData 同步到服务端 readQuickDataTag
    }

    // 快速数据同步（写方向）：将归还方向写入 NBT 随菜单数据包发送
    @Override
    protected void writeQuickDataTag(net.minecraft.nbt.CompoundTag tag) {
        super.writeQuickDataTag(tag);
        tag.putBoolean("firstCraftReturnDir", firstCraftReturnDir);
    }

    // 快速数据同步（读方向）：从数据包恢复归还方向
    @Override
    public void readQuickDataTag(net.minecraft.nbt.CompoundTag tag) {
        super.readQuickDataTag(tag);
        if (tag.contains("firstCraftReturnDir"))
            firstCraftReturnDir = tag.getBoolean("firstCraftReturnDir");
    }

    // 通用清空：按方向归还自持容器（copy 原版实现）的物品
    protected void cleanSlotsFromContainer(boolean toStorage, net.minecraft.world.Container container, int[] idxs) {
        if (player.level().isClientSide()) return;
        // 归还目标为当前打开的网络存储（对齐 BD cleanCraftSlots：用菜单 storage，而非玩家主网络）
        var storage = this.storage;
        for (int idx : idxs) {
            ItemStack s = container.getItem(idx);
            if (s.isEmpty()) continue;
            container.setItem(idx, ItemStack.EMPTY);
            if (toStorage) {
                if (storage != null) { long left = storage.insert(new com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey(s), s.getCount(), false).amount(); s.setCount((int) left); }
                // 对齐 BD cleanCraftSlots 状态同步：玩家存活且未断线才转移背包，否则跳过（走掉落兜底，不吞货）
                if (!s.isEmpty() && player.isAlive() && !((net.minecraft.server.level.ServerPlayer) player).hasDisconnected()) player.getInventory().add(s);
                if (!s.isEmpty()) player.drop(s, false);
            } else {
                // 对齐 BD cleanCraftSlots 状态同步：玩家存活且未断线才转移背包，否则跳过（走网络/掉落兜底）
                if (player.isAlive() && !((net.minecraft.server.level.ServerPlayer) player).hasDisconnected()) player.getInventory().add(s);
                if (!s.isEmpty() && storage != null) { long left = storage.insert(new com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey(s), s.getCount(), false).amount(); s.setCount((int) left); }
                if (!s.isEmpty()) player.drop(s, false);
            }
        }
        // 归还后立即全量同步玩家背包：关闭界面瞬间差分同步可能漏发个别槽，导致客户端物品栏不显示
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.inventoryMenu.broadcastFullState();
        }
    }

    // 通过 SlotAccessor mixin 直接修改槽位坐标（rebuildSlots 动态布局用）
    public static void setSlotX(Slot s, int x) { ((com.solr98.beyondintegration.mixin.SlotAccessor) s).setX(x); }
    public static void setSlotY(Slot s, int y) { ((com.solr98.beyondintegration.mixin.SlotAccessor) s).setY(y); }

    // 客户端构造：由网络包创建（无本地存储数据）
    public DimensionsStorageMenu(int id, Inventory playerInventory, FriendlyByteBuf data) {
        this(ModMenus.STORAGE.get(), id, playerInventory,
                new UnorderedStackHandlerRemoveZero(AbstractUnorderedStackHandler.UiTimestampPolicy.NONE));
    }

    // 主构造：传入存储处理器数据（服务端/客户端共用）
    public DimensionsStorageMenu(MenuType<?> type, int id, Inventory playerInventory, AbstractUnorderedStackHandler data) {
        super(type, id, playerInventory, data);
    }

    // 自定义槽位索引记录（工作台/盔甲等非网络槽），rebuildSlots 据此重排坐标
    public final List<Integer> customSlotIndices = new ArrayList<>();
    // 面板高度（像素），子类可覆写调整界面布局
    public int getPanelHeight() { return 62; }
    // 依据存储行数计算槽位 Y 坐标（基础 Y + 偏移）
    public int ey(int baseY) { return 68 + (getLines() - 2) * 18 + baseY + 1; }

    // 恢复原版 shift+点击：原版 clicked(QUICK_MOVE) → quickMoveStack + while 循环已驱动连续合成；
    // 默认拦截 BD 的 CallSeverClickPacket → customClickHandler → quickMoveHandle（避免双通道重复处理），
    // 结果槽 shift 请求直接忽略；Craft 菜单已对齐 BD 即时补料，关闭拦截改走 BD 批量合成通道（带回滚保护）
    protected boolean blockResultSlotShiftClick = true;
    @Override
    public void customClickHandler(int slotIndex, com.wintercogs.beyonddimensions.api.storage.key.KeyAmount clickedStack,
                                   int button, boolean shiftDown) {
        if (blockResultSlotShiftClick && shiftDown && slotIndex >= 0 && slotIndex < this.slots.size()
                && this.slots.get(slotIndex) instanceof net.minecraft.world.inventory.ResultSlot) {
            return;
        }
        super.customClickHandler(slotIndex, clickedStack, button, shiftDown);
    }

    // 添加玩家背包槽位：根据面板高度与存储行数动态计算位置，并记录背包槽索引范围
    @Override
    protected void addPlayerInv(Inventory inv) {
        int ph = getPanelHeight();
        inventoryStartIndex = slots.size();
        for (int r = 0; r < 3; ++r)
            for (int c = 0; c < 9; ++c)
                addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 25 + ph + (getLines() - 1) * 18 + 26 + 6 + 8 + r * 18));
        for (int c = 0; c < 9; ++c)
            addSlot(new Slot(inv, c, 8 + c * 18, 25 + ph + (getLines() - 1) * 18 + 26 + 6 + 8 + 3 * 18 + 4));
        inventoryEndIndex = slots.size();
    }

    // 重建槽位布局：激活/隐藏存储槽行（按行数），并重排背包槽 Y 坐标
    @Override
    public void rebuildSlots() {
        int ph = getPanelHeight();
        int n = 0;
        for (Slot s : slots) {
            // n 仅对网络槽计数（背包/工作站槽不参与行计算）
            if (s instanceof com.wintercogs.beyonddimensions.common.menu.widget.slot.AbstractStackTypedSlot ss) {
                ss.setActive(n / 9 < getLines());
                n++;
            }
        }
        int i = inventoryStartIndex;
        n = 0;
        while (i < inventoryEndIndex) {
            Slot s = slots.get(i);
            setSlotY(s, 25 + ph + (getLines() - 1) * 18 + 26 + 6 + 8 + (n / 9 < 3 ? n / 9 * 18 : 3 * 18 + 4));
            i++;
            n++;
        }
    }

    // BDBaseMenu 将 moveItemStackTo 禁用（恒 false），此处自实现原版逻辑供快速移动使用
    protected boolean moveStackTo(ItemStack stack, int startIndex, int endIndex, boolean reverseDirection) {
        boolean flag = false;
        int i = reverseDirection ? endIndex - 1 : startIndex;
        if (stack.isStackable()) {
            while (!stack.isEmpty() && (reverseDirection ? i >= startIndex : i < endIndex)) {
                Slot slot = this.slots.get(i);
                ItemStack itemstack = slot.getItem();
                if (!itemstack.isEmpty() && ItemStack.isSameItemSameComponents(stack, itemstack)) {
                    int total = itemstack.getCount() + stack.getCount();
                    int max = Math.min(stack.getMaxStackSize(), slot.getMaxStackSize(stack));
                    if (total <= max) {
                        stack.setCount(0);
                        itemstack.setCount(total);
                        slot.setChanged();
                        flag = true;
                    } else if (itemstack.getCount() < max) {
                        stack.shrink(max - itemstack.getCount());
                        itemstack.setCount(max);
                        slot.setChanged();
                        flag = true;
                    }
                }
                i = reverseDirection ? i - 1 : i + 1;
            }
        }
        if (!stack.isEmpty()) {
            i = reverseDirection ? endIndex - 1 : startIndex;
            while (reverseDirection ? i >= startIndex : i < endIndex) {
                Slot slot1 = this.slots.get(i);
                ItemStack itemstack1 = slot1.getItem();
                if (itemstack1.isEmpty() && slot1.mayPlace(stack)) {
                    int count = Math.min(stack.getCount(), slot1.getMaxStackSize(stack));
                    slot1.set(stack.split(count));
                    slot1.setChanged();
                    flag = true;
                }
                i = reverseDirection ? i - 1 : i + 1;
            }
        }
        return flag;
    }

    // 菜单始终有效（远程访问工作站，无需靠近方块）
    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player p) { return true; }
}

