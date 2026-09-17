package com.solr98.beyondintegration.init;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.UnorderedStackHandlerRemoveZero;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import org.jetbrains.annotations.NotNull;

/**
 * 磨石菜单（copy 自原版 GrindstoneMenu(1.21.1) 改造，私有符号统一 beyond$ 前缀）：
 * 继承网络存储菜单；经验球奖励与音效改在玩家位置播放；快捷移动适配网络存储。
 */
public class DimensionsGrindMenu extends DimensionsStorageMenu implements ICleanableWorkstation {
    // 槽位常量：输入 0 / 附加 1 / 结果 2
    public static final int INPUT_SLOT = 0;
    public static final int ADDITIONAL_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    // 输入/输出槽数量与三槽位 X/Y 坐标
    private static final int WS_INPUT = 2, WS_OUTPUT = 2;
    private static final int[] WX = {49, 49, 129};
    private static final int[] WY = {6, 27, 21};

    // ---- copy 原版 GrindstoneMenu 私有状态（beyond$ 前缀）----
    // 输入 + 附加槽容器（2 格，变化时触发结果重算）
    final Container beyond$repairSlots = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            DimensionsGrindMenu.this.slotsChanged(this);
        }
    };
    final Container beyond$resultSlots = new ResultContainer(); // 结果槽容器
    private int beyond$xp = -1; // 当前可返还经验值（-1 表示未预计算）
    private int beyond$wsS = -1; // 本菜单自加槽位起始索引

    // 客户端构造：由网络包创建
    public DimensionsGrindMenu(int id, Inventory inv, FriendlyByteBuf b) {
        this(ModMenus.GRIND.get(), id, inv,
                new UnorderedStackHandlerRemoveZero(AbstractUnorderedStackHandler.UiTimestampPolicy.NONE));
    }

    // 主构造：注册输入槽（原版 mayPlace 规则）与结果槽（onTake 返还经验球/音效并清空双槽）
    public DimensionsGrindMenu(MenuType<?> t, int id, Inventory inv, AbstractUnorderedStackHandler d) {
        super(t, id, inv, d);
        beyond$wsS = slots.size();
        // 输入槽 0/1（原版 mayPlace 规则）
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            addSlot(new Slot(beyond$repairSlots, i, WX[i], ey(WY[i])) {
                @Override public boolean mayPlace(ItemStack s) {
                    return s.isDamageableItem() || EnchantmentHelper.hasAnyEnchantments(s) || s.canGrindstoneRepair();
                }
            });
            customSlotIndices.add(slots.size() - 1);
        }
        // 结果槽 2（原版 onTake：GrindstoneTake hook + 经验球奖励 + 音效 + 清双槽）
        addSlot(new Slot(beyond$resultSlots, 0, WX[2], ey(WY[2])) {
            @Override public boolean mayPlace(ItemStack s) { return false; }
            @Override public void onTake(Player p, ItemStack st) {
                ContainerLevelAccess access = ContainerLevelAccess.create(p.level(), p.blockPosition());
                if (CommonHooks.onGrindstoneTake(DimensionsGrindMenu.this.beyond$repairSlots, access, p, lvl -> beyond$getExperienceAmount(lvl))) return;
                if (p.level() instanceof ServerLevel serverLevel) {
                    ExperienceOrb.award(serverLevel, Vec3.atCenterOf(p.blockPosition()), beyond$getExperienceAmount(p.level()));
                }
                p.level().levelEvent(1042, p.blockPosition(), 0);
                beyond$repairSlots.setItem(0, ItemStack.EMPTY);
                beyond$repairSlots.setItem(1, ItemStack.EMPTY);
            }
        });
        customSlotIndices.add(slots.size() - 1);
    }

    // 重建布局：定位输入/附加/结果三槽坐标
    @Override public void rebuildSlots() {
        super.rebuildSlots();
        if (beyond$wsS >= 0) { for (int i = 0; i < 3; i++) { setSlotX(slots.get(beyond$wsS + i), WX[i]); setSlotY(slots.get(beyond$wsS + i), ey(WY[i])); } }
    }

    // ---- BI 公开 API（GUI 使用）----
    public ItemStack getOutput() { return beyond$resultSlots.getItem(0); }
    public ItemStack getInput() { return beyond$repairSlots.getItem(0); }
    public ItemStack getAdditional() { return beyond$repairSlots.getItem(1); }

    // ---- copy 原版 GrindstoneMenu 核心逻辑 ----
    @Override
    public void slotsChanged(Container inventory) {
        super.slotsChanged(inventory);
        if (inventory == this.beyond$repairSlots) {
            this.beyond$createResult();
        }
    }

    // 计算磨石结果：先走 forge onGrindstoneChange hook（未拦截时按原版逻辑合成）并广播变化
    private void beyond$createResult() {
        this.beyond$xp = CommonHooks.onGrindstoneChange(this.beyond$repairSlots.getItem(0), this.beyond$repairSlots.getItem(1), this.beyond$resultSlots, -1);
        if (this.beyond$xp == Integer.MIN_VALUE)
            this.beyond$resultSlots.setItem(0, this.beyond$computeResult(this.beyond$repairSlots.getItem(0), this.beyond$repairSlots.getItem(1)));
        this.broadcastChanges();
    }

    // 原版合成判定：仅单件有附魔→去诅咒；两件同物品→合并；其余返回空
    private ItemStack beyond$computeResult(ItemStack inputItem, ItemStack additionalItem) {
        boolean flag = !inputItem.isEmpty() || !additionalItem.isEmpty();
        if (!flag) {
            return ItemStack.EMPTY;
        } else if (inputItem.getCount() <= 1 && additionalItem.getCount() <= 1) {
            boolean flag1 = !inputItem.isEmpty() && !additionalItem.isEmpty();
            if (!flag1) {
                ItemStack itemstack = !inputItem.isEmpty() ? inputItem : additionalItem;
                return !EnchantmentHelper.hasAnyEnchantments(itemstack) ? ItemStack.EMPTY : this.beyond$removeNonCursesFrom(itemstack.copy());
            } else {
                return this.beyond$mergeItems(inputItem, additionalItem);
            }
        } else {
            return ItemStack.EMPTY;
        }
    }

    // 合并两件同类物品：耐久相加 + 最大耐久 5% 加成，合并附魔并去诅咒
    private ItemStack beyond$mergeItems(ItemStack inputItem, ItemStack additionalItem) {
        if (!inputItem.is(additionalItem.getItem())) {
            return ItemStack.EMPTY;
        } else {
            int i = Math.max(inputItem.getMaxDamage(), additionalItem.getMaxDamage());
            int j = inputItem.getMaxDamage() - inputItem.getDamageValue();
            int k = additionalItem.getMaxDamage() - additionalItem.getDamageValue();
            int l = j + k + i * 5 / 100;
            int i1 = 1;
            if (!inputItem.isDamageableItem() || !inputItem.isRepairable()) {
                if (inputItem.getMaxStackSize() < 2 || !ItemStack.matches(inputItem, additionalItem)) {
                    return ItemStack.EMPTY;
                }
                i1 = 2;
            }
            ItemStack itemstack = inputItem.copyWithCount(i1);
            if (itemstack.isDamageableItem()) {
                itemstack.set(DataComponents.MAX_DAMAGE, i);
                itemstack.setDamageValue(Math.max(i - l, 0));
                if (!additionalItem.isRepairable()) itemstack.setDamageValue(inputItem.getDamageValue());
            }
            this.beyond$mergeEnchantsFrom(itemstack, additionalItem);
            return this.beyond$removeNonCursesFrom(itemstack);
        }
    }

    // 合并附加槽附魔：诅咒附魔仅在主物品未含时才保留
    private void beyond$mergeEnchantsFrom(ItemStack inputItem, ItemStack additionalItem) {
        EnchantmentHelper.updateEnchantments(inputItem, p_344370_ -> {
            ItemEnchantments itemenchantments = EnchantmentHelper.getEnchantmentsForCrafting(additionalItem);
            for (Object2IntMap.Entry<Holder<Enchantment>> entry : itemenchantments.entrySet()) {
                Holder<Enchantment> holder = entry.getKey();
                if (!holder.is(net.minecraft.tags.EnchantmentTags.CURSE) || p_344370_.getLevel(holder) == 0) {
                    p_344370_.upgrade(holder, entry.getIntValue());
                }
            }
        });
    }

    // 移除除诅咒外的全部附魔；附魔书转普通书；重算修理费用（每附魔 +1 次递增）
    private ItemStack beyond$removeNonCursesFrom(ItemStack item) {
        ItemEnchantments itemenchantments = EnchantmentHelper.updateEnchantments(
                item, p_330066_ -> p_330066_.removeIf(p_344368_ -> !p_344368_.is(net.minecraft.tags.EnchantmentTags.CURSE))
        );
        if (item.is(Items.ENCHANTED_BOOK) && itemenchantments.isEmpty()) {
            item = item.transmuteCopy(Items.BOOK);
        }
        int i = 0;
        for (int j = 0; j < itemenchantments.size(); j++) {
            i = DimensionsAnvilMenu.beyond$calculateIncreasedRepairCost(i);
        }
        item.set(DataComponents.REPAIR_COST, i);
        return item;
    }

    // 本次取走可返还的经验：物品附魔基础开销之和 /2 再加随机（对齐原版）
    private int beyond$getExperienceAmount(Level level) {
        if (beyond$xp > -1) return beyond$xp;
        int l = 0;
        l += this.beyond$getExperienceFromItem(this.beyond$repairSlots.getItem(0));
        l += this.beyond$getExperienceFromItem(this.beyond$repairSlots.getItem(1));
        if (l > 0) {
            int i1 = (int) Math.ceil((double) l / 2.0);
            return i1 + level.random.nextInt(i1);
        } else {
            return 0;
        }
    }

    // 计算单件物品的附魔基础经验开销（诅咒不计）
    private int beyond$getExperienceFromItem(ItemStack stack) {
        int l = 0;
        ItemEnchantments itemenchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : itemenchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            int i1 = entry.getIntValue();
            if (!holder.is(net.minecraft.tags.EnchantmentTags.CURSE)) {
                l += holder.value().getMinCost(i1);
            }
        }
        return l;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        if (slotIndex == beyond$wsS + WS_OUTPUT) {
            int beforeCount = stack.getCount();
            moveStackTo(stack, inventoryStartIndex, inventoryEndIndex, true);
            if (!stack.isEmpty()) {
                DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
                if (net != null) { long remaining = net.getUnifiedStorage().insert(new ItemStackKey(stack), stack.getCount(), false).amount(); stack.setCount((int) remaining); }
            }
            if (stack.getCount() < beforeCount) {
                // 材料清空/经验球由 onTake 完成，不可预清空输入槽
                slot.onQuickCraft(result, stack);
                slot.onTake(player, result);
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

    // 关闭菜单：归还输入/附加槽物品（结果槽直接丢弃，对齐原版）
    @Override
    public void removed(@NotNull Player p) {
        super.removed(p);
        if (p.level().isClientSide()) return;
        cleanSlots(firstCraftReturnDir);
    }

    @Override
    public void cleanSlots(boolean toStorage) {
        cleanSlotsFromContainer(toStorage, beyond$repairSlots, new int[]{0, 1});
        // 结果槽对齐原版 GrindstoneMenu.removed：关闭时直接丢弃，不归还
        beyond$resultSlots.setItem(0, ItemStack.EMPTY);
    }
}
