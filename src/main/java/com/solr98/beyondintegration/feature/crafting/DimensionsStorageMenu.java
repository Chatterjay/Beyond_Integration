package com.solr98.beyondintegration.feature.crafting;

import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.UnorderedStackHandlerRemoveZero;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.jetbrains.annotations.NotNull;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import static com.wintercogs.beyonddimensions.common.init.BDMenus.Dimensions_Craft_Menu;

/**
 * 网络工作站菜单公共基类：继承 BD 网络存储菜单（DimensionsNetMenu）。
 * 统一管理背包/存储槽布局与 rebuildSlots、关闭/清空时按方向归还（网络/背包优先）、
 * 自实现 moveStackTo 供 shift 快速移动、结果槽 shift 拦截开关与槽位坐标反射重排。
 */
public class DimensionsStorageMenu extends DimensionsNetMenu {
    // 关闭/清空时的优先归还方向：true=网络优先，false=背包优先
    public boolean firstCraftReturnDir = false;

    public void setReturnDir(boolean toStorage) {
        firstCraftReturnDir = toStorage;
        // 由 writeAndSendQuickData 同步到服务端 readQuickDataTag
    }

    @Override protected void writeQuickDataTag(net.minecraft.nbt.CompoundTag tag) {
        super.writeQuickDataTag(tag);
        tag.putBoolean("firstCraftReturnDir", firstCraftReturnDir);
    }

    @Override public void readQuickDataTag(net.minecraft.nbt.CompoundTag tag) {
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

    // 通用清空：按方向归还指定槽位（delegate 容器）物品
    protected void cleanSlotsFrom(boolean toStorage, net.minecraft.world.inventory.AbstractContainerMenu container, int[] idxs) {
        if (player.level().isClientSide()) return;
        // 归还目标为当前打开的网络存储（对齐 BD cleanCraftSlots：用菜单 storage，而非玩家主网络）
        var storage = this.storage;
        for (int idx : idxs) {
            ItemStack s = container.getSlot(idx).getItem();
            if (s.isEmpty()) continue;
            container.getSlot(idx).set(ItemStack.EMPTY);
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

    // 反射获取的 Slot.x / Slot.y 字段（obfuscated 名称，坐标重排用）
    protected static final Field SLOT_X = ObfuscationReflectionHelper.findField(Slot.class, "f_40220_");
    protected static final Field SLOT_Y = ObfuscationReflectionHelper.findField(Slot.class, "f_40221_");
    // 通过反射设置槽位 X/Y 坐标（失败静默忽略）
    public static void setSlotX(Slot s, int x) { try { SLOT_X.setInt(s, x); } catch (Exception ignored) {} }
    public static void setSlotY(Slot s, int y) { try { SLOT_Y.setInt(s, y); } catch (Exception ignored) {} }

    public DimensionsStorageMenu(int id, Inventory playerInventory, FriendlyByteBuf data) {
        this(Dimensions_Craft_Menu.get(), id, playerInventory, new UnorderedStackHandlerRemoveZero(AbstractUnorderedStackHandler.UiTimestampPolicy.NONE));
    }
    public DimensionsStorageMenu(MenuType<?> type, int id, Inventory playerInventory, AbstractUnorderedStackHandler data) {
        super(type, id, playerInventory, data);
    }

    // 自定义槽（工作台格位）索引表：rebuildSlots 据此重排坐标
    public final List<Integer> customSlotIndices = new ArrayList<>();
    // 面板高度：决定存储槽行数与背包偏移（子类重写）
    public int getPanelHeight() { return 62; }
    // 工作台槽位 Y 坐标：面板顶部偏移 + 存储行数自适应
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

    @Override protected void addPlayerInv(Inventory inv) {
        int ph = getPanelHeight();
        inventoryStartIndex = slots.size();
        for (int r = 0; r < 3; ++r) for (int c = 0; c < 9; ++c)
            addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 25 + ph + (getLines() - 1) * 18 + 26 + 6 + 8 + r * 18));
        for (int c = 0; c < 9; ++c)
            addSlot(new Slot(inv, c, 8 + c * 18, 25 + ph + (getLines() - 1) * 18 + 26 + 6 + 8 + 3 * 18 + 4));
        inventoryEndIndex = slots.size();
    }

    // 存储行数变化后重排：切换存储槽可见行并重新定位背包槽 Y 坐标
    @Override public void rebuildSlots() {
        int ph = getPanelHeight();
        int n = 0;
        for (Slot s : slots) {
            if (s instanceof com.wintercogs.beyonddimensions.common.menu.widget.slot.AbstractStackTypedSlot ss) { ss.setActive(n / 9 < getLines()); n++; }
        }
        int i = inventoryStartIndex; n = 0;
        while (i < inventoryEndIndex) {
            Slot s = slots.get(i);
            setSlotY(s, 25 + ph + (getLines() - 1) * 18 + 26 + 6 + 8 + (n / 9 < 3 ? n / 9 * 18 : 3 * 18 + 4));
            i++; n++;
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
                if (!itemstack.isEmpty() && ItemStack.isSameItemSameTags(stack, itemstack)) {
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

    @Override public boolean stillValid(@NotNull Player p) { return true; }
}
