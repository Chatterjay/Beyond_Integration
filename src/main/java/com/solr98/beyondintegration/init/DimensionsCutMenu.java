package com.solr98.beyondintegration.init;

import com.google.common.collect.Lists;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.UnorderedStackHandlerRemoveZero;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 切石机菜单（copy 自原版 StonecutterMenu(1.21.1) 改造，私有符号统一 beyond$ 前缀）：
 * 继承网络存储菜单；输入耗尽时 autoRefill 从网络补料（合成循环只消耗输入槽存量）；
 * 配方选择按 ID 保留、快捷移动适配网络存储。
 */
public class DimensionsCutMenu extends DimensionsStorageMenu implements ICleanableWorkstation {
    // 槽位常量：输入/输出槽数量、槽位 X 坐标与 Y 坐标
    private static final int WS_INPUT = 1, WS_OUTPUT = 1;
    private static final int[] WX = {20, 143};
    private static final int WY = 20;

    // ---- copy 原版 StonecutterMenu 私有状态（beyond$ 前缀）----
    // 输入槽容器（1 格，变化时触发配方重算并通知 GUI 刷新）
    private final SimpleContainer beyond$container = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            DimensionsCutMenu.this.slotsChanged(this);
            DimensionsCutMenu.this.beyond$slotUpdateListener.run();
        }
    };
    private final ResultContainer beyond$resultContainer = new ResultContainer(); // 结果槽容器
    private List<RecipeHolder<StonecutterRecipe>> beyond$recipes = Lists.newArrayList(); // 当前输入对应的配方列表
    private final DataSlot beyond$selectedRecipeIndex = DataSlot.standalone(); // 选中配方索引（同步到客户端）
    private ItemStack beyond$input = ItemStack.EMPTY; // 上次处理的输入物品（用于检测输入变化）
    private long beyond$lastSoundTime; // 上次播放取物音效的时间（限频）
    private Runnable beyond$slotUpdateListener = () -> {}; // 槽位更新回调（GUI 刷新配方列表用）
    private int beyond$wsS = -1; // 本菜单自加槽位起始索引
    // 上次选中的配方 ID：补料重算配方列表后按 ID 恢复选择
    private net.minecraft.resources.ResourceLocation beyond$lastSelectedRecipeId = null;
    // 上次消耗的输入材料（耗尽后补料用）；本轮 onTake 是否补料（用于终止 shift 循环）
    private ItemStack beyond$lastIngredient = ItemStack.EMPTY;
    private boolean beyond$justRefilled = false;
    private final Player owningPlayer; // 持有者玩家引用

    // 客户端构造：由网络包创建
    public DimensionsCutMenu(int id, Inventory inv, FriendlyByteBuf b) {
        this(ModMenus.CUT.get(), id, inv,
                new UnorderedStackHandlerRemoveZero(AbstractUnorderedStackHandler.UiTimestampPolicy.NONE));
    }

    // 主构造：注册输入槽与结果槽；结果槽 onTake 消耗输入、耗尽时补料并播放音效
    public DimensionsCutMenu(MenuType<?> t, int id, Inventory inv, AbstractUnorderedStackHandler d) {
        super(t, id, inv, d);
        owningPlayer = inv.player;
        beyond$wsS = slots.size();
        // 输入槽
        addSlot(new Slot(beyond$container, 0, WX[0], ey(WY)));
        customSlotIndices.add(slots.size() - 1);
        // 结果槽（BD 合成体验：onTake 消耗优先网络/背包扣，输入槽材料保留，可持续合成）
        // ResultSlot 子类：BD quickMoveHandle 的批量合成分支（64 上限 + 每轮 onTake 补料 + 回滚）依赖 instanceof ResultSlot
        addSlot(new ResultSlot(player, new TransientCraftingContainer(this, 1, 1), beyond$resultContainer, 1, WX[1], ey(WY)) {
            @Override public boolean mayPlace(ItemStack s) { return false; }
            @Override public void onTake(Player p, ItemStack st) {
                if (p.level().isClientSide()) return;
                beyond$justRefilled = false;
                st.onCraftedBy(p.level(), p, st.getCount());
                beyond$resultContainer.awardUsedRecipes(p, List.of(beyond$container.getItem(0)));
                ItemStack input = beyond$container.getItem(0);
                if (!input.isEmpty()) {
                    // 合成只消耗输入槽存量，绝不在合成过程中直接消耗网络材料
                    beyond$lastIngredient = input.copyWithCount(1);
                    beyond$container.removeItem(0, 1);
                }
                // 输入耗尽 → 循环结束后补料一次（最多 64）→ 由 quickMoveStack 终止循环
                if (beyond$container.getItem(0).isEmpty() && !beyond$lastIngredient.isEmpty()) {
                    beyond$refillTo64(p);
                    beyond$justRefilled = true;
                }
                beyond$setupResultSlot();
                long l = p.level().getGameTime();
                if (beyond$lastSoundTime != l) {
                    p.level().playSound(null, p.blockPosition(), SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
                    beyond$lastSoundTime = l;
                }
                super.onTake(p, st);
            }
        });
        customSlotIndices.add(slots.size() - 1);
        this.addDataSlot(this.beyond$selectedRecipeIndex);
    }

    // 重建布局：定位输入/结果槽坐标
    @Override public void rebuildSlots() {
        super.rebuildSlots();
        if (beyond$wsS >= 0) { int y = ey(WY); for (int i = 0; i < 2; i++) { setSlotX(slots.get(beyond$wsS + i), WX[i]); setSlotY(slots.get(beyond$wsS + i), y); } }
    }

    // ---- BI 公开 API（GUI 使用）----
    public ItemStack getOutput() { return beyond$resultContainer.getItem(0); }
    public ItemStack getInput() { return beyond$container.getItem(0); }
    public List<StonecutterRecipe> getRecipes() { return beyond$recipes.stream().map(RecipeHolder::value).toList(); }
    public int getNumRecipes() { return beyond$recipes.size(); }
    public int getSelectedRecipeIndex() { return beyond$selectedRecipeIndex.get(); }
    public boolean hasInputItem() { return !beyond$container.getItem(0).isEmpty() && !beyond$recipes.isEmpty(); }

    // 注册槽位更新回调（GUI 刷新配方列表用）
    public void registerUpdateListener(Runnable listener) { this.beyond$slotUpdateListener = listener; }

    // ---- copy 原版 StonecutterMenu 核心逻辑 ----
    @Override
    public void slotsChanged(Container inventory) {
        super.slotsChanged(inventory);
        ItemStack itemstack = beyond$container.getItem(0);
        if (!itemstack.is(this.beyond$input.getItem())) {
            this.beyond$input = itemstack.copy();
            this.beyond$setupRecipeList(itemstack);
        }
        // 补料仅在结果槽 onTake（消耗后）进行；手动拿走或关闭界面（cleanSlots）不补
    }

    private void beyond$setupRecipeList(ItemStack stack) {
        this.beyond$recipes.clear();
        this.beyond$selectedRecipeIndex.set(-1);
        this.beyond$resultContainer.setItem(0, ItemStack.EMPTY);
        if (!stack.isEmpty()) {
            this.beyond$recipes = owningPlayer.level().getRecipeManager()
                    .getRecipesFor(RecipeType.STONECUTTING, new SingleRecipeInput(stack), owningPlayer.level());
            // 配方选择保留：重算后若上次选中的配方仍存在（同 ID 补料场景）则恢复并自动装配；
            // 换材料时 ID 不匹配则保持未选中（结果空，玩家重新点选，对齐原版）
            if (this.beyond$lastSelectedRecipeId != null) {
                for (int i = 0; i < this.beyond$recipes.size(); i++) {
                    if (this.beyond$recipes.get(i).id().equals(this.beyond$lastSelectedRecipeId)) {
                        this.beyond$selectedRecipeIndex.set(i);
                        break;
                    }
                }
            }
        }
        if (this.beyond$selectedRecipeIndex.get() >= 0) {
            this.beyond$setupResultSlot();
        }
    }

    // 装配当前选中配方的产物到结果槽（校验物品是否启用），并广播变化
    void beyond$setupResultSlot() {
        if (!this.beyond$recipes.isEmpty() && this.beyond$isValidRecipeIndex(this.beyond$selectedRecipeIndex.get())) {
            RecipeHolder<StonecutterRecipe> recipeholder = this.beyond$recipes.get(this.beyond$selectedRecipeIndex.get());
            ItemStack itemstack = recipeholder.value().assemble(new SingleRecipeInput(this.beyond$container.getItem(0)), owningPlayer.level().registryAccess());
            if (itemstack.isItemEnabled(owningPlayer.level().enabledFeatures())) {
                this.beyond$resultContainer.setRecipeUsed(recipeholder);
                this.beyond$resultContainer.setItem(0, itemstack);
            } else {
                this.beyond$resultContainer.setItem(0, ItemStack.EMPTY);
            }
        } else {
            this.beyond$resultContainer.setItem(0, ItemStack.EMPTY);
        }
        this.broadcastChanges();
    }

    private boolean beyond$isValidRecipeIndex(int recipeIndex) {
        return recipeIndex >= 0 && recipeIndex < this.beyond$recipes.size();
    }

    // 按钮点击：切换选中配方（记录配方 ID 供补料后恢复）并重新装配结果
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (this.beyond$isValidRecipeIndex(id)) {
            this.beyond$selectedRecipeIndex.set(id);
            this.beyond$lastSelectedRecipeId = this.beyond$recipes.get(id).id();
            this.beyond$setupResultSlot();
        }
        return true;
    }

    // 从网络补料至输入槽（最多 64）；仅在合成循环耗尽后调用一次，不参与合成消耗
    private void beyond$refillTo64(Player player) {
        if (player.level().isClientSide()) return;
        if (!beyond$container.getItem(0).isEmpty()) return;
        if (beyond$lastIngredient.isEmpty()) return;
        DimensionsNet net = com.solr98.beyondintegration.handler.MenuNetIdHelper.getNetFromMenu((ServerPlayer) player);
        if (net == null) net = DimensionsNet.getPrimaryNetFromPlayer((ServerPlayer) player);
        if (net == null) return;
        int want = beyond$container.getMaxStackSize(beyond$lastIngredient);
        KeyAmount extracted = net.getUnifiedStorage().extract(new ItemStackKey(beyond$lastIngredient), want, false, false);
        if (extracted.amount() > 0) {
            beyond$container.setItem(0, beyond$lastIngredient.copyWithCount((int) extracted.amount()));
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

    // 关闭菜单：归还输入槽物品（结果槽直接丢弃，对齐原版）
    @Override
    public void removed(@NotNull Player p) {
        super.removed(p);
        if (p.level().isClientSide()) return;
        cleanSlots(firstCraftReturnDir);
    }

    @Override
    public void cleanSlots(boolean toStorage) {
        cleanSlotsFromContainer(toStorage, beyond$container, new int[]{0});
        // 结果槽对齐原版 StonecutterMenu.removed：关闭时直接丢弃，不归还
        beyond$resultContainer.setItem(0, ItemStack.EMPTY);
    }
}
