package com.solr98.beyondintegration.init;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.UnorderedStackHandlerRemoveZero;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.NotNull;

/**
 * 铁砧合成菜单（copy 自原版 AnvilMenu(1.21.1) 改造，私有符号统一 beyond$ 前缀避免冲突）：
 * 继承网络存储菜单；经验消耗支持 LEVEL（网络 XP 转玩家经验升级后按等级扣）与
 * POINTS（固定点数，网络优先）两种模式，昂贵标准 VANILLA/APOTHIC 可配置；
 * 材料清理/快捷移动均适配网络存储。
 */
public class DimensionsAnvilMenu extends DimensionsStorageMenu implements ICleanableWorkstation {
    // 自定义名称最大长度（对齐原版 50 字符上限）
    public static final int MAX_NAME_LENGTH = 50;
    // 槽位常量：结果槽索引 2、输入/输出槽数量、三个槽位 X 坐标与 Y 坐标
    private static final int RESULT_SLOT = 2;
    private static final int WS_INPUT = 2, WS_OUTPUT = 2;
    private static final int[] WX = {27, 76, 134};
    private static final int WY = 41;

    // ---- BI 公开状态（GUI/网络包使用）----
    public String anvName = "";
    // 客户端同步的费用镜像（GUI 与客户端判定使用；服务端以 beyond$cost 为准）
    public int anvLevel;

    // ---- copy 原版 AnvilMenu 私有状态（beyond$ 前缀）----
    public int beyond$repairItemCountCost; // 修复类合成消耗的原料数量
    private String beyond$itemName;        // 当前输入的自定义名称
    private final DataSlot beyond$cost = DataSlot.standalone(); // 合成费用（等级制，服务端为准，DataSlot 同步到客户端）
    private final Container beyond$inputSlots;   // 输入槽容器（2 格：主物品 + 附加材料）
    private final Container beyond$resultSlots = new ResultContainer(); // 结果槽容器
    private int beyond$wsS = -1; // 本菜单自加槽位的起始索引

    // 客户端构造：由网络包创建
    public DimensionsAnvilMenu(int id, Inventory inv, FriendlyByteBuf b) {
        this(ModMenus.ANVIL.get(), id, inv,
                new UnorderedStackHandlerRemoveZero(AbstractUnorderedStackHandler.UiTimestampPolicy.NONE));
    }

    // 主构造：注册输入/结果槽与费用 DataSlot；结果槽 mayPickup/onTake 负责经验扣费与材料消耗
    public DimensionsAnvilMenu(MenuType<?> t, int id, Inventory inv, AbstractUnorderedStackHandler d) {
        super(t, id, inv, d);
        // 原版 ItemCombinerMenu.createContainer：输入槽变化 → slotsChanged → createResult
        this.beyond$inputSlots = new SimpleContainer(2) {
            @Override
            public void setChanged() {
                super.setChanged();
                DimensionsAnvilMenu.this.slotsChanged(this);
            }
        };
        beyond$wsS = slots.size();
        for (int i = 0; i < 2; i++) {
            addSlot(new Slot(beyond$inputSlots, i, WX[i], ey(WY)));
            customSlotIndices.add(slots.size() - 1);
        }
        // 结果槽：经验消耗优先使用网络 XP 流体；等级/网络判定；材料清理（原版 onTake 逻辑）
        addSlot(new Slot(beyond$resultSlots, 0, WX[2], ey(WY)) {
            @Override public boolean mayPlace(ItemStack s) { return false; }
            @Override public boolean mayPickup(Player p) {
                if (p.getAbilities().instabuild) return true;
                // 客户端用同步的 anvLevel（beyond$cost 在客户端通过 set 镜像）
                int c = p.level().isClientSide() ? anvLevel : beyond$cost.get();
                if (c <= 0) return true;
                long costPts = beyond$xpCostPoints(c);
                if (p.level().isClientSide()) {
                    // 客户端：镜像 XP 足够则放行（网络支付）；否则按原版等级判定
                    long netXp = 0;
                    if (clientNetStorage != null)
                        netXp = clientNetStorage.getStackByKey(xpFluidKey()).amount();
                    if (beyond$chargeModeLevel()) {
                        // LEVEL 模式：等级足够直接放行；不足时网络需补足到 cost 级
                        if (p.experienceLevel >= c) return true;
                        long needPts = costPts - beyond$xpCostPoints(p.experienceLevel);
                        return netXp >= needPts * 20L;
                    }
                    return netXp >= costPts * 20L || p.experienceLevel >= c;
                }
                DimensionsNet net = com.solr98.beyondintegration.handler.MenuNetIdHelper.getNetFromMenu((ServerPlayer) p);
                if (net == null) net = DimensionsNet.getPrimaryNetFromPlayer(p);
                long netXp = 0;
                if (net != null) netXp = net.getUnifiedStorage().extract(xpFluidKey(), costPts * 20L, true, false).amount();
                if (beyond$chargeModeLevel()) {
                    if (p.experienceLevel >= c) return true;
                    long needPts = costPts - beyond$xpCostPoints(p.experienceLevel);
                    return netXp >= needPts * 20L;
                }
                boolean allow = netXp >= costPts * 20L || p.experienceLevel >= c;
                return allow;
            }
            @Override public void onTake(Player p, ItemStack stack) {
                if (p.level().isClientSide()) return;
                int c = beyond$cost.get();
                if (c > 0 && !p.getAbilities().instabuild) {
                    DimensionsNet net = com.solr98.beyondintegration.handler.MenuNetIdHelper.getNetFromMenu((ServerPlayer) p);
                    if (net == null) net = DimensionsNet.getPrimaryNetFromPlayer(p);
                    long costPts = beyond$xpCostPoints(c);
                    long mb = costPts * 20L;
                    long got = 0;
                    if (net != null) got = net.getUnifiedStorage().extract(xpFluidKey(), mb, false, false).amount();
                    if (beyond$chargeModeLevel()) {
                        // LEVEL 模式：网络 XP 转为玩家经验补足等级，再按原版等级扣费
                        long gotPts = got / 20;
                        if (gotPts > 0) p.giveExperiencePoints((int) Math.min(gotPts, Integer.MAX_VALUE));
                        p.giveExperienceLevels(-c);
                    } else {
                        // POINTS 模式：网络优先，不足扣玩家点数
                        long missingPts = Math.max(0, (mb - got) / 20);
                        // 不能 clamp 到 totalExperience：被 giveExperienceLevels 降级过的玩家该字段可能为 0，
                        // 而 giveExperiencePoints(-n) 通过 experienceProgress 循环掉级扣费，不依赖该字段
                        if (missingPts > 0) p.giveExperiencePoints(-(int) missingPts);
                    }
                }
                // 原版清理逻辑（AnvilMenu.onTake）：修复类按 repairItemCountCost 消耗，合并类整个清空
                beyond$inputSlots.setItem(0, ItemStack.EMPTY);
                ItemStack mat = beyond$inputSlots.getItem(1);
                if (!mat.isEmpty()) {
                    int repairCost = beyond$repairItemCountCost;
                    if (repairCost > 0) {
                        if (mat.getCount() > repairCost) { mat.shrink(repairCost); beyond$inputSlots.setItem(1, mat); }
                        else beyond$inputSlots.setItem(1, ItemStack.EMPTY);
                    } else {
                        beyond$inputSlots.setItem(1, ItemStack.EMPTY);
                    }
                }
                // 铁砧使用音效（对齐原版 AnvilBlock.use 的 ANVIL_USE 反馈）
                p.level().playSound(null, p.blockPosition(),
                        net.minecraft.sounds.SoundEvents.ANVIL_USE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
                slotsChanged(beyond$inputSlots);
            }
        });
        customSlotIndices.add(slots.size() - 1);
        addDataSlot(new DataSlot() {
            @Override public int get() { return beyond$cost.get(); }
            @Override public void set(int v) { beyond$cost.set(v); anvLevel = v; }
        });
    }

    // ---- 原版等级→点数公式（对齐 Player.getXpNeededForNextLevel；1.21.1 与 1.20.1 相同）----
    private static int beyond$xpNeededForLevel(int level) {
        if (level >= 30) return 112 + (level - 30) * 9;
        if (level >= 15) return 37 + (level - 15) * 5;
        return 7 + level * 2;
    }

    // Apothic 语义（EnchantmentUtils.getTotalExperienceForLevel(cost)）：固定扣"从 0 级升到 cost 级"的总点数
    private static long beyond$xpCostPoints(int cost) {
        long sum = 0;
        for (int l = 0; l < cost; l++) sum += beyond$xpNeededForLevel(l);
        return sum;
    }

    // 经验流体 Key（复用附魔分离处理器中的经验流体定义）
    private static com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey xpFluidKey() {
        return com.solr98.beyondintegration.handler.EnchantmentBookSeparatorHandler.xpFluidKey();
    }

    // 当前模式的费用上限判定：LEVEL 按等级，POINTS 按点数
    private static boolean beyond$overCap(int cost) {
        if (beyond$chargeModeLevel()) return cost >= CommandConfig.anvilLevelCap();
        return beyond$xpCostPoints(cost) >= CommandConfig.anvilPointsCap();
    }

    // 当前费用模式是否为 LEVEL（等级制）；false 则为 POINTS（点数制）
    private static boolean beyond$chargeModeLevel() {
        return CommandConfig.anvilCostMode() == CommandConfig.AnvilChargeMode.LEVEL;
    }

    // 重建布局：将铁砧三个槽位（输入/材料/结果）定位到对应坐标
    @Override public void rebuildSlots() {
        super.rebuildSlots();
        if (beyond$wsS >= 0) { int y = ey(WY); for (int i = 0; i < 3; i++) { setSlotX(slots.get(beyond$wsS + i), WX[i]); setSlotY(slots.get(beyond$wsS + i), y); } }
    }

    // 原版 ItemCombinerMenu.slotsChanged
    @Override
    public void slotsChanged(Container inventory) {
        super.slotsChanged(inventory);
        if (inventory == this.beyond$inputSlots) {
            this.beyond$createResult();
        }
    }

    // ---- BI 公开 API（GUI 使用）----
    public ItemStack getOutput() { return beyond$resultSlots.getItem(0); }
    public ItemStack getInput() { return beyond$inputSlots.getItem(0); }
    public ItemStack getAdditional() { return beyond$inputSlots.getItem(1); }
    public int getCost() { return beyond$cost.get(); }
    // 消耗的经验点数（Apothic 语义：0→cost 级总点数；客户端 anvLevel 为同步镜像）
    public long getCostPoints() { return beyond$xpCostPoints(player.level().isClientSide() ? anvLevel : beyond$cost.get()); }

    // ---- copy 原版 AnvilMenu.createResult（逐行对齐 1.21.1，含昂贵标准配置兼容）----
    public void beyond$createResult() {
        ItemStack itemstack = this.beyond$inputSlots.getItem(0);
        this.beyond$cost.set(1);
        int i = 0;
        long j = 0L;
        int k = 0;
        if (!itemstack.isEmpty()) {
            // ── 对齐原版 CommonHooks.onAnvilChange：铁砧配方事件（左槽非空即触发，任何合并逻辑之前）──
            // 事件取消 → 直接 return；事件有输出 → 直接写结果槽/费用/材料消耗并 return，
            // 完全跳过后续原版逻辑（附魔合并/改名/清空/费用钳制），与 CommonHooks 行为一致
            net.neoforged.neoforge.event.AnvilUpdateEvent beyond$anvilEvent = new net.neoforged.neoforge.event.AnvilUpdateEvent(
                    itemstack, this.beyond$inputSlots.getItem(1), this.beyond$itemName, j, this.player);
            if (net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(beyond$anvilEvent).isCanceled()) {
                // 对齐 CommonHooks.onAnvilChange 取消分支：清结果槽、费用与材料消耗归零
                this.beyond$resultSlots.setItem(0, ItemStack.EMPTY);
                this.beyond$cost.set(0);
                this.beyond$repairItemCountCost = 0;
                return;
            }
            if (!beyond$anvilEvent.getOutput().isEmpty()) {
                this.beyond$resultSlots.setItem(0, beyond$anvilEvent.getOutput());
                this.beyond$cost.set((int) beyond$anvilEvent.getCost());
                this.beyond$repairItemCountCost = beyond$anvilEvent.getMaterialCost();
                return;
            }
            ItemStack itemstack1 = itemstack.copy();
            ItemStack itemstack2 = this.beyond$inputSlots.getItem(1);
            ItemEnchantments.Mutable itemenchantments$mutable = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(itemstack1));
            j += (long) itemstack.getOrDefault(DataComponents.REPAIR_COST, 0).intValue()
                    + (long) itemstack2.getOrDefault(DataComponents.REPAIR_COST, 0).intValue();
            this.beyond$repairItemCountCost = 0;
            boolean flag = false;
            if (!itemstack2.isEmpty()) {
                flag = itemstack2.has(DataComponents.STORED_ENCHANTMENTS);
                if (itemstack1.isDamageableItem() && itemstack1.getItem().isValidRepairItem(itemstack, itemstack2)) {
                    int l2 = Math.min(itemstack1.getDamageValue(), itemstack1.getMaxDamage() / 4);
                    if (l2 <= 0) {
                        this.beyond$resultSlots.setItem(0, ItemStack.EMPTY);
                        this.beyond$cost.set(0);
                        return;
                    }
                    int j3;
                    for (j3 = 0; l2 > 0 && j3 < itemstack2.getCount(); j3++) {
                        int k3 = itemstack1.getDamageValue() - l2;
                        itemstack1.setDamageValue(k3);
                        i++;
                        l2 = Math.min(itemstack1.getDamageValue(), itemstack1.getMaxDamage() / 4);
                    }
                    this.beyond$repairItemCountCost = j3;
                } else {
                    if (!flag && (!itemstack1.is(itemstack2.getItem()) || !itemstack1.isDamageableItem())) {
                        this.beyond$resultSlots.setItem(0, ItemStack.EMPTY);
                        this.beyond$cost.set(0);
                        return;
                    }
                    if (itemstack1.isDamageableItem() && !flag) {
                        int l = itemstack.getMaxDamage() - itemstack.getDamageValue();
                        int i1 = itemstack2.getMaxDamage() - itemstack2.getDamageValue();
                        int j1 = i1 + itemstack1.getMaxDamage() * 12 / 100;
                        int k1 = l + j1;
                        int l1 = itemstack1.getMaxDamage() - k1;
                        if (l1 < 0) l1 = 0;
                        if (l1 < itemstack1.getDamageValue()) {
                            itemstack1.setDamageValue(l1);
                            i += 2;
                        }
                    }
                    ItemEnchantments itemenchantments = EnchantmentHelper.getEnchantmentsForCrafting(itemstack2);
                    boolean flag2 = false;
                    boolean flag3 = false;
                    // ---- BI 铁砧附魔增强（配置见 CommandConfig anvil 段）----
                    // 完全解禁：一键开启叠级突破 + 无视冲突 + 无视适用性
                    boolean beyond$unrestricted = CommandConfig.anvilUnrestricted();
                    boolean beyond$ignoreConflict = CommandConfig.anvilIgnoreConflict() || beyond$unrestricted;
                    boolean beyond$ignoreSupport = CommandConfig.anvilIgnoreSupport() || beyond$unrestricted;
                    CommandConfig.BreakLevelMode beyond$levelMode = beyond$unrestricted
                            ? CommandConfig.BreakLevelMode.PLUS : CommandConfig.anvilBreakLevelMode();
                    boolean beyond$breakLevel = beyond$levelMode != CommandConfig.BreakLevelMode.OFF;
                    int beyond$enchantCostBase = i;   // 附魔段费用基准（倍率仅作用于附魔段）
                    // 代价参数预读取：倍率基础值（100 = ×1）+ 各惩罚基础值（循环内不再重复取配置）
                    int beyond$costPercent = 100;
                    int beyond$conflictPenalty = 0;
                    int beyond$supportPenalty = 0;
                    if (beyond$ignoreConflict) {
                        beyond$costPercent += CommandConfig.anvilConflictPercent();
                        beyond$conflictPenalty = CommandConfig.anvilConflictPenalty();
                    }
                    if (beyond$ignoreSupport) {
                        beyond$costPercent += CommandConfig.anvilSupportPercent();
                        beyond$supportPenalty = CommandConfig.anvilSupportPenalty();
                    }
                    if (beyond$breakLevel) beyond$costPercent += CommandConfig.anvilBreakLevelPercent();
                    if (beyond$unrestricted) beyond$costPercent += CommandConfig.anvilUnrestrictedPercent();
                    int beyond$conflictCount = 0;     // 被"无视"的冲突附魔数（计罚金）
                    int beyond$supportViolationCount = 0; // 违例（不适用）附魔数（计罚金）
                    for (Object2IntMap.Entry<Holder<Enchantment>> entry : itemenchantments.entrySet()) {
                        Holder<Enchantment> holder = entry.getKey();
                        int i2 = itemenchantments$mutable.getLevel(holder);
                        int j2 = entry.getIntValue();
                        if (beyond$levelMode == CommandConfig.BreakLevelMode.ADDITIVE) {
                            // 相加突破：仅同类附魔直接相加（5+5=10）；不同附魔 i2=0 时自然等于书等级
                            j2 = i2 + j2;
                        } else {
                            // 原版合成规则（PLUS 仅解除封顶，规则不变：同附魔 +1 即 5+5=6，异附魔取 max）
                            j2 = i2 == j2 ? j2 + 1 : Math.max(j2, i2);
                        }
                        Enchantment enchantment = holder.value();
                        boolean flag1 = itemstack.supportsEnchantment(holder);
                        boolean beyond$supportViolation = !flag1;
                        if (this.player.getAbilities().instabuild) flag1 = true;
                        for (Holder<Enchantment> holder1 : itemenchantments$mutable.keySet()) {
                            if (!holder1.equals(holder) && !Enchantment.areCompatible(holder, holder1)) {
                                if (beyond$ignoreConflict) {
                                    // 无视冲突：附魔保留，冲突附魔改计罚金（conflictPenalty × 冲突数）
                                    beyond$conflictCount++;
                                } else {
                                    flag1 = false;
                                    i++;
                                }
                            }
                        }
                        if (!flag1 && beyond$ignoreSupport) {
                            // 无视适用性：视同可打（等同创造模式），违例附魔改计罚金
                            flag1 = true;
                        }
                        if (!flag1) {
                            flag3 = true;
                        } else {
                            flag2 = true;
                            if (beyond$supportViolation) beyond$supportViolationCount++;
                            if (!beyond$breakLevel) {
                                // 原版钳制附魔最高等级；PLUS/ADDITIVE 模式下解除。
                                // 有效上限：加载 Apothic Enchanting 时取其配置上限
                                // （神化通过 coremod 仅重定向特定调用点，不含本 mod，需自行查询）。
                                // 限制仅作用于"有变化"的合并结果，且不把已有等级降到目标原等级以下
                                // （来源等级不高于目标时合并结果=原等级，超限已有附魔不会被削回上限）
                                int beyond$maxLevel = beyond$effectiveMaxLevel(enchantment);
                                if (j2 > beyond$maxLevel) j2 = Math.max(beyond$maxLevel, i2);
                            }
                            itemenchantments$mutable.set(holder, j2);
                            int l3 = enchantment.getAnvilCost();
                            if (flag) l3 = Math.max(1, l3 / 2);
                            i += l3 * j2;
                            if (itemstack.getCount() > 1) i = 40;
                        }
                    }
                    // 代价结算：基础罚金先计入附魔段费用，再应用百分位倍率
                    // 倍率 = (100 + Σ启用项百分位) / 100，各项百分位相加（不连乘）；100 = ×1 基准
                    if (beyond$ignoreConflict || beyond$ignoreSupport || beyond$breakLevel || beyond$unrestricted) {
                        int beyond$enchantPart = i - beyond$enchantCostBase;
                        int beyond$penalty = beyond$conflictPenalty * beyond$conflictCount
                                + beyond$supportPenalty * beyond$supportViolationCount;
                        i = beyond$enchantCostBase
                                + (int) Math.round((beyond$enchantPart + beyond$penalty) * beyond$costPercent / 100.0);
                    }
                    if (flag3 && !flag2) {
                        this.beyond$resultSlots.setItem(0, ItemStack.EMPTY);
                        this.beyond$cost.set(0);
                        return;
                    }
                    if (flag && !itemstack1.isBookEnchantable(itemstack2)) itemstack1 = ItemStack.EMPTY;
                }
            }

            if (this.beyond$itemName != null && !StringUtil.isBlank(this.beyond$itemName)) {
                if (!this.beyond$itemName.equals(itemstack.getHoverName().getString())) {
                    k = 1;
                    i += k;
                    itemstack1.set(DataComponents.CUSTOM_NAME, Component.literal(this.beyond$itemName));
                }
            } else if (itemstack.has(DataComponents.CUSTOM_NAME)) {
                k = 1;
                i += k;
                itemstack1.remove(DataComponents.CUSTOM_NAME);
            }

            int k2 = (int) Mth.clamp(j + (long) i, 0L, 2147483647L);
            this.beyond$cost.set(k2);
            if (i <= 0) {
                itemstack1 = ItemStack.EMPTY;
            }
            // 原版 39 钳制仅 LEVEL 模式且等级上限为 40（纯改名费用恰好 40 的防误判）
            if (k == i && k > 0 && this.beyond$cost.get() >= 40
                    && beyond$chargeModeLevel() && CommandConfig.anvilLevelCap() == 40) {
                this.beyond$cost.set(39);
            }
            // 费用上限：LEVEL 按等级、POINTS 按点数，达到即置空（过于昂贵）
            if (beyond$overCap(this.beyond$cost.get()) && !this.player.getAbilities().instabuild) {
                itemstack1 = ItemStack.EMPTY;
            }

            if (!itemstack1.isEmpty()) {
                int i3 = itemstack1.getOrDefault(DataComponents.REPAIR_COST, 0);
                if (i3 < itemstack2.getOrDefault(DataComponents.REPAIR_COST, 0)) {
                    i3 = itemstack2.getOrDefault(DataComponents.REPAIR_COST, 0);
                }
                if (k != i || k == 0) {
                    i3 = beyond$calculateIncreasedRepairCost(i3);
                }
                itemstack1.set(DataComponents.REPAIR_COST, i3);
                EnchantmentHelper.setEnchantments(itemstack1, itemenchantments$mutable.toImmutable());
            }

            this.beyond$resultSlots.setItem(0, itemstack1);
            this.broadcastChanges();
        } else {
            this.beyond$resultSlots.setItem(0, ItemStack.EMPTY);
            this.beyond$cost.set(0);
        }
    }

    // copy 原版 AnvilMenu.calculateIncreasedRepairCost
    public static int beyond$calculateIncreasedRepairCost(int oldRepairCost) {
        return oldRepairCost * 2 + 1;
    }

    /**
     * 附魔有效等级上限：加载 Apothic Enchanting 时经其 EnchHooks 取神化配置上限
     * （神化通过 coremod 重定向原版调用点，本 mod 不在重定向范围内，需主动查询；
     * EnchHooks 内部已处理神化附魔模块开关，配置未加载等异常回退原版）；
     * 未加载/异常时退回原版 {@link Enchantment#getMaxLevel()}。
     */
    public static int beyond$effectiveMaxLevel(Enchantment ench) {
        if (net.neoforged.fml.ModList.get().isLoaded("apothic_enchanting")) {
            try {
                return dev.shadowsoffire.apothic_enchanting.asm.EnchHooks.getMaxLevel(ench);
            } catch (Throwable ignored) {}
        }
        return ench.getMaxLevel();
    }

    // copy 原版 AnvilMenu.setItemName（含 validateName）
    public boolean beyond$setItemName(String itemName) {
        String s = beyond$validateName(itemName);
        if (s != null && !s.equals(this.beyond$itemName)) {
            this.beyond$itemName = s;
            if (this.getSlot(RESULT_SLOT + beyond$wsS).hasItem()) {
                ItemStack itemstack = this.getSlot(RESULT_SLOT + beyond$wsS).getItem();
                if (StringUtil.isBlank(s)) {
                    itemstack.remove(DataComponents.CUSTOM_NAME);
                } else {
                    itemstack.set(DataComponents.CUSTOM_NAME, Component.literal(s));
                }
            }
            this.beyond$createResult();
            return true;
        }
        return false;
    }

    // 过滤非法字符并校验长度（copy 原版 AnvilMenu.validateName）
    private static String beyond$validateName(String itemName) {
        String s = StringUtil.filterText(itemName);
        return s.length() <= 50 ? s : null;
    }

    // GUI 调用入口：设置自定义名称并同步费用镜像到客户端
    public void rename(String name) { anvName = name; beyond$setItemName(name); anvLevel = beyond$cost.get(); }

    // 快速移动：结果槽显式校验经验（shift 不经过 mayPickup），多余产物入背包→网络；输入槽→背包；背包→输入槽
    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        if (slotIndex == beyond$wsS + WS_OUTPUT) {
            // shift 快速移动不经过 doClick 的 mayPickup 检查，此处显式校验经验是否足够
            boolean pickable = slot.mayPickup(player);
            if (!pickable) return ItemStack.EMPTY;
            int beforeCount = stack.getCount();
            moveStackTo(stack, inventoryStartIndex, inventoryEndIndex, true);
            if (!stack.isEmpty()) {
                DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
                if (net != null) {
                    long remaining = net.getUnifiedStorage().insert(new ItemStackKey(stack), stack.getCount(), false).amount();
                    stack.setCount((int) remaining);
                }
            }
            if (stack.getCount() < beforeCount) {
                // 材料消耗与经验扣费统一由 onTake 完成（不可预清空输入槽，否则 cost 会被重算为 0）
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

    // 关闭菜单：按归还方向清空输入槽（结果槽直接丢弃，对齐原版 ItemCombinerMenu.removed）
    @Override
    public void removed(@NotNull Player p) {
        super.removed(p);
        if (p.level().isClientSide()) return;
        cleanSlots(firstCraftReturnDir);
    }

    @Override
    public void cleanSlots(boolean toStorage) {
        cleanSlotsFromContainer(toStorage, beyond$inputSlots, new int[]{0, 1});
        // 结果槽对齐原版 ItemCombinerMenu.removed：关闭时直接丢弃，不归还（否则绕过 onTake 扣费）
        beyond$resultSlots.setItem(0, ItemStack.EMPTY);
    }
}
