package com.solr98.beyondintegration.feature.crafting;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.init.ModMenus;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.UnorderedStackHandlerRemoveZero;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.common.Tags;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 原版增强版附魔台菜单（1.20.1；参考原版 EnchantmentMenu 移植，私有符号 beyond$ 前缀）：
 * 继承网络存储菜单；附魔功率由配置决定（FIXED/NETWORK_XP/PLAYER_LEVEL，可解限>15）。
 * 增强/解限：ignoreEnchanted(已附魔再附+升级覆盖)、ignoreConflict、noLapis、levelGateIgnore(点数制)、
 * uncapPower、costPercent、allowTreasure(#treasure 并入)；刷新=局部随机偏移重掷三槽；预览=服务端下发"将获得"列表。
 * 神化(Apothic)模式不在 1.20.1 移植范围。
 */
public class DimensionsEnchantMenu extends DimensionsStorageMenu implements ICleanableWorkstation {
    // 工作台槽位坐标：物品 (15,42)、燃料 (35,42)
    private static final int[] WX = {15, 35};
    private static final int WY = 42;

    // 三槽费用/谜名（DataSlot 同步）
    public final int[] costs = new int[3];
    public final int[] enchantClue = new int[]{-1, -1, -1};
    public final int[] levelClue = new int[]{-1, -1, -1};

    protected final Container beyond$enchantSlots;  // 2 格输入
    protected final RandomSource beyond$random = RandomSource.create();
    protected final DataSlot beyond$enchantmentSeed = DataSlot.standalone();
    protected int beyond$wsS = -1;
    protected int beyond$refreshTick = 0; // 刷新局部偏移（不影响玩家附魔种子）
    // 服务端功能开关（DataSlot → 客户端镜像，决定按钮/预览显示）
    public boolean serverPreviewEnabled;
    public boolean serverRefreshEnabled;
    public int serverRefreshLapis;

    // 客户端构造
    public DimensionsEnchantMenu(int id, Inventory inv, FriendlyByteBuf b) {
        this(ModMenus.ENCHANT.get(), id, inv,
                new UnorderedStackHandlerRemoveZero(AbstractUnorderedStackHandler.UiTimestampPolicy.NONE));
    }

    // 主构造
    public DimensionsEnchantMenu(MenuType<?> t, int id, Inventory inv, AbstractUnorderedStackHandler d) {
        super(t, id, inv, d);
        this.beyond$enchantSlots = new SimpleContainer(2) {
            @Override public void setChanged() {
                super.setChanged();
                DimensionsEnchantMenu.this.slotsChanged(this);
            }
        };
        beyond$wsS = slots.size();
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            addSlot(new Slot(beyond$enchantSlots, i, WX[i], ey(WY)) {
                @Override public int getMaxStackSize() { return idx == 0 ? 1 : 64; }
                @Override public boolean mayPlace(ItemStack s) {
                    // 1.20.1 原版语义：燃料槽 = #forge:enchanting_fuels
                    if (idx == 1) return s.is(net.minecraftforge.common.Tags.Items.ENCHANTING_FUELS);
                    return true;
                }
            });
            customSlotIndices.add(slots.size() - 1);
        }
        addDataSlot(DataSlot.shared(this.costs, 0));
        addDataSlot(DataSlot.shared(this.costs, 1));
        addDataSlot(DataSlot.shared(this.costs, 2));
        this.addDataSlot(this.beyond$enchantmentSeed).set(player.getEnchantmentSeed());
        addDataSlot(DataSlot.shared(this.enchantClue, 0));
        addDataSlot(DataSlot.shared(this.enchantClue, 1));
        addDataSlot(DataSlot.shared(this.enchantClue, 2));
        addDataSlot(DataSlot.shared(this.levelClue, 0));
        addDataSlot(DataSlot.shared(this.levelClue, 1));
        addDataSlot(DataSlot.shared(this.levelClue, 2));
        addDataSlot(new DataSlot() {
            @Override public int get() { return serverPreviewEnabled ? 1 : 0; }
            @Override public void set(int v) { serverPreviewEnabled = v != 0; }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return serverRefreshEnabled ? 1 : 0; }
            @Override public void set(int v) { serverRefreshEnabled = v != 0; }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return serverRefreshLapis; }
            @Override public void set(int v) { serverRefreshLapis = v; }
        });
        // 服务端初始化开关
        if (!player.level().isClientSide()) {
            this.serverPreviewEnabled = CommandConfig.enchantPreviewEnabled();
            this.serverRefreshEnabled = CommandConfig.enchantRefreshEnabled();
            this.serverRefreshLapis = CommandConfig.enchantRefreshLapis();
            // 打开界面时自动从主网络填充燃料（尽可能补到 64）
            beyond$autoFillFuel();
        }
    }

    /** 自动填充燃料期间抑制 slotsChanged 的重算/广播（构造阶段界面尚未打开） */
    private boolean beyond$fillingFuel = false;

    /**
     * 打开界面时自动从主网络填充燃料槽：已有燃料则补足到 64，空槽则取网络中
     * 第一种可作燃料的物品（#forge:enchanting_fuels）至多 64 个。
     * 仅在服务端菜单构造（玩家打开界面）时调用。
     */
    private void beyond$autoFillFuel() {
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) return;
        var storage = net.getUnifiedStorage();
        this.beyond$fillingFuel = true;
        try {
            ItemStack fuel = this.beyond$enchantSlots.getItem(1);
            if (!fuel.isEmpty()) {
                int need = 64 - fuel.getCount();
                if (need > 0) {
                    long got = storage.extract(new com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey(fuel), need, false, false).amount();
                    if (got > 0) { fuel.grow((int) got); this.beyond$enchantSlots.setItem(1, fuel); }
                }
                return;
            }
            com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey candidate = null;
            for (var ka : storage.getStorage()) {
                if (ka.key() instanceof com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey ik) {
                    ItemStack s = ik.getReadOnlyStack();
                    if (!s.isEmpty() && s.is(Tags.Items.ENCHANTING_FUELS)) { candidate = ik; break; }
                }
            }
            if (candidate != null) {
                long got = storage.extract(candidate, 64, false, false).amount();
                if (got > 0) this.beyond$enchantSlots.setItem(1, candidate.copyStackWithCount(got));
            }
        } finally {
            this.beyond$fillingFuel = false;
        }
    }

    /** 是否允许"已附魔物品继续附魔"（含附魔书） */
    private static boolean beyond$enchantedAllowed(ItemStack s) {
        return CommandConfig.enchantIgnoreEnchanted() && !EnchantmentHelper.getEnchantments(s).isEmpty();
    }

    /**
     * 可进入计算判定（照搬原版可附魔判定 Item#isEnchantable）：
     * 物品自身必须能够附魔才能作为可附魔物品；
     * 未附魔物品走原版语义，已附魔物品仅在 enchantIgnoreEnchanted 开启时放行（升级/覆盖）。
     */
    protected boolean beyond$canEnchant(ItemStack s) {
        if (s.isEmpty()) return false;
        // 原版判定：物品自身必须能够附魔（Item#isEnchantable，含 BookItem 等物品覆写）
        if (!s.getItem().isEnchantable(s)) return false;
        // 未附魔：原版语义（ItemStack#isEnchantable 的 !isEnchanted() 分支）
        if (!s.isEnchanted()) return true;
        // 已附魔：仅宽松配置允许（交由宽松选魔处理升级/覆盖）
        return beyond$enchantedAllowed(s);
    }

    protected int beyond$powerCap() { return CommandConfig.enchantUncapPower() ? 100 : 15; }

    /** 附魔功率来源（配置） */
    protected int beyond$power() {
        int cap = beyond$powerCap();
        return switch (CommandConfig.enchantPowerMode()) {
            case FIXED -> Math.min(cap, Math.max(0, CommandConfig.enchantFixedPower()));
            case NETWORK_XP -> {
                long xp = 0;
                DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
                if (net != null) xp = net.getUnifiedStorage().extract(com.solr98.beyondintegration.feature.enchant.EnchantmentBookSeparatorHandler.xpFluidKey(), Long.MAX_VALUE, true, false).amount();
                yield (int) Math.min(cap, xp / Math.max(1, CommandConfig.enchantXpPerPower()));
            }
            case PLAYER_LEVEL -> (int) Math.min(cap, Math.max(0, player.experienceLevel) / Math.max(1, CommandConfig.enchantLevelPerPower()));
        };
    }

    // 原版费用公式（解限时自实现，避免 EnchantmentHelper 内 15 钳制）
    private static int beyond$vanillaCost(RandomSource rnd, int slot, int power, ItemStack stack) {
        int ench = stack.getEnchantmentValue();
        if (ench <= 0) return 0;
        int j = rnd.nextInt(8) + 1 + (power >> 1) + rnd.nextInt(power + 1);
        return switch (slot) {
            case 0 -> Math.max(j / 3, 1);
            case 1 -> j * 2 / 3 + 1;
            default -> Math.max(j, power * 2);
        };
    }

    /** 服务端重算：费用 + 谜名 + 自动下发预览 */
    private void beyond$recalc(ItemStack item) {
        int power = beyond$power();
        boolean uncapped = CommandConfig.enchantUncapPower() && power > 15;
        this.beyond$random.setSeed((long) (this.beyond$enchantmentSeed.get() + beyond$refreshTick));
        for (int k = 0; k < 3; k++) {
            this.costs[k] = uncapped
                    ? beyond$vanillaCost(this.beyond$random, k, power, item)
                    : EnchantmentHelper.getEnchantmentCost(this.beyond$random, k, power, item);
            this.enchantClue[k] = -1;
            this.levelClue[k] = -1;
            if (this.costs[k] < k + 1) this.costs[k] = 0;
            this.costs[k] = net.minecraftforge.event.ForgeEventFactory.onEnchantmentLevelSet(player.level(), player.blockPosition(), k, power, item, this.costs[k]);
        }
        for (int l = 0; l < 3; l++) {
            List<EnchantmentInstance> list = List.of();
            if (this.costs[l] > 0) {
                list = beyond$selectList(item, l, this.costs[l]);
                if (!list.isEmpty()) {
                    EnchantmentInstance main = list.get(this.beyond$random.nextInt(list.size()));
                    this.enchantClue[l] = BuiltInRegistries.ENCHANTMENT.getId(main.enchantment);
                    this.levelClue[l] = main.level;
                }
            }
            beyond$sendPreview(l, list);
        }
    }

    @Override public void slotsChanged(Container inv) {
        super.slotsChanged(inv);
        if (player.level().isClientSide()) return; // 费用计算仅在服务端（客户端靠 DataSlot）
        if (beyond$fillingFuel) return; // 自动填充燃料：构造阶段跳过重算/广播
        if (inv == this.beyond$enchantSlots) {
            ItemStack item = inv.getItem(0);
            if (beyond$canEnchant(item)) {
                beyond$recalc(item);
                this.broadcastChanges();
            } else {
                for (int i = 0; i < 3; i++) { this.costs[i] = 0; this.enchantClue[i] = -1; this.levelClue[i] = -1; }
                for (int i = 0; i < 3; i++) beyond$sendPreview(i, List.of()); // 清客户端旧预览
            }
        }
    }

    /** 下发单槽"将获得"完整列表（客户端预览/升级标记；关闭时发空） */
    protected void beyond$sendPreview(int slot, List<EnchantmentInstance> full) {
        if (!(player instanceof ServerPlayer sp)) return;
        List<EnchantmentInstance> send = serverPreviewEnabled ? new ArrayList<>(full) : List.of();
        com.solr98.beyondintegration.network.PacketHandler.sendToPlayer(sp, new com.solr98.beyondintegration.network.EnchantCluesPacket(containerId, slot, send));
    }

    /** 单槽选魔：宽松（ignoreEnchanted）走自定义池（升级/冲突/宝藏），否则原版 selectEnchantment(allowTreasure) */
    private List<EnchantmentInstance> beyond$selectList(ItemStack item, int slot, int cost) {
        this.beyond$random.setSeed((long) (this.beyond$enchantmentSeed.get() + slot + beyond$refreshTick));
        if (!beyond$enchantedAllowed(item)) {
            // 未启用宽松：原版机制（允许宝藏则由配置控制）
            return EnchantmentHelper.selectEnchantment(this.beyond$random, item, cost, CommandConfig.enchantAllowTreasure());
        }
        return beyond$selectLoose(item, cost, CommandConfig.enchantAllowTreasure());
    }

    /** 宽松选魔：已附魔物品可升级已有附魔（低→高覆盖）、附加新兼容项；冲突需 ignoreConflict */
    private List<EnchantmentInstance> beyond$selectLoose(ItemStack stack, int cost, boolean allowTreasure) {
        List<EnchantmentInstance> chosen = new ArrayList<>();
        int i = stack.getEnchantmentValue();
        if (i <= 0) i = 15; // 附魔书等
        int level = cost;
        level += 1 + this.beyond$random.nextInt(i / 4 + 1) + this.beyond$random.nextInt(i / 4 + 1);
        float f = (this.beyond$random.nextFloat() + this.beyond$random.nextFloat() - 1.0F) * 0.15F;
        level = Mth.clamp(Math.round(level + level * f), 1, Integer.MAX_VALUE);
        Map<Enchantment, Integer> existing = EnchantmentHelper.getEnchantments(stack);
        boolean ignoreConflict = CommandConfig.enchantIgnoreConflict();
        boolean bookish = stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK);
        List<EnchantmentInstance> pool = new ArrayList<>();
        for (Enchantment ench : BuiltInRegistries.ENCHANTMENT) {
            if ((ench.isTreasureOnly() && !allowTreasure) || !ench.isDiscoverable()) continue;
            boolean apply = ench.canApplyAtEnchantingTable(stack) || (bookish && ench.isAllowedOnBooks());
            if (!apply) continue;
            int cur = existing.getOrDefault(ench, 0);
            if (cur > 0 && ench.getMaxLevel() <= cur) continue; // 无更高可升
            if (cur == 0 && !ignoreConflict) {
                boolean conflict = false;
                for (Enchantment ex : existing.keySet()) {
                    if (!ench.isCompatibleWith(ex)) { conflict = true; break; }
                }
                if (conflict) continue;
            }
            for (int lvl = ench.getMaxLevel(); lvl >= ench.getMinLevel(); lvl--) {
                if (lvl <= cur) break;
                if (level >= ench.getMinCost(lvl) && level <= ench.getMaxCost(lvl)) {
                    pool.add(new EnchantmentInstance(ench, lvl));
                    break;
                }
            }
        }
        if (pool.isEmpty()) return chosen;
        WeightedRandom.getRandomItem(this.beyond$random, pool).ifPresent(chosen::add);
        while (this.beyond$random.nextInt(50) <= level && !pool.isEmpty()) {
            if (!chosen.isEmpty()) EnchantmentHelper.filterCompatibleEnchantments(pool, chosen.get(chosen.size() - 1));
            if (pool.isEmpty()) break;
            WeightedRandom.getRandomItem(this.beyond$random, pool).ifPresent(chosen::add);
            level /= 2;
        }
        return chosen;
    }

    // 应用附魔结果（copy 原版 1.20.1 clickMenuButton 内逻辑：书→附魔书）
    private ItemStack beyond$apply(ItemStack in, List<EnchantmentInstance> list) {
        ItemStack out = in;
        boolean book = in.is(Items.BOOK);
        if (book) {
            out = new ItemStack(Items.ENCHANTED_BOOK);
            if (in.getTag() != null) out.setTag(in.getTag().copy());
        }
        for (EnchantmentInstance ei : list) {
            if (book) EnchantedBookItem.addEnchantment(out, ei);
            else out.enchant(ei.enchantment, ei.level);
        }
        return out;
    }

    /** 执行钩子：服务端执行附魔 */
    protected void beyond$doEnchant(Player player, int id, int lapisNeed, ItemStack item, ItemStack fuel) {
        List<EnchantmentInstance> list = beyond$selectList(item, id, this.costs[id]);
        if (list.isEmpty()) return;
        boolean noLapis = CommandConfig.enchantNoLapis();
        boolean gateIgnore = CommandConfig.enchantLevelGateIgnore();
        boolean creative = player.getAbilities().instabuild;
        if (!creative && gateIgnore) {
            // 点数制（网络 XP 优先+玩家点数补足）；失败则中止
            int pts = beyond$scaleCost((int) beyond$xpTotalCost(this.costs[id]));
            if (!beyond$chargePoints((ServerPlayer) player, pts)) return;
            player.onEnchantmentPerformed(item, 0);
        } else {
            player.onEnchantmentPerformed(item, lapisNeed);
        }
        ItemStack enchanted = beyond$apply(item, list);
        this.beyond$enchantSlots.setItem(0, enchanted);
        if (!noLapis && !creative) {
            fuel.shrink(lapisNeed);
            if (fuel.isEmpty()) this.beyond$enchantSlots.setItem(1, ItemStack.EMPTY);
        }
        player.awardStat(Stats.ENCHANT_ITEM);
        if (player instanceof ServerPlayer sp) {
            net.minecraft.advancements.CriteriaTriggers.ENCHANTED_ITEM.trigger(sp, enchanted, lapisNeed);
        }
        this.beyond$enchantSlots.setChanged();
        this.beyond$enchantmentSeed.set(player.getEnchantmentSeed());
        this.slotsChanged(this.beyond$enchantSlots);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F,
                player.level().random.nextFloat() * 0.1F + 0.9F);
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (id >= 0 && id < this.costs.length) {
            ItemStack item = this.beyond$enchantSlots.getItem(0);
            ItemStack fuel = this.beyond$enchantSlots.getItem(1);
            int i = id + 1;
            boolean noLapis = CommandConfig.enchantNoLapis();
            boolean gateIgnore = CommandConfig.enchantLevelGateIgnore();
            if ((!noLapis && (fuel.isEmpty() || fuel.getCount() < i)) && !player.getAbilities().instabuild) return false;
            if (this.costs[id] <= 0 || item.isEmpty()) return false;
            if (!player.getAbilities().instabuild && !gateIgnore
                    && (player.experienceLevel < i || player.experienceLevel < this.costs[id])) return false;
            if (player.level().isClientSide()) return true;
            beyond$doEnchant(player, id, i, item, fuel);
            return true;
        }
        return false;
    }

    /** 刷新：消耗燃料后递增局部偏移重算（由 RefreshEnchantPacket 触发） */
    public void doRefresh(ServerPlayer sp) {
        if (sp.level().isClientSide()) return;
        if (!serverRefreshEnabled) return;
        boolean noLapis = CommandConfig.enchantNoLapis();
        boolean creative = sp.getAbilities().instabuild;
        int need = CommandConfig.enchantRefreshLapis();
        ItemStack fuel = this.beyond$enchantSlots.getItem(1);
        if (!noLapis && !creative && need > 0 && (fuel.isEmpty() || fuel.getCount() < need)) {
            sp.sendSystemMessage(Component.translatable("gui.beyond_integration.enchant.refresh_need", need));
            return;
        }
        if (!noLapis && !creative && need > 0) {
            fuel.shrink(need);
            if (fuel.isEmpty()) this.beyond$enchantSlots.setItem(1, ItemStack.EMPTY);
        }
        beyond$refreshTick++;
        this.slotsChanged(this.beyond$enchantSlots);
        this.broadcastChanges();
    }

    // ---- 经验点数扣费（网络 XP 优先+玩家补足；anvil 同款换算）----
    // 原版等级→点数公式（对齐 Player.getXpNeededForNextLevel；1.20.1 与 1.21.1 相同）
    private static int beyond$xpNeededForLevel(int level) {
        if (level >= 30) return 112 + (level - 30) * 9;
        if (level >= 15) return 37 + (level - 15) * 5;
        return 7 + level * 2;
    }

    private static long beyond$xpTotalCost(int level) {
        long sum = 0;
        for (int l = 0; l < level; l++) sum += beyond$xpNeededForLevel(l);
        return sum;
    }

    private static int beyond$scaleCost(int pts) {
        return Math.max(1, (int) Math.round(pts * CommandConfig.enchantCostPercent() / 100.0));
    }

    private static com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey beyond$xpFluidKey() {
        return com.solr98.beyondintegration.feature.enchant.EnchantmentBookSeparatorHandler.xpFluidKey();
    }

    private boolean beyond$chargePoints(ServerPlayer sp, int pts) {
        if (pts <= 0) return true;
        long mb = (long) pts * 20L;
        long got = 0;
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(sp);
        if (net != null) got = net.getUnifiedStorage().extract(beyond$xpFluidKey(), mb, false, false).amount();
        long missingPts = Math.max(0, (mb - got) / 20L);
        if (missingPts == 0) return true;
        if (sp.totalExperience < missingPts) {
            sp.sendSystemMessage(Component.translatable("gui.beyond_integration.enchant.need_xp_points", pts));
            return false;
        }
        sp.sendSystemMessage(Component.translatable("gui.beyond_integration.enchant.xp_paid_from_player", missingPts));
        sp.giveExperiencePoints(-(int) Math.min(missingPts, Integer.MAX_VALUE));
        return true;
    }

    // ---- BI 公开 API（GUI）----
    public ItemStack getItemInput() { return beyond$enchantSlots.getItem(0); }
    public int getGoldCount() { ItemStack s = beyond$enchantSlots.getItem(1); return s.isEmpty() ? 0 : s.getCount(); }
    public int getEnchantmentSeed() { return this.beyond$enchantmentSeed.get(); }
    @Override public int getPanelHeight() { return 76; }

    @Override public void rebuildSlots() {
        super.rebuildSlots();
        if (beyond$wsS >= 0) { int y = ey(WY); for (int i = 0; i < 2; i++) { setSlotX(slots.get(beyond$wsS + i), WX[i]); setSlotY(slots.get(beyond$wsS + i), y); } }
    }

    @Override public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        if (slotIndex >= beyond$wsS && slotIndex < beyond$wsS + 2) {
            if (!moveStackTo(stack, inventoryStartIndex, inventoryEndIndex, true)) return ItemStack.EMPTY;
            if (!stack.isEmpty()) {
                DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
                if (net != null) {
                    long remaining = net.getUnifiedStorage().insert(new com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey(stack), stack.getCount(), false).amount();
                    stack.setCount((int) remaining);
                }
            }
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
            return result;
        }
        if (slotIndex >= inventoryStartIndex && slotIndex < inventoryEndIndex) {
            for (int i = 0; i < 2 && !stack.isEmpty(); i++) {
                Slot target = this.slots.get(beyond$wsS + i);
                if (!target.mayPlace(stack)) continue;
                ItemStack ts = target.getItem();
                if (ts.isEmpty()) { int n = Math.min(stack.getCount(), target.getMaxStackSize(stack)); target.set(stack.split(n)); target.setChanged(); }
                else if (ItemStack.isSameItemSameTags(stack, ts)) { int space = target.getMaxStackSize(stack) - ts.getCount(); if (space > 0) { int n = Math.min(stack.getCount(), space); ts.grow(n); stack.shrink(n); target.set(ts); target.setChanged(); } }
            }
            if (stack.isEmpty()) { slot.setChanged(); return result; }
        }
        return super.quickMoveStack(player, slotIndex);
    }

    @Override public void removed(@NotNull Player p) {
        super.removed(p);
        if (p.level().isClientSide()) return;
        // 关闭时：自动填充的燃料存回网络；待附魔物品按原归还方向清理（避免丢失）
        cleanSlotsFromContainer(true, beyond$enchantSlots, new int[]{1});
        cleanSlotsFromContainer(firstCraftReturnDir, beyond$enchantSlots, new int[]{0});
    }

    @Override public void cleanSlots(boolean toStorage) {
        cleanSlotsFromContainer(toStorage, beyond$enchantSlots, new int[]{0, 1});
    }
}