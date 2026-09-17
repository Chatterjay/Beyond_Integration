package com.solr98.beyondintegration.init;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.UnorderedStackHandlerRemoveZero;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.IdMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 原版模式附魔台菜单（copy 自原版 EnchantmentMenu(1.21.1) 改造，私有符号统一 beyond$ 前缀避免冲突）：
 * 继承网络存储菜单；附魔功率（书架加成）无物理方块，来源由配置决定
 * （FIXED 固定值 / NETWORK_XP 网络经验流体 / PLAYER_LEVEL 玩家等级，见 CommandConfig "enchant" 段）。
 * 行为 = 原版语义：EnchantmentHelper 费用/选魔、等级门槛、扣 slot+1 级。
 * 神化模式由子类 {@link DimensionsApothEnchantMenu} 覆写计算/扣费钩子独立实现。
 * 槽位与青金石消耗保持原版语义，关闭时按方向归还网络/背包；快捷移动适配网络存储。
 */
public class DimensionsEnchantMenu extends DimensionsStorageMenu implements ICleanableWorkstation {
    // 工作台槽位坐标：物品槽 (15,42)、燃料槽 (35,42)；面板贴图自原版 (0,4) 起剪（槽位背景在原版 47 → 贴图内 43），
    // 实测贴图槽位背景上移 2px → 41；后再向下微调 1px → 42
    private static final int[] WX = {15, 35};
    private static final int WY = 42;

    // ---- copy 原版 EnchantmentMenu 公共状态（费用/附魔预订，DataSlot 同步到客户端）----
    public final int[] costs = new int[3];
    public final int[] enchantClue = new int[]{-1, -1, -1};
    public final int[] levelClue = new int[]{-1, -1, -1};

    // ---- copy 原版 EnchantmentMenu 私有状态（beyond$ 前缀；子类可访问）----
    protected final Container beyond$enchantSlots;          // 输入槽容器（2 格：物品 + 燃料）
    protected final Level beyond$level;                     // 服务端世界引用（客户端为 null，不执行附魔计算）
    protected final RandomSource beyond$random = RandomSource.create();
    protected final DataSlot beyond$enchantmentSeed = DataSlot.standalone();
    protected int beyond$wsS = -1;                          // 本菜单自加槽位的起始索引
    /** 刷新计数：局部随机偏移（服务端重掷三槽用；不影响玩家附魔种子） */
    protected int beyond$refreshTick = 0;
    // ---- 服务端功能开关（DataSlot 同步客户端：决定按钮/预览是否显示）----
    public boolean serverPreviewEnabled;
    public boolean serverRefreshEnabled;
    public int serverRefreshLapis; // 刷新费用（客户端 tooltip 显示）

    // 客户端构造：由网络包创建（无世界上下文，附魔计算纯靠 DataSlot 同步）
    public DimensionsEnchantMenu(int id, Inventory inv, FriendlyByteBuf b) {
        this(ModMenus.ENCHANT.get(), id, inv,
                new UnorderedStackHandlerRemoveZero(AbstractUnorderedStackHandler.UiTimestampPolicy.NONE), null);
    }

    // 主构造：注册输入槽与费用/附魔预订 DataSlot；附魔计算仅在服务端（beyond$level != null）执行
    public DimensionsEnchantMenu(MenuType<?> t, int id, Inventory inv, AbstractUnorderedStackHandler d, Level level) {
        super(t, id, inv, d);
        this.beyond$level = level;
        // copy 原版：输入槽变化 → slotsChanged → 重新计算附魔
        this.beyond$enchantSlots = new SimpleContainer(2) {
            @Override
            public void setChanged() {
                super.setChanged();
                DimensionsEnchantMenu.this.slotsChanged(this);
            }
        };
        beyond$wsS = slots.size();
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            addSlot(new Slot(beyond$enchantSlots, i, WX[i], ey(WY)) {
                @Override public int getMaxStackSize() { return idx == 0 ? 1 : 64; } // 物品槽单数（对齐原版）
                @Override public boolean mayPlace(ItemStack s) {
                    if (idx == 1) {
                        // 燃料槽：神化模式（子类）接受 #c:enchanting_fuels（对齐 Apoth），原版模式仅青金石
                        if (DimensionsEnchantMenu.this.isApothMode()) return s.is(net.neoforged.neoforge.common.Tags.Items.ENCHANTING_FUELS);
                        return s.is(Items.LAPIS_LAZULI);
                    }
                    return true;
                }
            });
            customSlotIndices.add(slots.size() - 1);
        }
        // 费用三项 + 随机种子 + 附魔预订六项（copy 原版 EnchantmentMenu 构造的 DataSlot 注册）
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
        // 服务端：按配置初始化功能开关（客户端镜像随后经 DataSlot 同步）
        if (level != null) {
            this.serverPreviewEnabled = CommandConfig.enchantPreviewEnabled();
            this.serverRefreshEnabled = CommandConfig.enchantRefreshEnabled();
            this.serverRefreshLapis = CommandConfig.enchantRefreshLapis();
            // 打开界面时自动从主网络填充燃料（尽可能补到 64）
            beyond$autoFillFuel();
        }
    }

    /** 自动填充燃料期间抑制 slotsChanged 的重算/广播（构造阶段界面尚未打开） */
    private boolean beyond$fillingFuel = false;

    /** 当前模式的燃料判定（与燃料槽 mayPlace 一致：原版=青金石；神化=#c:enchanting_fuels） */
    private boolean beyond$isFuel(ItemStack s) {
        if (isApothMode()) return s.is(net.neoforged.neoforge.common.Tags.Items.ENCHANTING_FUELS);
        return s.is(Items.LAPIS_LAZULI);
    }

    /**
     * 打开界面时自动从主网络填充燃料槽：已有燃料则补足到 64，空槽则取网络中
     * 第一种可作燃料的物品至多 64 个。仅在服务端菜单构造（玩家打开界面）时调用。
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
                    if (!s.isEmpty() && beyond$isFuel(s)) { candidate = ik; break; }
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

    // ---- 模式（类级多态）：原版 false，神化子类覆写 true ----
    public boolean isApothMode() { return false; }

    /** 是否允许"已附魔物品继续附魔"（enchantIgnoreEnchanted 配置；宽松选魔据此决定升级/覆盖） */
    protected static boolean beyond$enchantedAllowed(ItemStack stack) {
        return CommandConfig.enchantIgnoreEnchanted() && stack.isEnchanted();
    }

    /**
     * 物品是否可放入附魔计算（照搬原版可附魔判定 Item#isEnchantable）：
     * 物品自身必须能够附魔才能作为可附魔物品；
     * 未附魔物品走原版语义，已附魔物品仅在 enchantIgnoreEnchanted 开启时放行，
     * 交由宽松选魔决定可附加的附魔（升级/新兼容/可选冲突）。
     */
    protected boolean beyond$canEnchant(ItemStack stack) {
        if (stack.isEmpty()) return false;
        // 原版判定：物品自身必须能够附魔（Item#isEnchantable，含 BookItem 等物品覆写）
        if (!stack.getItem().isEnchantable(stack)) return false;
        // 未附魔：原版语义（ItemStack#isEnchantable 的 !isEnchanted() 分支）
        if (!stack.isEnchanted()) return true;
        // 已附魔：仅宽松配置允许（交由宽松选魔处理升级/覆盖）
        return beyond$enchantedAllowed(stack);
    }

    /** 功率上限：默认 15（原版）；enchantUncapPower 开启后最高 100（神化子类恒 100） */
    protected int beyond$powerCap() {
        return CommandConfig.enchantUncapPower() ? 100 : 15;
    }

    // ---- 附魔功率来源（配置决定，替代原版书架检测）----
    protected int beyond$power() {
        int cap = beyond$powerCap();
        return switch (CommandConfig.enchantPowerMode()) {
            case FIXED -> Math.min(cap, Math.max(0, CommandConfig.enchantFixedPower()));
            case NETWORK_XP -> {
                long xp = 0;
                DimensionsNet net = com.solr98.beyondintegration.handler.MenuNetIdHelper.getNetFromMenu((ServerPlayer) player);
                if (net == null) net = DimensionsNet.getPrimaryNetFromPlayer(player);
                if (net != null) xp = net.getUnifiedStorage().extract(com.solr98.beyondintegration.handler.EnchantmentBookSeparatorHandler.xpFluidKey(), Long.MAX_VALUE, true, false).amount();
                yield (int) Math.min(cap, xp / Math.max(1, CommandConfig.enchantXpPerPower()));
            }
            case PLAYER_LEVEL -> (int) Math.min(cap, Mth.clamp(player.experienceLevel, 0, Integer.MAX_VALUE) / Math.max(1, CommandConfig.enchantLevelPerPower()));
        };
    }

    // 原版费用公式（解限时自行实现，避免 EnchantmentHelper 内部钳制 15）
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

    // 附魔池：对齐原版 getEnchantmentList（seed + slot + 刷新偏移 定序）；
    // enchantAllowTreasure 开启时并入 #treasure（宝藏附魔，如经验修补/冰霜行者）
    protected List<EnchantmentInstance> beyond$getEnchantmentList(RegistryAccess registryAccess, ItemStack stack, int slot, int cost) {
        this.beyond$random.setSeed((long) (this.beyond$enchantmentSeed.get() + slot + beyond$refreshTick));
        Optional<HolderSet.Named<Enchantment>> optional = registryAccess.registryOrThrow(Registries.ENCHANTMENT).getTag(EnchantmentTags.IN_ENCHANTING_TABLE);
        if (optional.isEmpty()) {
            return List.of();
        }
        java.util.stream.Stream<Holder<Enchantment>> possible = optional.get().stream();
        if (CommandConfig.enchantAllowTreasure()) {
            var treasure = registryAccess.registryOrThrow(Registries.ENCHANTMENT).getTag(EnchantmentTags.TREASURE);
            possible = java.util.stream.Stream.concat(possible, treasure.map(HolderSet.Named::stream).orElseGet(java.util.stream.Stream::of));
        }
        if (CommandConfig.enchantIgnoreEnchanted() && stack.isEnchanted()) {
            // 宽松选魔（ignoreEnchanted）：已附魔物品继续附魔——
            //  1) 已有附魔可升级到更高等级（低级被高级覆盖）；
            //  2) 可附加新的非冲突附魔；
            //  3) 与已有冲突的条目仅当 enchantIgnoreConflict 开启时允许。
            return beyond$selectLoose(registryAccess, stack, cost, possible);
        } else {
            List<EnchantmentInstance> list = EnchantmentHelper.selectEnchantment(this.beyond$random, stack, cost, possible);
            if (stack.is(Items.BOOK) && list.size() > 1) {
                list.remove(this.beyond$random.nextInt(list.size()));
            }
            return list;
        }
    }

    // 宽松选魔：池 = in_enchanting_table 且物品 supportsEnchantment；
    // 已有同 key 仅接受更高等级（升级覆盖）；全新 key 若与现有冲突需 enchantIgnoreConflict 才放行。
    // 池内互斥与追加逻辑对齐原版 selectEnchantment。
    private List<EnchantmentInstance> beyond$selectLoose(RegistryAccess registryAccess, ItemStack stack, int cost,
                                                         java.util.stream.Stream<Holder<Enchantment>> possible) {
        List<EnchantmentInstance> chosen = new ArrayList<>();
        int i = stack.getEnchantmentValue();
        if (i <= 0) i = 15; // 附魔书等 enchantability=0 的物品在宽松模式下以基准 15 参与随机（否则恒空）
        int level = cost;
        level += 1 + this.beyond$random.nextInt(i / 4 + 1) + this.beyond$random.nextInt(i / 4 + 1);
        float f = (this.beyond$random.nextFloat() + this.beyond$random.nextFloat() - 1.0F) * 0.15F;
        level = net.minecraft.util.Mth.clamp(Math.round(level + (float) level * f), 1, Integer.MAX_VALUE);
        ItemEnchantments existing = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        boolean ignoreConflict = CommandConfig.enchantIgnoreConflict();
        int powerLevel = level; // 供 lambda 使用（候选生成后 level 还会 /=2 追加用）
        List<EnchantmentInstance> pool = new ArrayList<>();
        possible.forEach(holder -> {
            if (!stack.supportsEnchantment(holder)) return;
            Enchantment ench = holder.value();
            int curLevel = existing.getLevel(holder);
            // 已有同 key：仅接受更高等级（同级/低级无意义，跳过整项）
            if (curLevel > 0 && ench.getMaxLevel() <= curLevel) return;
            if (curLevel == 0 && !ignoreConflict) {
                // 全新 key：与现有任一冲突则跳过（除非开启 ignoreConflict）
                for (Holder<Enchantment> h : existing.keySet()) {
                    if (!Enchantment.areCompatible(h, holder)) return;
                }
            }
            for (int lvl = ench.getMaxLevel(); lvl >= ench.getMinLevel(); lvl--) {
                if (lvl <= curLevel) break; // 更低/同级无意义
                if (powerLevel >= ench.getMinCost(lvl) && powerLevel <= ench.getMaxCost(lvl)) {
                    pool.add(new EnchantmentInstance(holder, lvl));
                    break;
                }
            }
        });
        if (pool.isEmpty()) return chosen;
        net.minecraft.util.random.WeightedRandom.getRandomItem(this.beyond$random, pool).ifPresent(chosen::add);
        while (this.beyond$random.nextInt(50) <= level && !pool.isEmpty()) {
            if (!chosen.isEmpty()) {
                EnchantmentHelper.filterCompatibleEnchantments(pool, chosen.get(chosen.size() - 1));
            }
            if (pool.isEmpty()) break;
            net.minecraft.util.random.WeightedRandom.getRandomItem(this.beyond$random, pool).ifPresent(chosen::add);
            level /= 2;
        }
        return chosen;
    }

    // 物品无效（空/不可附魔）时的清理钩子（神化子类覆写以清空其属性同步）
    protected void beyond$onEnchantSlotsInvalid() {}

    /** 计算钩子：服务端重算三槽费用与附魔预订（原版实现；神化子类覆写为 Apoth 计算）。
     *  每槽算完后自动下发"将获得"完整列表（服务端开关开启时），打开界面放入物品即可预览。 */
    protected void beyond$onEnchantSlotsChanged(ItemStack itemstack) {
        IdMap<Holder<Enchantment>> idmap = beyond$level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).asHolderIdMap();
        int power = beyond$power();
        boolean uncapped = CommandConfig.enchantUncapPower() && power > 15;
        this.beyond$random.setSeed((long) (this.beyond$enchantmentSeed.get() + beyond$refreshTick));
        for (int k = 0; k < 3; k++) {
            // 解限（power>15）时用无钳制公式，否则走原版 helper（内部 clamp 15）
            this.costs[k] = uncapped
                    ? beyond$vanillaCost(this.beyond$random, k, power, itemstack)
                    : EnchantmentHelper.getEnchantmentCost(this.beyond$random, k, power, itemstack);
            this.enchantClue[k] = -1;
            this.levelClue[k] = -1;
            if (this.costs[k] < k + 1) {
                this.costs[k] = 0;
            }
            this.costs[k] = net.neoforged.neoforge.event.EventHooks.onEnchantmentLevelSet(beyond$level, player.blockPosition(), k, power, itemstack, costs[k]);
        }
        for (int l = 0; l < 3; l++) {
            List<EnchantmentInstance> list = List.of();
            if (this.costs[l] > 0) {
                list = this.beyond$getEnchantmentList(beyond$level.registryAccess(), itemstack, l, this.costs[l]);
                if (list != null && !list.isEmpty()) {
                    EnchantmentInstance enchantmentinstance = list.get(this.beyond$random.nextInt(list.size()));
                    this.enchantClue[l] = idmap.getId(enchantmentinstance.enchantment);
                    this.levelClue[l] = enchantmentinstance.level;
                }
            }
            beyond$sendSlotList(l, list); // 自动下发预览（开关关闭时发空表，客户端维持谜语）
        }
    }

    /** 下发单槽"将获得"完整列表（EnchantCluesPayload；serverPreviewEnabled=false 时发空表以维持谜语） */
    protected void beyond$sendSlotList(int slot, List<EnchantmentInstance> full) {
        if (!(player instanceof ServerPlayer sp)) return;
        List<EnchantmentInstance> send = serverPreviewEnabled ? new ArrayList<>(full) : List.of();
        com.solr98.beyondintegration.network.PacketHandler.sendToPlayer(sp,
                new com.solr98.beyondintegration.network.payload.EnchantCluesPayload(this.containerId, slot, send, true));
    }

    // copy 原版 EnchantmentMenu.slotsChanged：服务端计算（计算体经虚钩子分派原版/神化）
    @Override
    public void slotsChanged(Container inventory) {
        super.slotsChanged(inventory);
        if (beyond$level == null) return; // 客户端附魔计算不执行，费用靠 DataSlot 同步
        if (beyond$fillingFuel) return; // 自动填充燃料：构造阶段跳过重算/广播
        if (inventory == this.beyond$enchantSlots) {
            ItemStack itemstack = inventory.getItem(0);
            if (beyond$canEnchant(itemstack)) {
                beyond$onEnchantSlotsChanged(itemstack);
                this.broadcastChanges();
            } else {
                for (int i = 0; i < 3; i++) {
                    this.costs[i] = 0;
                    this.enchantClue[i] = -1;
                    this.levelClue[i] = -1;
                }
                beyond$onEnchantSlotsInvalid();
                // 无效/取出物品：下发空表覆盖客户端缓存（清掉旧预览）
                for (int i = 0; i < 3; i++) {
                    beyond$sendSlotList(i, List.of());
                }
            }
        }
    }

    /** 执行钩子：服务端确认点击后执行附魔（原版实现；神化子类覆写为 Apoth 点数制执行） */
    protected void beyond$doEnchant(Player player, int id, int lapisNeed, ItemStack itemstack, ItemStack lapis) {
        boolean noLapis = CommandConfig.enchantNoLapis();
        boolean gateIgnore = CommandConfig.enchantLevelGateIgnore();
        boolean creative = player.getAbilities().instabuild;
        ItemStack itemstack2 = itemstack;
        List<EnchantmentInstance> list = this.beyond$getEnchantmentList(beyond$level.registryAccess(), itemstack, id, this.costs[id]);
        if (!list.isEmpty()) {
            if (!creative && gateIgnore) {
                // 解除等级门槛：改为按"升到费用等级所需点数"计费（网络 XP 优先），失败则不执行
                int pts = beyond$scaleCost(dev.shadowsoffire.placebo.util.EnchantmentUtils.getTotalExperienceForLevel(this.costs[id]));
                if (!beyond$chargePoints((ServerPlayer) player, pts)) return;
                player.onEnchantmentPerformed(itemstack, 0); // 仅推进种子，经验已在点数扣费中结算
            } else {
                player.onEnchantmentPerformed(itemstack, lapisNeed);
            }
            // Neo: 允许物品自定义附魔转变（对齐原版 applyEnchantments）
            itemstack2 = itemstack.getItem().applyEnchantments(itemstack, list);
            this.beyond$enchantSlots.setItem(0, itemstack2);
            net.neoforged.neoforge.common.CommonHooks.onPlayerEnchantItem(player, itemstack2, list);
            if (!noLapis && !creative) {
                lapis.consume(lapisNeed, player);
                if (lapis.isEmpty()) {
                    this.beyond$enchantSlots.setItem(1, ItemStack.EMPTY);
                }
            }
            player.awardStat(Stats.ENCHANT_ITEM);
            if (player instanceof ServerPlayer) {
                net.minecraft.advancements.CriteriaTriggers.ENCHANTED_ITEM.trigger((ServerPlayer) player, itemstack2, lapisNeed);
            }
            this.beyond$enchantSlots.setChanged();
            this.beyond$enchantmentSeed.set(player.getEnchantmentSeed());
            this.slotsChanged(this.beyond$enchantSlots);
            beyond$level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, beyond$level.random.nextFloat() * 0.1F + 0.9F);
        }
    }

    // copy 原版 EnchantmentMenu.clickMenuButton：校验双端一致（等级门槛+青金石），执行经虚钩子分派；
    // 增强配置：enchantNoLapis 免燃料消耗门槛，enchantLevelGateIgnore 解除经验等级门槛
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= 0 && id < this.costs.length) {
            ItemStack itemstack = this.beyond$enchantSlots.getItem(0);
            ItemStack itemstack1 = this.beyond$enchantSlots.getItem(1);
            int i = id + 1;
            boolean noLapis = CommandConfig.enchantNoLapis();
            boolean gateIgnore = CommandConfig.enchantLevelGateIgnore();
            if ((!noLapis && (itemstack1.isEmpty() || itemstack1.getCount() < i)) && !player.hasInfiniteMaterials()) {
                return false;
            } else if (this.costs[id] <= 0 || itemstack.isEmpty()) {
                return false;
            } else if (!player.getAbilities().instabuild && !gateIgnore
                    && (player.experienceLevel < i || player.experienceLevel < this.costs[id])) {
                return false;
            } else if (beyond$level == null) {
                return true; // 客户端仅校验（校验通过即可发包），执行由服务端完成
            } else {
                beyond$doEnchant(player, id, i, itemstack, itemstack1);
                return true;
            }
        } else {
            return false;
        }
    }

    // ---- 经验点数扣费（网络 XP 优先、玩家经验补足；合计不足提示失败）----
    protected boolean beyond$chargePoints(ServerPlayer p, int pts) {
        if (pts <= 0) return true;
        long mb = (long) pts * 20L;
        long got = 0;
        DimensionsNet net = com.solr98.beyondintegration.handler.MenuNetIdHelper.getNetFromMenu(p);
        if (net == null) net = DimensionsNet.getPrimaryNetFromPlayer(p);
        if (net != null) got = net.getUnifiedStorage().extract(com.solr98.beyondintegration.handler.EnchantmentBookSeparatorHandler.xpFluidKey(), mb, false, false).amount();
        long missingPts = Math.max(0, (mb - got) / 20L);
        if (missingPts == 0) return true;
        if (dev.shadowsoffire.placebo.util.EnchantmentUtils.getExperience(p) < missingPts) {
            p.sendSystemMessage(Component.translatable("gui.beyond_integration.enchant.need_xp_points", pts));
            return false;
        }
        p.sendSystemMessage(Component.translatable("gui.beyond_integration.enchant.xp_paid_from_player", missingPts));
        p.giveExperiencePoints(-(int) Math.min(missingPts, Integer.MAX_VALUE));
        return true;
    }

    /** 费用百分比换算（enchantCostPercent） */
    protected static int beyond$scaleCost(int pts) {
        return Math.max(1, (int) Math.round(pts * CommandConfig.enchantCostPercent() / 100.0));
    }

    /**
     * 刷新：消耗燃料（enchantRefreshLapis；免燃料/创造免费）后递增局部随机偏移并重算三槽候选。
     * 由客户端 RefreshEnchantPayload 触发，仅服务端执行。
     */
    public void doRefresh(ServerPlayer sp) {
        if (beyond$level == null) return; // 仅服务端
        if (!serverRefreshEnabled) return; // 服务端开关兜底
        boolean noLapis = CommandConfig.enchantNoLapis();
        boolean creative = sp.getAbilities().instabuild;
        int need = CommandConfig.enchantRefreshLapis();
        ItemStack fuel = beyond$enchantSlots.getItem(1);
        if (!noLapis && !creative && need > 0 && (fuel.isEmpty() || fuel.getCount() < need)) {
            sp.sendSystemMessage(Component.translatable("gui.beyond_integration.enchant.refresh_need", need));
            return;
        }
        if (!noLapis && !creative && need > 0) {
            fuel.shrink(need);
            if (fuel.isEmpty()) {
                beyond$enchantSlots.setItem(1, ItemStack.EMPTY);
            }
        }
        beyond$refreshTick++; // 局部偏移：三槽费用/候选/点击执行均使用同一偏移，结果一致
        this.slotsChanged(this.beyond$enchantSlots);
        this.broadcastChanges();
    }

    // ---- BI 公开 API（GUI 使用）----
    public ItemStack getItemInput() { return beyond$enchantSlots.getItem(0); }
    public int getGoldCount() { ItemStack s = beyond$enchantSlots.getItem(1); return s.isEmpty() ? 0 : s.getCount(); }
    public int getEnchantmentSeed() { return this.beyond$enchantmentSeed.get(); }
    @Override public int getPanelHeight() { return 76; } // 附魔台面板：槽(41) + 三按钮区(12..69) 底部留白

    // 重建布局：将附魔台两个槽位定位到对应坐标
    @Override public void rebuildSlots() {
        super.rebuildSlots();
        if (beyond$wsS >= 0) { int y = ey(WY); for (int i = 0; i < 2; i++) { setSlotX(slots.get(beyond$wsS + i), WX[i]); setSlotY(slots.get(beyond$wsS + i), y); } }
    }

    // 快速移动：物品/燃料槽 → 背包→网络；背包→对应槽（对齐原版分流：物品进槽0、燃料进槽1）
    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
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
                else if (ItemStack.isSameItemSameComponents(stack, ts)) { int space = target.getMaxStackSize(stack) - ts.getCount(); if (space > 0) { int n = Math.min(stack.getCount(), space); ts.grow(n); stack.shrink(n); target.set(ts); target.setChanged(); } }
            }
            if (stack.isEmpty()) { slot.setChanged(); return result; }
        }
        return super.quickMoveStack(player, slotIndex);
    }

    // 关闭菜单：自动填充的燃料存回网络，待附魔物品按归还方向清空（对齐原版 EnchantmentMenu.removed 的 clearContainer）
    @Override
    public void removed(@NotNull Player p) {
        super.removed(p);
        if (p.level().isClientSide()) return;
        // 关闭时：自动填充的燃料存回网络；待附魔物品按原归还方向清理（避免丢失）
        cleanSlotsFromContainer(true, beyond$enchantSlots, new int[]{1});
        cleanSlotsFromContainer(firstCraftReturnDir, beyond$enchantSlots, new int[]{0});
    }

    @Override
    public void cleanSlots(boolean toStorage) {
        cleanSlotsFromContainer(toStorage, beyond$enchantSlots, new int[]{0, 1});
    }
}