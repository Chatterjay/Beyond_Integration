package com.solr98.beyondintegration.handler;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import com.wintercogs.beyonddimensions.common.item.XpExchangeItem;
import com.wintercogs.beyonddimensions.util.XpUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 直设式经验给予（等级计算 + 设定 + 经验补差）：方式二，由配置
 * {@code xp_rod.grant_mode=DIRECT} 启用（Mixin {@code XpExchangeItemMixin} 接管 keep 模式 tick）。
 *
 * <p>与方式一（BATCH 分批 {@code giveExperiencePoints}，上限 238609312）互补：
 * 本方式不依赖原版升级公式，按"等级计算 → 直接设定等级/进度 → 网络经验补差"执行。</p>
 *
 * <p>实现：每 tick 从网络提取一批经验流体（{@code grant_batch_size} 点经验），
 * 以"当前等级+进度"换算总经验，加上补差额后反解等级/进度并直接设定
 * （{@code experienceLevel/experienceProgress}），{@code totalExperience} 按 int 钳制；
 * 网络扣除按实际补差经验结算（含不足 1 XP 零头回插）。</p>
 */
public final class XpDirectGranter {

    private XpDirectGranter() {}

    /** 直设路径的安全等级上限（与方式一共用；此处反解/进度计算仍走 XpUtil 的 int 成本公式） */
    private static final int MAX_DIRECT_LEVEL = XpGrantHelper.BATCH_MAX_LEVEL;

    /**
     * keep 模式 tick 入口：配置为 DIRECT 时接管补级。
     * 返回 true 表示已处理（调用方应 cancel 原方法）；其余情况返回 false 交回 BD 原逻辑。
     */
    public static boolean tryGrant(ItemStack stack, Player player) {
        if (!CommandConfig.xpRodTweaksEnabled()) return false; // 总开关关闭：走 BD 原逻辑
        if (!(stack.getItem() instanceof XpExchangeItem)) return false;
        if (CommandConfig.xpRodGrantMode() != CommandConfig.XpGrantMode.DIRECT) return false; // 路径由配置决定
        int target = XpExchangeItem.getXpLevelPerAction(stack);
        if (target <= 0) return false;
        if (player.experienceLevel < 0 || !Float.isFinite(player.experienceProgress)
                || player.experienceProgress < 0f || player.experienceProgress > 1f) {
            return false; // 状态异常：交回原逻辑（其自身校验会跳过）
        }

        double cur = XpUtil.levelAsDouble(player);
        if (cur >= target) return false; // 达到/超过目标：降方向交回 BD 原逻辑

        DimensionsNet net = NetedItem.getNet(stack);
        if (net == null) return true;

        long remainXp = XpUtil.xpBetweenLevels(cur, target);
        if (remainXp <= 0) return true;

        // 一批可补的经验（0 = 一次性补到目标，受网络存量限制）
        int batch = CommandConfig.xpRodGrantBatchSize();
        long wantXp = batch > 0 ? Math.min(batch, remainXp) : remainXp;
        int rate = XpExchangeItem.getConversionRate();
        long units = extractXpUnits(net, wantXp * rate);
        long gainedXp = units / rate;
        // 不足 1 XP 的零头回插（网络余额可能非 rate 整倍）
        long odd = units % rate;
        if (odd > 0) net.getUnifiedStorage().insert(xpFluidKey(), odd, false);
        if (gainedXp <= 0) return true;

        // 等级计算 + 设定 + 经验补差
        long curTotal = XpUtil.xpBetweenLevels(0, cur);
        long newTotal = curTotal + gainedXp;
        int newLevel = levelForTotalXp(newTotal);
        long base = XpUtil.xpBetweenLevels(0, newLevel);
        long nextCost = XpUtil.xpBetweenLevels(newLevel, newLevel + 1);
        float progress = nextCost > 0 ? (float) ((newTotal - base) / (double) nextCost) : 0f;

        player.experienceLevel = newLevel;
        player.experienceProgress = progress;
        // totalExperience 为 int：补差结果按 int 钳制（超界部分以等级/进度为准）
        player.totalExperience = (int) Math.min(newTotal, Integer.MAX_VALUE);
        return true;
    }

    /** 从网络提取经验流体（先自家 XP 流体，再 #c:experience 其他流体），返回实际提取量（mB） */
    private static long extractXpUnits(DimensionsNet net, long wantUnits) {
        if (wantUnits <= 0) return 0;
        var storage = net.getUnifiedStorage();
        long got = storage.extract(xpFluidKey(), wantUnits, false, false).amount();
        if (got >= wantUnits || XpExchangeItem.xpFluids == null) return got;
        for (Fluid f : XpExchangeItem.xpFluids) {
            if (got >= wantUnits) break;
            try {
                got += storage.extract(new FluidStackKey(new FluidStack(f, 1)), wantUnits - got, false, false).amount();
            } catch (Throwable ignored) {}
        }
        return got;
    }

    /** 总经验反解等级（二分；上限 MAX_DIRECT_LEVEL 保护 long 公式） */
    private static int levelForTotalXp(long totalXp) {
        int lo = 0, hi = MAX_DIRECT_LEVEL;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (XpUtil.xpBetweenLevels(0, mid) <= totalXp) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }

    /** 统一经验流体键（BI 各处使用的 BD 经验流体） */
    private static FluidStackKey xpFluidKey() {
        return EnchantmentBookSeparatorHandler.xpFluidKey();
    }
}
