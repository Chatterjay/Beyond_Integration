package com.solr98.beyondintegration.init;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.UnorderedStackHandlerRemoveZero;
import dev.shadowsoffire.apothic_enchanting.table.ApothEnchantmentHelper;
import dev.shadowsoffire.apothic_enchanting.table.EnchantmentTableStats;
import dev.shadowsoffire.apothic_enchanting.util.MiscUtil;
import dev.shadowsoffire.placebo.util.EnchantmentUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 神化(Apothic)模式附魔台菜单（独立子类，行为与 {@link DimensionsEnchantMenu}（原版）完全分开）：
 * 覆写计算/执行虚钩子接入 Apothic 机制——
 * eterna 复用 power 配置（最低保底 1.5），quanta/arcana 由 CommandConfig 配置（默认 15/0）；
 * 费用/选魔按 ApothEnchantmentHelper（等级上限/功率曲线随 Apothic EnchantmentInfo 扩展，可超原版等级）；
 * 三属性（eterna/quanta/arcana）经 DataSlot 同步客户端供属性条渲染；
 * 每槽至多发送 clues+1 个候选符石（EnchantCluesPayload）；
 * 燃料槽接受 #c:enchanting_fuels；经验扣费按 Apothic 点数制（MiscUtil.getExpCostForSlot），
 * 网络 XP 流体优先扣除、玩家经验（placebo 曲线）补足；已附魔物品仅诅咒可再附。
 */
public class DimensionsApothEnchantMenu extends DimensionsEnchantMenu {
    // ---- 神化三属性（服务端计算后经 DataSlot 同步客户端）----
    public int apothEterna, apothQuanta, apothArcana;

    // 客户端构造：由网络包创建（MenuType 为 ENCHANT_APOTH）
    public DimensionsApothEnchantMenu(int id, Inventory inv, FriendlyByteBuf b) {
        this(ModMenus.ENCHANT_APOTH.get(), id, inv,
                new UnorderedStackHandlerRemoveZero(AbstractUnorderedStackHandler.UiTimestampPolicy.NONE), null);
    }

    // 主构造：附加三属性 DataSlot 注册
    public DimensionsApothEnchantMenu(MenuType<?> t, int id, Inventory inv, AbstractUnorderedStackHandler d, Level level) {
        super(t, id, inv, d, level);
        addDataSlot(new DataSlot() {
            @Override public int get() { return apothEterna; }
            @Override public void set(int v) { apothEterna = v; }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return apothQuanta; }
            @Override public void set(int v) { apothQuanta = v; }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return apothArcana; }
            @Override public void set(int v) { apothArcana = v; }
        });
    }

    @Override public boolean isApothMode() { return true; }

    /** 神化 eterna 恒允许 0-100（不受 enchantUncapPower 限制） */
    @Override protected int beyond$powerCap() { return 100; }

    /** 神化物品判定：未附魔或仅诅咒可再附；enchantIgnoreEnchanted 开启后任意已附魔可再附（copy Apoth isEnchantableEnough） */
    private static boolean beyond$isApothEnchantable(ItemStack stack) {
        if (!stack.isEnchanted()) return true;
        if (CommandConfig.enchantIgnoreEnchanted()) return true;
        return EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet().stream().allMatch(h -> h.is(EnchantmentTags.CURSE));
    }

    @Override protected boolean beyond$canEnchant(ItemStack stack) {
        return !stack.isEmpty() && beyond$isApothEnchantable(stack);
    }

    @Override protected void beyond$onEnchantSlotsInvalid() {
        this.apothEterna = 0;
        this.apothQuanta = 0;
        this.apothArcana = 0;
        this.broadcastChanges(); // 属性条清零同步
    }

    /** 神化模式属性：eterna 由 power 配置决定；quanta/arcana 由配置决定（默认 15/0 对齐 Apoth 无书架基准） */
    private static EnchantmentTableStats beyond$apothStats(float eterna) {
        return new EnchantmentTableStats(eterna,
                (float) CommandConfig.enchantApothQuanta(),
                (float) CommandConfig.enchantApothArcana(),
                1, Set.of(), false, false);
    }

    /** 神化选魔（对齐 ApothEnchantmentMenu.getEnchantmentList，seed + slot + 刷新偏移 定序；无 Infusion 分支） */
    private List<EnchantmentInstance> beyond$selectApoth(ItemStack stack, int slot, int cost, EnchantmentTableStats stats) {
        this.beyond$random.setSeed((long) (this.beyond$enchantmentSeed.get() + slot + beyond$refreshTick));
        return ApothEnchantmentHelper.selectEnchantment(
                this.beyond$random, stack, cost, stats,
                beyond$level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT));
    }

    /** 神化费用（经验点数，Apothic 语义：0 槽=XP(L)，1 槽=+XP(L-1)，2 槽=+XP(L-2)，对齐 MiscUtil.getExpCostForSlot） */
    public int getApothExpCost(int slot) {
        int level = this.costs[slot];
        if (level <= 0) return 0;
        return MiscUtil.getExpCostForSlot(level, slot);
    }

    // ---- 神化模式重算（对齐 ApothEnchantmentMenu.slotsChanged：费用 → 主候选 DataSlot + 候选符石包）----
    @Override protected void beyond$onEnchantSlotsChanged(ItemStack itemstack) {
        float eterna = Math.max(1.5F, (float) beyond$power()); // 无书架保底 1.5（对齐 Apoth）
        EnchantmentTableStats stats = beyond$apothStats(eterna);
        IdMap<Holder<Enchantment>> idmap = beyond$level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).asHolderIdMap();
        // 三属性值同步到客户端（属性条渲染；服务端配置为准）
        this.apothEterna = (int) eterna;
        this.apothQuanta = CommandConfig.enchantApothQuanta();
        this.apothArcana = CommandConfig.enchantApothArcana();
        this.beyond$random.setSeed((long) (this.beyond$enchantmentSeed.get() + beyond$refreshTick));
        for (int k = 0; k < 3; k++) {
            this.costs[k] = ApothEnchantmentHelper.getEnchantmentCost(this.beyond$random, k, eterna, itemstack);
            this.enchantClue[k] = -1;
            this.levelClue[k] = -1;
            if (this.costs[k] < k + 1) {
                this.costs[k]++;
            }
            this.costs[k] = net.neoforged.neoforge.event.EventHooks.onEnchantmentLevelSet(beyond$level, player.blockPosition(), k, Math.round(eterna), itemstack, this.costs[k]);
        }
        for (int l = 0; l < 3; l++) {
            if (this.costs[l] > 0) {
                List<EnchantmentInstance> list = this.beyond$selectApoth(itemstack, l, this.costs[l], stats);
                if (list != null && !list.isEmpty()) {
                    // 主候选 → DataSlot（费用按钮谜名/禁用判定）；完整列表随预览下发
                    EnchantmentInstance main = list.get(this.beyond$random.nextInt(list.size()));
                    this.enchantClue[l] = idmap.getId(main.enchantment);
                    this.levelClue[l] = main.level;
                }
                beyond$sendSlotList(l, list == null ? List.of() : list);
            } else {
                beyond$sendSlotList(l, List.of());
            }
        }
    }

    // ---- 神化执行（对齐 ApothEnchantmentMenu.clickMenuButton：点数扣费 + onEnchantmentPerformed(…,0)）----
    // 费用按 enchantCostPercent 缩放后走父类网络 XP 优先扣费；enchantNoLapis 开启时免燃料消耗
    @Override protected void beyond$doEnchant(Player player, int id, int lapisNeed, ItemStack itemstack, ItemStack lapis) {
        float eterna = Math.max(1.5F, (float) beyond$power());
        EnchantmentTableStats stats = beyond$apothStats(eterna);
        List<EnchantmentInstance> list = this.beyond$selectApoth(itemstack, id, this.costs[id], stats);
        if (list.isEmpty()) return;
        if (!player.getAbilities().instabuild) {
            if (!beyond$chargePoints((ServerPlayer) player, beyond$scaleCost(getApothExpCost(id)))) return; // 合计不足：失败提示且不执行
        }
        player.onEnchantmentPerformed(itemstack, 0); // 传 0：不扣等级，仅推进种子/统计（对齐 Apoth）
        ItemStack enchanted = itemstack.getItem().applyEnchantments(itemstack, list);
        this.beyond$enchantSlots.setItem(0, enchanted);
        net.neoforged.neoforge.common.CommonHooks.onPlayerEnchantItem(player, enchanted, list);
        if (!player.getAbilities().instabuild && !CommandConfig.enchantNoLapis()) {
            lapis.shrink(lapisNeed);
            if (lapis.isEmpty()) {
                this.beyond$enchantSlots.setItem(1, ItemStack.EMPTY);
            }
        }
        player.awardStat(Stats.ENCHANT_ITEM);
        if (player instanceof ServerPlayer sp) {
            net.minecraft.advancements.CriteriaTriggers.ENCHANTED_ITEM.trigger(sp, enchanted, lapisNeed);
        }
        this.beyond$enchantSlots.setChanged();
        this.beyond$enchantmentSeed.set(player.getEnchantmentSeed());
        this.slotsChanged(this.beyond$enchantSlots);
        beyond$level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, beyond$level.random.nextFloat() * 0.1F + 0.9F);
    }
}