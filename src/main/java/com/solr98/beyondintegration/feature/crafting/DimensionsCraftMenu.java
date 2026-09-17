package com.solr98.beyondintegration.feature.crafting;

import com.mojang.datafixers.util.Pair;
import com.solr98.beyondintegration.init.ModMenus;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.UnorderedStackHandlerRemoveZero;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
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
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.ForgeHooks;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 3×3 合成台菜单（继承网络存储菜单）：合成格/结果槽/盔甲槽/副手槽 + 网络存储。
 * 结果槽取走时即时补料（网络优先→背包），shift 合成走 BD 批量合成通道（带回滚）；
 * 支持 JEI/EMI 配方一键填充与合成格一键清空。
 */
public class DimensionsCraftMenu extends DimensionsStorageMenu implements ICleanableWorkstation {
    // 3×3 合成格容器（变化触发 slotsChanged 重算结果）
    private final TransientCraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    // 合成结果容器
    private final ResultContainer resultSlots = new ResultContainer();
    // 菜单所属玩家（服务端操作使用）
    private final Player player;
    // 工作台自定义槽起始索引（背包 36 + 存储槽之后）
    private int wsS = -1;
    @Override public int getPanelHeight() { return 72; }

    // 工作台区域相对面板的 Y 偏移（随存储行数自适应）
    private int gyOff() { return 68 + (getLines() - 2) * 18; }

    public DimensionsCraftMenu(int id, Inventory inv, net.minecraft.network.FriendlyByteBuf b) {
        this(ModMenus.CRAFT.get(), id, inv,
                new UnorderedStackHandlerRemoveZero(
                        AbstractUnorderedStackHandler.UiTimestampPolicy.NONE));
    }

    public DimensionsCraftMenu(MenuType<?> t, int id, Inventory inv, AbstractUnorderedStackHandler d) {
        super(t, id, inv, d);
        this.player = inv.player;
        // 已对齐 BD 即时补料：结果槽 shift 走 BD 批量合成通道（quickMoveHandle，带回滚），不屏蔽
        this.blockResultSlotShiftClick = false;
        // BD 基类已添加背包(36) + 存储槽(99行×9)，工作台槽位从 wsS 开始
        this.wsS = slots.size();
        int gapOff = gyOff();

        // 3×3 合成格 (相对面板: x=77, y=1)
        for (int r = 0; r < 3; ++r)
            for (int c = 0; c < 3; ++c) {
                int idx = c + r * 3;
                addSlot(new Slot(craftSlots, idx, 77 + c * 18, gapOff + 1 + r * 18) {
                    @Override public void setChanged() {
                        super.setChanged();
                        slotsChanged(craftSlots);
                    }
                });
                customSlotIndices.add(slots.size() - 1);
            }

        // 结果槽 (相对面板: x=153, y=20)
        // 合成实现 copy 自 BD AutoRefillResultSlot：取走时即时补料（网络优先 → 背包），
        // 槽位耗尽时先验原料再扣（格内数量不变），返回物槽位 → 网络 → 背包 → 掉落
        addSlot(new ResultSlot(player, craftSlots, resultSlots, 0, 153, gapOff + 20) {
            @Override
            public void onTake(Player p, ItemStack stack) {
                if (p.level().isClientSide()) return;
                // 原版 ResultSlot：成就/统计
                this.checkTakeAchievements(stack);
                ForgeHooks.setCraftingPlayer(p);
                NonNullList<ItemStack> remainingItems = p.level().getRecipeManager().getRemainingItemsFor(RecipeType.CRAFTING, craftSlots, p.level());
                ForgeHooks.setCraftingPlayer(null);

                // 此函数每次调用最多完成一次合成
                int craftTimes = 1;
                for (int slotIndex = 0; slotIndex < remainingItems.size(); ++slotIndex) {
                    ItemStack slotStack = craftSlots.getItem(slotIndex);
                    ItemStack recipeRemainder = remainingItems.get(slotIndex);

                    if (!slotStack.isEmpty()) {
                        int itemsToRemove = craftTimes;
                        // 槽位将耗尽时，优先从网络/背包补足本次消耗（先验原料量再显示产物的机制）
                        if (slotStack.getCount() <= itemsToRemove) {
                            ItemStackKey toRemoveKey = new ItemStackKey(slotStack);
                            int extracted = (int) storage.extract(toRemoveKey, itemsToRemove, false, false).amount();
                            itemsToRemove -= extracted;
                            for (int i = 0; i < p.getInventory().items.size() && itemsToRemove > 0; i++) {
                                ItemStack invStack = p.getInventory().items.get(i);
                                if (ItemStack.isSameItemSameTags(invStack, toRemoveKey.getReadOnlyStack())) {
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
                slotsChanged(craftSlots);
            }
        });
        customSlotIndices.add(slots.size() - 1);

        // 盔甲槽 (相对面板: x=8, y=1/19/37/55)
        Inventory pi = inv;
        addSlot(new Slot(pi, 39, 8, gapOff + 1) {
            @Override public boolean mayPlace(ItemStack s) { return s.canEquip(EquipmentSlot.HEAD, player); }
            @Override public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_HELMET);
            }
            @Override public void setByPlayer(ItemStack newStack) {
                ItemStack oldStack = getItem().copy();
                super.setByPlayer(newStack);
                player.onEquipItem(EquipmentSlot.HEAD, oldStack, newStack);
            }
        });
        customSlotIndices.add(slots.size() - 1);
        addSlot(new Slot(pi, 38, 8, gapOff + 19) {
            @Override public boolean mayPlace(ItemStack s) { return s.canEquip(EquipmentSlot.CHEST, player); }
            @Override public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE);
            }
            @Override public void setByPlayer(ItemStack newStack) {
                ItemStack oldStack = getItem().copy();
                super.setByPlayer(newStack);
                player.onEquipItem(EquipmentSlot.CHEST, oldStack, newStack);
            }
        });
        customSlotIndices.add(slots.size() - 1);
        addSlot(new Slot(pi, 37, 8, gapOff + 37) {
            @Override public boolean mayPlace(ItemStack s) { return s.canEquip(EquipmentSlot.LEGS, player); }
            @Override public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS);
            }
            @Override public void setByPlayer(ItemStack newStack) {
                ItemStack oldStack = getItem().copy();
                super.setByPlayer(newStack);
                player.onEquipItem(EquipmentSlot.LEGS, oldStack, newStack);
            }
        });
        customSlotIndices.add(slots.size() - 1);
        addSlot(new Slot(pi, 36, 8, gapOff + 55) {
            @Override public boolean mayPlace(ItemStack s) { return s.canEquip(EquipmentSlot.FEET, player); }
            @Override public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS);
            }
            @Override public void setByPlayer(ItemStack newStack) {
                ItemStack oldStack = getItem().copy();
                super.setByPlayer(newStack);
                player.onEquipItem(EquipmentSlot.FEET, oldStack, newStack);
            }
        });
        customSlotIndices.add(slots.size() - 1);
        // 副手槽 (相对面板: x=77, y=55)
        addSlot(new Slot(pi, 40, 77, gapOff + 55) {
            @Override public boolean mayPlace(ItemStack s) { return true; }
            @Override public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);
            }
            @Override public void setByPlayer(ItemStack newStack) {
                ItemStack oldStack = getItem().copy();
                super.setByPlayer(newStack);
                player.onEquipItem(EquipmentSlot.OFFHAND, oldStack, newStack);
            }
        });
        customSlotIndices.add(slots.size() - 1);
    }

    @Override public void rebuildSlots() {
        super.rebuildSlots();
        int gapOff = gyOff();
        var ci = customSlotIndices;
        if (ci.size() < 15) return;
        // 合成格 (0-8)
        for (int r = 0; r < 3; ++r)
            for (int c = 0; c < 3; ++c) {
                setSlotX(slots.get(ci.get(r * 3 + c)), 77 + c * 18);
                setSlotY(slots.get(ci.get(r * 3 + c)), gapOff + 1 + r * 18);
            }
        // 结果槽 (9)
        setSlotX(slots.get(ci.get(9)), 153);
        setSlotY(slots.get(ci.get(9)), gapOff + 20);
        // 盔甲槽 (10-13)
        for (int i = 0; i < 4; i++) {
            setSlotX(slots.get(ci.get(10 + i)), 8);
            setSlotY(slots.get(ci.get(10 + i)), gapOff + 1 + i * 18);
        }
        // 副手槽 (14)
        setSlotX(slots.get(ci.get(14)), 77);
        setSlotY(slots.get(ci.get(14)), gapOff + 55);
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container != craftSlots) return;
        // 直接复用 BD slotChangedCraftingGrid：含 Polymorph 配方选择 + setRecipeUsed + isItemEnabled + 即时包推送
        com.wintercogs.beyonddimensions.common.menu.DimensionsCraftMenu.slotChangedCraftingGrid(
                this, player.level(), player, craftSlots, resultSlots, wsS + 9);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != resultSlots && super.canTakeItemForPickAll(stack, slot);
    }

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
            if (!moveStackTo(stack, inventoryStartIndex, inventoryEndIndex, true))
                return ItemStack.EMPTY;
            slot.setChanged();
            slotsChanged(craftSlots);
            return result;
        }

        // 盔甲槽 (wsS+10 ~ wsS+13) → 背包
        if (slotIndex >= wsS + 10 && slotIndex < wsS + 14) {
            if (!moveStackTo(stack, inventoryStartIndex, inventoryEndIndex, true))
                return ItemStack.EMPTY;
            slot.setChanged();
            return result;
        }

        // 副手槽 (wsS+14) → 背包
        if (slotIndex >= wsS + 14 && slotIndex < wsS + 15) {
            if (!moveStackTo(stack, inventoryStartIndex, inventoryEndIndex, true))
                return ItemStack.EMPTY;
            slot.setChanged();
            return result;
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

    @Override
    public boolean clickMenuButton(Player player, int id) { return false; }

    // 合成格转移按钮
    // 先 copy 并清空再归还（网络→背包→掉落），任何失败路径都不吞货
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

    // ── JEI/EMI 配方填充 ──
    // JEI/EMI 配方填充：先清空合成格，再按输入键从背包（优先）→网络取料填入前 9 格并重算结果
    public void transferRecipe(java.util.List<IStackKey<?>> inputKeys, java.util.List<Long> amounts) {
        // 清空合成格（背包优先归还，满则掉落，不吞货）
        cleanCraftSlots(false);
        // 取料目标为当前打开的网络存储（对齐 BD transferRecipe）
        var storage = this.storage;
        int limit = Math.min(9, inputKeys.size());
        for (int i = 0; i < limit; i++) {
            if (!(inputKeys.get(i) instanceof ItemStackKey isk)) continue;
            long needL = i < amounts.size() ? amounts.get(i) : 0;
            if (needL <= 0) continue;
            int need = (int) Math.min(Integer.MAX_VALUE, needL);
            // 背包优先
            for (int j = 0; j < 36 && need > 0; j++) {
                ItemStack inv = player.getInventory().getItem(j);
                if (ItemStack.isSameItemSameTags(inv, isk.getReadOnlyStack())) {
                    int take = Math.min(need, inv.getCount());
                    inv.shrink(take);
                    player.getInventory().setItem(j, inv.isEmpty() ? ItemStack.EMPTY : inv);
                    need -= take;
                }
            }
            // 网络其次
            if (need > 0 && storage != null) {
                need -= (int) storage.extract(isk, need, false, false).amount();
            }
            int got = (int) Math.min(needL, needL - need);
            if (got > 0) craftSlots.setItem(i, isk.copyStackWithCount(got));
        }
        slotsChanged(craftSlots);
    }

    @Override
    public void removed(@NotNull Player p) {
        super.removed(p);
        if (p.level().isClientSide()) return;
        cleanSlots(firstCraftReturnDir);
    }

    @Override
    public void cleanSlots(boolean toStorage) {
        cleanCraftSlots(toStorage);
    }
}
