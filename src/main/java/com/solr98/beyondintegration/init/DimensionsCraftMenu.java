package com.solr98.beyondintegration.init;

import com.mojang.datafixers.util.Pair;
import com.solr98.beyondintegration.api.ICraftingIntegration;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.UnorderedStackHandlerRemoveZero;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.common.CommonHooks;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * 3×3 工作台合成菜单：继承网络存储基类，合成格/结果槽/盔甲槽/副手槽挂载到网络存储。
 * 结果槽取走时即时补料（网络优先→背包）；shift 批量合成走 BD quickMoveHandle 通道；
 * 支持 JEI 配方一键填充（transferRecipe）与关闭时的物品归还。
 */
public class DimensionsCraftMenu extends DimensionsStorageMenu implements ICleanableWorkstation {
    // 盔甲槽在玩家背包中的索引（头盔→靴子）
    private static final int[] ARMOR_SLOTS = {39, 38, 37, 36};
    // 与 ARMOR_SLOTS 对应的装备槽位（放行判定/空槽图标/换装事件用）
    private static final EquipmentSlot[] ARMOR_EQUIP = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    // 3×3 合成容器（槽位变化时触发配方重算）
    private final TransientCraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    // 合成结果容器
    private final ResultContainer resultSlots = new ResultContainer();
    // 持有者玩家引用
    private final Player player;
    // 工作台自定义槽位（合成格 9 + 结果 1 + 盔甲 4 + 副手 1）的起始索引
    private int wsS = -1;

    // 面板高度（像素）
    @Override public int getPanelHeight() { return 72; }
    // 工作台区域顶部偏移 Y（随存储行数变化）
    private int gyOff() { return 68 + (getLines() - 2) * 18; }

    // 客户端构造：由网络包创建
    public DimensionsCraftMenu(int id, Inventory inv, net.minecraft.network.FriendlyByteBuf b) {
        this(ModMenus.CRAFT.get(), id, inv,
                new UnorderedStackHandlerRemoveZero(AbstractUnorderedStackHandler.UiTimestampPolicy.NONE));
    }

    // 主构造：注册 3×3 合成格、结果槽（即时补料）、盔甲槽与副手槽
    public DimensionsCraftMenu(MenuType<?> t, int id, Inventory inv, AbstractUnorderedStackHandler d) {
        super(t, id, inv, d);
        this.player = inv.player;
        // 已对齐 BD 即时补料：结果槽 shift 走 BD 批量合成通道（quickMoveHandle，带回滚），不屏蔽
        this.blockResultSlotShiftClick = false;
        // BD 基类已添加背包(36) + 存储槽(99行×9)，工作台槽位从 wsS 开始
        this.wsS = slots.size();
        int gapOff = gyOff();

        for (int r = 0; r < 3; ++r)
            for (int c = 0; c < 3; ++c) {
                int idx = c + r * 3;
                addSlot(new Slot(craftSlots, idx, 77 + c * 18, gapOff + 1 + r * 18) {
                    @Override public void setChanged() { super.setChanged(); slotsChanged(craftSlots); }
                });
                customSlotIndices.add(slots.size() - 1);
            }

        addSlot(new ResultSlot(player, craftSlots, resultSlots, 0, 153, gapOff + 20) {
            @Override
            public void onTake(Player p, ItemStack stack) {
                if (p.level().isClientSide()) return;
                // 合成实现 copy 自 BD AutoRefillResultSlot(1.21.1)：取走时即时补料（网络优先 → 背包），
                // 槽位耗尽时先验原料再扣（格内数量不变），返回物槽位 → 网络 → 背包 → 掉落
                this.checkTakeAchievements(stack);
                CraftingInput.Positioned positionedInput = craftSlots.asPositionedCraftInput();
                CraftingInput craftingGrid = positionedInput.input();
                int gridStartX = positionedInput.left();
                int gridStartY = positionedInput.top();

                CommonHooks.setCraftingPlayer(p);
                NonNullList<ItemStack> remainingItems = p.level().getRecipeManager()
                        .getRemainingItemsFor(RecipeType.CRAFTING, craftingGrid, p.level());
                CommonHooks.setCraftingPlayer(null);

                // 此函数每次调用最多完成一次合成
                int craftTimes = 1;
                for (int gridRow = 0; gridRow < craftingGrid.height(); gridRow++) {
                    for (int gridCol = 0; gridCol < craftingGrid.width(); gridCol++) {
                        int slotIndex = gridCol + gridStartX + (gridRow + gridStartY) * craftSlots.getWidth();
                        ItemStack slotStack = craftSlots.getItem(slotIndex);
                        ItemStack recipeRemainder = remainingItems.get(gridCol + gridRow * craftingGrid.width());

                        if (!slotStack.isEmpty()) {
                            int itemsToRemove = craftTimes;
                            // 槽位将耗尽时，优先从网络/背包补足本次消耗（先验原料量再显示产物的机制）
                            if (slotStack.getCount() <= itemsToRemove) {
                                ItemStackKey toRemoveKey = new ItemStackKey(slotStack);
                                int extracted = (int) storage.extract(toRemoveKey, itemsToRemove, false, false).amount();
                                itemsToRemove -= extracted;
                                for (int i = 0; i < p.getInventory().items.size() && itemsToRemove > 0; i++) {
                                    ItemStack invStack = p.getInventory().items.get(i);
                                    if (ItemStack.isSameItemSameComponents(invStack, toRemoveKey.getReadOnlyStack())) {
                                        int shrinkAmount = Math.min(itemsToRemove, invStack.getCount());
                                        invStack.shrink(shrinkAmount);
                                        p.getInventory().items.set(i, invStack.isEmpty() ? ItemStack.EMPTY : invStack);
                                        itemsToRemove -= shrinkAmount;
                                    }
                                }
                            }
                            // 未补足的部分从槽位扣除
                            if (itemsToRemove > 0) {
                                craftSlots.removeItem(slotIndex, itemsToRemove);
                            }
                            slotStack = craftSlots.getItem(slotIndex);
                        }

                        // 返回物（如空桶/熔炉等）：槽位回填 → 网络 → 背包 → 掉落
                        if (!recipeRemainder.isEmpty()) {
                            int remainderCount = craftTimes;
                            ItemStackKey remainderKey = new ItemStackKey(recipeRemainder);
                            if (slotStack.isEmpty() && remainderCount > 0) {
                                craftSlots.setItem(slotIndex, remainderKey.copyStackWithCount(1));
                                remainderCount--;
                            }
                            if (remainderCount > 0) {
                                remainderCount = (int) storage.insert(remainderKey, remainderCount, false).amount();
                            }
                            if (remainderCount > 0) {
                                ItemStack insertStack = remainderKey.copyStackWithCount(remainderCount);
                                p.getInventory().add(insertStack);
                                remainderCount = insertStack.getCount();
                            }
                            if (remainderCount > 0) {
                                p.drop(remainderKey.copyStackWithCount(remainderCount), false);
                            }
                        }
                    }
                }
                slotsChanged(craftSlots);
            }
        });
        customSlotIndices.add(slots.size() - 1);

        Inventory pi = inv;
        for (int i = 0; i < 4; i++) {
            int slotIdx = ARMOR_SLOTS[i];
            EquipmentSlot eq = ARMOR_EQUIP[i];
            int yOff = i;
            addSlot(new Slot(pi, slotIdx, 8, gapOff + 1 + yOff * 18) {
                @Override public boolean mayPlace(ItemStack s) { return s.canEquip(eq, player); }
                @Override public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() { return Pair.of(InventoryMenu.BLOCK_ATLAS, getArmorIcon(eq)); }
                @Override public void setByPlayer(ItemStack newStack) { ItemStack old = getItem().copy(); super.setByPlayer(newStack); player.onEquipItem(eq, old, newStack); }
            });
            customSlotIndices.add(slots.size() - 1);
        }

        addSlot(new Slot(pi, 40, 77, gapOff + 55) {
            @Override public boolean mayPlace(ItemStack s) { return true; }
            @Override public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() { return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD); }
            @Override public void setByPlayer(ItemStack newStack) { ItemStack old = getItem().copy(); super.setByPlayer(newStack); player.onEquipItem(EquipmentSlot.OFFHAND, old, newStack); }
        });
        customSlotIndices.add(slots.size() - 1);
    }

    // 根据装备槽返回对应的空槽占位图标
    private static ResourceLocation getArmorIcon(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
            case CHEST -> InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
            case LEGS -> InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
            case FEET -> InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
            default -> InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
        };
    }

    // 重建布局：按 customSlotIndices 重排合成格/结果槽/盔甲槽/副手槽坐标
    @Override public void rebuildSlots() {
        super.rebuildSlots();
        int gapOff = gyOff();
        var ci = customSlotIndices;
        if (ci.size() < 15) return;
        for (int r = 0; r < 3; ++r) for (int c = 0; c < 3; ++c) { setSlotX(slots.get(ci.get(r * 3 + c)), 77 + c * 18); setSlotY(slots.get(ci.get(r * 3 + c)), gapOff + 1 + r * 18); }
        setSlotX(slots.get(ci.get(9)), 153); setSlotY(slots.get(ci.get(9)), gapOff + 20);
        for (int i = 0; i < 4; i++) { setSlotX(slots.get(ci.get(10 + i)), 8); setSlotY(slots.get(ci.get(10 + i)), gapOff + 1 + i * 18); }
        setSlotX(slots.get(ci.get(14)), 77); setSlotY(slots.get(ci.get(14)), gapOff + 55);
    }

    // 禁止通过双击"一键取走"从结果槽拿取（copy 原版 CraftingMenu 行为）
    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != resultSlots && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container != craftSlots) return;
        // 直接复用 BD slotChangedCraftingGrid：含 Polymorph 配方选择 + setRecipeUsed + isItemEnabled + 即时包推送
        com.wintercogs.beyonddimensions.common.menu.DimensionsCraftMenu.slotChangedCraftingGrid(
                this, player.level(), player, craftSlots, resultSlots, wsS + 9);
    }

    // 快速移动：结果槽 shift 交由 BD 通道批量合成；合成格/盔甲/副手→背包；背包→盔甲/副手
    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();

        if (slotIndex == wsS + 9) {
            // 结果槽 shift 由 BD 通道（CallSeverClickPacket → quickMoveHandle 批量合成+回滚）处理，
            // 此处返回 EMPTY 使原版通道空转，避免双通道重复合成（与 BD quickMoveStack no-op 一致）
            return ItemStack.EMPTY;
        }

        // 合成格 (wsS+0 ~ wsS+8) → 背包
        if (slotIndex >= wsS && slotIndex < wsS + 9) {
            if (!moveStackTo(stack, inventoryStartIndex, inventoryEndIndex, true)) return ItemStack.EMPTY;
            slot.setChanged(); slotsChanged(craftSlots); return result;
        }

        // 盔甲槽 (wsS+10 ~ wsS+13) → 背包
        if (slotIndex >= wsS + 10 && slotIndex < wsS + 14) {
            if (!moveStackTo(stack, inventoryStartIndex, inventoryEndIndex, true)) return ItemStack.EMPTY;
            slot.setChanged(); return result;
        }

        // 副手槽 (wsS+14) → 背包
        if (slotIndex >= wsS + 14 && slotIndex < wsS + 15) {
            if (!moveStackTo(stack, inventoryStartIndex, inventoryEndIndex, true)) return ItemStack.EMPTY;
            slot.setChanged(); return result;
        }

        // 背包 → 直接进网络：交由 BD 自定义快速移动通道处理
        // 客户端 shift 点击已发送 CallSeverClickPacket → customClickHandler → quickMoveHandle
        // 将槽位物品插入网络存储区（storageStartIndex~storageEndIndex，由 BD 负责插入与扣槽）；
        // 原版通道返回 EMPTY 不动作，避免重复插入与 QUICK_MOVE 循环。
        if (slotIndex >= inventoryStartIndex && slotIndex < inventoryEndIndex) {
            return ItemStack.EMPTY;
        }
        return super.quickMoveStack(player, slotIndex);
    }

    // 无按钮功能：恒返回 false
    @Override public boolean clickMenuButton(Player player, int id) { return false; }

    // 清空接口：归还 3×3 合成格物品（按方向）
    @Override
    public void cleanSlots(boolean toStorage) { cleanCraftSlots(toStorage); }

    // 归还合成格：先 copy 并清空再归还（网络→背包→掉落，绝不吞货），最后触发配方重算
    public void cleanCraftSlots(boolean toStorage) {
        if (player.level().isClientSide()) return;
        // 归还目标为当前打开的网络存储（对齐 BD cleanCraftSlots：用菜单 storage，而非玩家主网络）
        var storage = this.storage;
        // 先 copy 并清空再归还：归还过程不依赖容器内容，任何失败路径都能兜底到背包/掉落，绝不吞货
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            ItemStack s = craftSlots.getItem(i);
            if (!s.isEmpty()) stacks.add(s.copy());
        }
        craftSlots.clearContent();
        for (ItemStack s : stacks) {
            long remaining = s.getCount();
            if (toStorage) {
                if (storage != null) {
                    remaining = storage.insert(new ItemStackKey(s), s.getCount(), false).amount();
                }
            }
            // 对齐 BD cleanCraftSlots 状态同步：玩家存活且未断线才转移背包，否则跳过（走网络/掉落兜底）
            if (remaining > 0 && player.isAlive() && !((ServerPlayer) player).hasDisconnected()) {
                s.setCount((int) remaining);
                remaining = com.wintercogs.beyonddimensions.util.InventoryHelper.transferToPlayerInventory(player, s.copy()).getCount();
            }
            if (remaining > 0) {
                s.setCount((int) remaining);
                if (!toStorage && storage != null) {
                    remaining = storage.insert(new ItemStackKey(s), s.getCount(), false).amount();
                }
            }
            if (remaining > 0) {
                s.setCount((int) remaining);
                player.drop(s, false);
            }
        }
        slotsChanged(craftSlots);
        // 归还后立即全量同步玩家背包：关闭界面瞬间差分同步可能漏发个别槽，导致客户端物品栏不显示
        if (player instanceof ServerPlayer sp) {
            sp.inventoryMenu.broadcastFullState();
        }
    }

    // copy 自 BD DimensionsCraftMenu.transferRecipe：IStackKey 精确匹配（保留 NBT）
    public void transferRecipe(List<IStackKey<?>> inputKeys, List<Long> amounts) {
        cleanCraftSlots(firstCraftReturnDir);
        final int limit = Math.min(craftSlots.getContainerSize(), inputKeys.size());
        for (int i = 0; i < limit; i++) {
            long needL = (i < amounts.size() ? amounts.get(i) : 0L);
            IStackKey<?> key = inputKeys.get(i);
            if (!(key instanceof ItemStackKey itemStackKey) || needL <= 0) continue;
            int need = (int) Math.min(Integer.MAX_VALUE, needL);
            int remaining = extractFromInventory(player.getInventory(), itemStackKey.copyStack(), need);
            if (remaining > 0) remaining = extractFromStorage(storage, itemStackKey, remaining);
            int got = need - remaining;
            if (got > 0) craftSlots.setItem(i, itemStackKey.copyStackWithCount(got));
        }
        slotsChanged(craftSlots);
    }

    // 从背包提取物品（copy 自 BD）
    private int extractFromInventory(Inventory inventory, ItemStack template, int amount) {
        int remaining = amount;
        for (int i = 0; i < 36 && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (ItemStack.isSameItemSameComponents(stack, template)) {
                int extract = Math.min(remaining, stack.getCount());
                stack.shrink(extract);
                remaining -= extract;
                inventory.setItem(i, stack.isEmpty() ? ItemStack.EMPTY : stack);
            }
        }
        return remaining;
    }

    // 从存储提取物品（copy 自 BD）
    private int extractFromStorage(com.wintercogs.beyonddimensions.api.storage.handler.IStackHandler storage, IStackKey<?> type, int amount) {
        com.wintercogs.beyonddimensions.api.storage.key.KeyAmount extraction = storage.extract(type, amount, false, false);
        if (extraction.amount() > 0) {
            return amount - (int) extraction.amount();
        }
        return amount;
    }

    // 关闭菜单：归还合成格物品（结果槽不归还，对齐原版 CraftingMenu）
    @Override
    public void removed(@NotNull Player p) {
        super.removed(p);
        if (p.level().isClientSide()) return;
        cleanCraftSlots(firstCraftReturnDir);
    }
}
