package com.solr98.beyondintegration.init;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.UnorderedStackHandlerRemoveZero;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 锻造台菜单（copy 自原版 SmithingMenu + ItemCombinerMenu(1.21.1) 骨架改造，
 * 私有符号统一 beyond$ 前缀）：继承网络存储菜单；去掉 ContainerLevelAccess（音效改玩家位置）；
 * 模板/附加槽耗尽时从网络补料；快捷移动适配网络存储。
 */
public class DimensionsSmithMenu extends DimensionsStorageMenu implements ICleanableWorkstation {
    // 槽位常量：输入 3 / 输出 3、四槽位 X 坐标与 Y 坐标
    private static final int WS_INPUT = 3, WS_OUTPUT = 3;
    private static final int[] WX = {8, 26, 44, 98}; // 模板/底座/附加 + 输出槽（输出槽右移 1px）
    private static final int WY = 41;

    // ---- copy 原版 ItemCombinerMenu/SmithingMenu 私有状态（beyond$ 前缀）----
    // 输入槽容器（模板 0 / 底座 1 / 附加 2）
    private final Container beyond$inputSlots;
    private final ResultContainer beyond$resultSlots = new ResultContainer(); // 结果槽容器
    private final Level beyond$level; // 配方查询用的世界引用
    @Nullable
    private RecipeHolder<SmithingRecipe> beyond$selectedRecipe; // 当前匹配到的锻造配方（null 则无结果）
    private final List<RecipeHolder<SmithingRecipe>> beyond$recipes; // 全部锻造配方（mayPlace 判定用）
    private int beyond$wsS = -1; // 本菜单自加槽位起始索引
    // 自动补充记录：仅模板槽(0)/附加槽(2)，底座槽(1)是工具不放工具不补
    private ItemStack beyond$lastTemplate = ItemStack.EMPTY;
    private ItemStack beyond$lastAdditional = ItemStack.EMPTY;
    // 本轮 onTake 是否补料：补料后 quickMoveStack 返回 EMPTY 终止原版 while 循环（补料不参与本轮合成）
    private boolean beyond$justRefilled = false;

    // 客户端构造：由网络包创建
    public DimensionsSmithMenu(int id, Inventory inv, FriendlyByteBuf b) {
        this(ModMenus.SMITH.get(), id, inv,
                new UnorderedStackHandlerRemoveZero(AbstractUnorderedStackHandler.UiTimestampPolicy.NONE));
    }

    // 主构造：注册模板/底座/附加/结果槽；结果槽 onTake 消耗三槽、模板/附加耗尽时自动补料
    public DimensionsSmithMenu(MenuType<?> t, int id, Inventory inv, AbstractUnorderedStackHandler d) {
        super(t, id, inv, d);
        this.beyond$level = inv.player.level();
        this.beyond$recipes = this.beyond$level.getRecipeManager().getAllRecipesFor(RecipeType.SMITHING);
        // 原版 ItemCombinerMenu.createContainer：输入槽变化 → slotsChanged → createResult
        this.beyond$inputSlots = new SimpleContainer(3) {
            @Override
            public void setChanged() {
                super.setChanged();
                DimensionsSmithMenu.this.slotsChanged(this);
            }
        };
        beyond$wsS = slots.size();
        // 模板槽 0
        addSlot(new Slot(beyond$inputSlots, 0, WX[0], ey(WY)) {
            @Override public boolean mayPlace(ItemStack s) {
                return beyond$recipes.stream().anyMatch(r -> r.value().isTemplateIngredient(s));
            }
        });
        customSlotIndices.add(slots.size() - 1);
        // 底座槽 1
        addSlot(new Slot(beyond$inputSlots, 1, WX[1], ey(WY)) {
            @Override public boolean mayPlace(ItemStack s) {
                return beyond$recipes.stream().anyMatch(r -> r.value().isBaseIngredient(s));
            }
        });
        customSlotIndices.add(slots.size() - 1);
        // 附加槽 2
        addSlot(new Slot(beyond$inputSlots, 2, WX[2], ey(WY)) {
            @Override public boolean mayPlace(ItemStack s) {
                return beyond$recipes.stream().anyMatch(r -> r.value().isAdditionIngredient(s));
            }
        });
        customSlotIndices.add(slots.size() - 1);
        // 结果槽 3（原版 ItemCombinerMenu 结果槽：mayPickup/onTake）
        // ResultSlot 子类：BD quickMoveHandle 的批量合成分支（64 上限 + 每轮 onTake 补料 + 回滚）依赖 instanceof ResultSlot
        addSlot(new ResultSlot(player, new TransientCraftingContainer(this, 1, 1), beyond$resultSlots, 0, WX[3], ey(WY)) {
            @Override public boolean mayPlace(ItemStack s) { return false; }
            @Override public boolean mayPickup(Player p) {
                return beyond$selectedRecipe != null && beyond$selectedRecipe.value().matches(beyond$createRecipeInput(), beyond$level);
            }
            @Override public void onTake(Player p, ItemStack stack) {
                beyond$justRefilled = false;
                stack.onCraftedBy(p.level(), p, stack.getCount());
                beyond$resultSlots.awardUsedRecipes(p, beyond$getRelevantItems());
                beyond$shrinkStackInSlot(0);
                beyond$shrinkStackInSlot(1);
                beyond$shrinkStackInSlot(2);
                p.level().levelEvent(1044, p.blockPosition(), 0);
                // 合成只消耗输入槽存量；模板/附加槽耗尽时才从网络补料（最多 64），由 quickMoveStack 终止循环
                if (!p.level().isClientSide() && beyond$autoRefill()) beyond$justRefilled = true;
            }
        });
        customSlotIndices.add(slots.size() - 1);
    }

    // 重建布局：定位模板/底座/附加/结果四槽坐标
    @Override public void rebuildSlots() {
        super.rebuildSlots();
        if (beyond$wsS >= 0) { int y = ey(WY); for (int i = 0; i < 4; i++) { setSlotX(slots.get(beyond$wsS + i), WX[i]); setSlotY(slots.get(beyond$wsS + i), y); } }
    }

    // ---- BI 公开 API（GUI 使用）----
    public ItemStack getOutput() { return beyond$resultSlots.getItem(0); }
    public ItemStack getTemplate() { return beyond$inputSlots.getItem(0); }
    public ItemStack getBase() { return beyond$inputSlots.getItem(1); }
    public ItemStack getAdditional() { return beyond$inputSlots.getItem(2); }
    public Slot getTemplateSlot() { return slots.get(beyond$wsS + 0); }
    public Slot getBaseSlot() { return slots.get(beyond$wsS + 1); }
    public Slot getAdditionalSlot() { return slots.get(beyond$wsS + 2); }

    // ---- copy 原版 ItemCombinerMenu.slotsChanged ----
    @Override
    public void slotsChanged(Container inventory) {
        super.slotsChanged(inventory);
        if (inventory == this.beyond$inputSlots) {
            this.beyond$createResult();
            // 记录模板/附加槽最近物品（补料仅在结果槽 onTake 消耗后进行，手动拿走/关闭界面不补）
            ItemStack t = this.beyond$inputSlots.getItem(0);
            ItemStack a = this.beyond$inputSlots.getItem(2);
            if (!t.isEmpty()) this.beyond$lastTemplate = t.copyWithCount(1);
            if (!a.isEmpty()) this.beyond$lastAdditional = a.copyWithCount(1);
        }
    }

    // 空槽从网络补充（仅模板/附加，补满一组）；返回是否发生了补料
    private boolean beyond$autoRefill() {
        boolean refilled = beyond$autoRefillSlot(0, this.beyond$lastTemplate);
        refilled |= beyond$autoRefillSlot(2, this.beyond$lastAdditional);
        return refilled;
    }

    // 指定输入槽为空且记录有上次物品时，从网络补充一组；返回是否补料
    private boolean beyond$autoRefillSlot(int idx, ItemStack last) {
        if (last.isEmpty()) return false;
        if (!this.beyond$inputSlots.getItem(idx).isEmpty()) return false;
        DimensionsNet net = com.solr98.beyondintegration.handler.MenuNetIdHelper.getNetFromMenu((net.minecraft.server.level.ServerPlayer) this.player);
        if (net == null) net = DimensionsNet.getPrimaryNetFromPlayer((net.minecraft.server.level.ServerPlayer) this.player);
        if (net == null) return false;
        int take = this.beyond$inputSlots.getMaxStackSize(last);
        com.wintercogs.beyonddimensions.api.storage.key.KeyAmount extracted =
                net.getUnifiedStorage().extract(new ItemStackKey(last), take, false, false);
        if (extracted.amount() > 0) {
            this.beyond$inputSlots.setItem(idx, last.copyWithCount((int) extracted.amount()));
            return true;
        }
        return false;
    }

    // 组装三输入物品为 SmithingRecipeInput（配方匹配/合成用）
    private SmithingRecipeInput beyond$createRecipeInput() {
        return new SmithingRecipeInput(this.beyond$inputSlots.getItem(0), this.beyond$inputSlots.getItem(1), this.beyond$inputSlots.getItem(2));
    }

    // 收集三输入物品（用于配方使用统计）
    private List<ItemStack> beyond$getRelevantItems() {
        return List.of(this.beyond$inputSlots.getItem(0), this.beyond$inputSlots.getItem(1), this.beyond$inputSlots.getItem(2));
    }

    // 指定输入槽物品数量 -1（合成消耗）
    private void beyond$shrinkStackInSlot(int index) {
        ItemStack itemstack = this.beyond$inputSlots.getItem(index);
        if (!itemstack.isEmpty()) {
            itemstack.shrink(1);
            this.beyond$inputSlots.setItem(index, itemstack);
        }
    }

    // ---- copy 原版 SmithingMenu.createResult ----
    public void beyond$createResult() {
        SmithingRecipeInput smithingrecipeinput = this.beyond$createRecipeInput();
        List<RecipeHolder<SmithingRecipe>> list = this.beyond$level.getRecipeManager().getRecipesFor(RecipeType.SMITHING, smithingrecipeinput, this.beyond$level);
        if (list.isEmpty()) {
            this.beyond$resultSlots.setItem(0, ItemStack.EMPTY);
        } else {
            RecipeHolder<SmithingRecipe> recipeholder = list.get(0);
            ItemStack itemstack = recipeholder.value().assemble(smithingrecipeinput, this.beyond$level.registryAccess());
            if (itemstack.isItemEnabled(this.beyond$level.enabledFeatures())) {
                this.beyond$selectedRecipe = recipeholder;
                this.beyond$resultSlots.setRecipeUsed(recipeholder);
                this.beyond$resultSlots.setItem(0, itemstack);
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        if (slotIndex == beyond$wsS + WS_OUTPUT) {
            // shift 合成：原版 while 循环逐轮驱动；合成只消耗输入槽存量，
            // 耗尽后 onTake 补料一次并置标志 → 此处返回 EMPTY 终止循环
            int beforeCount = stack.getCount();
            moveStackTo(stack, inventoryStartIndex, inventoryEndIndex, true);
            if (!stack.isEmpty()) {
                DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
                if (net != null) { long remaining = net.getUnifiedStorage().insert(new ItemStackKey(stack), stack.getCount(), false).amount(); stack.setCount((int) remaining); }
            }
            if (stack.getCount() < beforeCount) {
                slot.onQuickCraft(result, stack);
                slot.onTake(player, result);
                if (beyond$justRefilled) { beyond$justRefilled = false; return ItemStack.EMPTY; }
                return result;
            }
            return ItemStack.EMPTY;
        }
        if (slotIndex >= beyond$wsS && slotIndex < beyond$wsS + WS_INPUT) {
            if (!moveStackTo(stack, inventoryStartIndex, inventoryEndIndex, true)) return ItemStack.EMPTY;
            slot.setChanged(); return result;
        }
        if (slotIndex >= inventoryStartIndex && slotIndex < inventoryEndIndex) {
            for (int i = 0; i < WS_INPUT && !stack.isEmpty(); i++) {
                Slot target = this.slots.get(beyond$wsS + i);
                if (!target.mayPlace(stack)) continue;
                ItemStack ts = target.getItem();
                if (ts.isEmpty()) { int n = Math.min(stack.getCount(), target.getMaxStackSize(stack)); target.set(stack.split(n)); target.setChanged(); }
                else if (ItemStack.isSameItemSameComponents(stack, ts)) { int space = target.getMaxStackSize(stack) - ts.getCount(); if (space > 0) { int n = Math.min(stack.getCount(), space); ts.grow(n); stack.shrink(n); target.set(ts); target.setChanged(); } }
            }
            if (stack.isEmpty()) { slot.setChanged(); return result; }
        }
        return super.quickMoveStack(player, slotIndex);
    }

    // 关闭菜单：归还三输入槽物品（结果槽直接丢弃，对齐原版）
    @Override
    public void removed(@NotNull Player p) {
        super.removed(p);
        if (p.level().isClientSide()) return;
        cleanSlots(firstCraftReturnDir);
    }

    @Override
    public void cleanSlots(boolean toStorage) {
        cleanSlotsFromContainer(toStorage, beyond$inputSlots, new int[]{0, 1, 2});
        // 结果槽对齐原版 SmithingMenu.removed：关闭时直接丢弃，不归还
        beyond$resultSlots.setItem(0, ItemStack.EMPTY);
    }
}
