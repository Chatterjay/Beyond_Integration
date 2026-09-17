package com.solr98.beyondintegration.handler;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.util.XpUtil;
import net.minecraft.world.entity.player.Player;

/**
 * 经验棒分批发放辅助（替换 BD {@code XpUtil.clampXpToGive} 的分配额逻辑，Mixin 见 XpUtilMixin）。
 *
 * <p>方式一（BATCH，配置 {@code xp_rod.grant_mode=BATCH}）：每次（每 tick，由经验棒 keep 模式驱动）
 * 检测玩家等级与目标等级差并发放一批（{@code xp_rod.grant_batch_size} 点），
 * 通过 {@code giveExperiencePoints} 合法升级，避免单次逼近 int 边界时的溢出与 float 进度异常。</p>
 *
 * <p>等级边界说明：
 * <ul>
 *   <li>{@code 21863} 级：0→21863 累计需 2,147,407,943 点（&lt; Integer.MAX_VALUE），
 *       21864 级将超过 int 上限——即 {@code totalExperience} 能完整表示总经验的最高等级
 *       （BD {@link XpUtil#MAX_SAFE_EXPERIENCE_LEVEL}）；此后 totalExperience 由原版钳制，
 *       但经验等级/进度仍可继续增长；</li>
 *   <li>{@link #BATCH_MAX_LEVEL}（238,609,312）级：原版升级成本公式 {@code 9L-158} 不溢出 int
 *       的最高等级（经验条/升级流程仍工作），即方式一可合法提升的最高等级。</li>
 * </ul>
 * 目标等级超过 {@link #BATCH_MAX_LEVEL} 时应使用方式二（DIRECT 直设式）。</p>
 */
public final class XpGrantHelper {

    private XpGrantHelper() {}

    /**
     * 方式一（giveExperiencePoints）可合法提升的最高等级：
     * 原版升级成本公式 {@code 9L-158}（L≥31）在 L=238609312 时仍 ≤ Integer.MAX_VALUE，
     * 再高会 int 溢出导致经验条异常。
     */
    public static final int BATCH_MAX_LEVEL = 238_609_312;

    /** 单次（每 tick）可发放的 XP 点数：玩家状态校验 + 等级余量 + 批次上限 */
    public static int pendingPerTick(Player player, long requestedXp) {
        if (requestedXp <= 0 || player.experienceLevel < 0
                || player.totalExperience < 0
                || !Float.isFinite(player.experienceProgress)
                || player.experienceProgress < 0f || player.experienceProgress > 1f) {
            return 0;
        }

        // 等级余量：目标等级取配置上限与方式一合法上限的较小值；
        // 不使用 totalExperience 余量限制——原版 giveExperiencePoints 内部已对 totalExperience
        // 做 int 钳制（等级 21863 后饱和），但 experienceLevel/progress 仍可继续增长到 238609312
        int maxLevel = (int) Math.min(CommandConfig.xpRodMaxTargetLevel(), (long) BATCH_MAX_LEVEL);
        long levelRoom = XpUtil.xpToReachAtLeast(XpUtil.levelAsDouble(player), maxLevel);
        long room = Math.min(levelRoom, Math.max(0L, requestedXp));
        if (room <= 0) return 0;

        int batch = CommandConfig.xpRodGrantBatchSize();
        if (batch <= 0) return (int) Math.min(room, Integer.MAX_VALUE);
        return (int) Math.min(room, batch);
    }
}
