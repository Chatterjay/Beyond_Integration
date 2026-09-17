package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.ftb.FtbIntegrationHelper;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import dev.ftb.mods.ftbquests.quest.reward.ItemReward;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FTB Quests 集成：物品奖励直接发放进 BD 网络（替代放进玩家背包）。
 * <p>
 * 仅当玩家存在主网络且集成启用时接管；否则保持 FTB 原版发放行为。
 * 发放数量规则与原版一致（count + 随机加成，按最大堆叠拆分后插入网络）。
 * <p>
 * FTB Quests 未安装（@Pseudo）/RI 加载（MixinPlugin 让路）/配置关闭时自动跳过。
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.quest.reward.ItemReward", remap = false)
public class FtbItemRewardMixin {

    @Shadow(remap = false) private ItemStack item;
    @Shadow(remap = false) private int count;
    @Shadow(remap = false) private int randomBonus;
    @Shadow(remap = false) private boolean onlyOne;

    @Inject(method = "claim", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void beyond$claimToNetwork(ServerPlayer player, boolean notify, CallbackInfo ci) {
        if (!FtbIntegrationHelper.isEnabled()) return;
        try {
            if (item == null || item.isEmpty() || count <= 0) return;
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) return;  // 无网络 → 保持原版发放
            // 原版 onlyOne 语义：背包已拥有则不发
            if (onlyOne && player.getInventory().contains(item)) return;

            int size = count + player.level().getRandom().nextInt(randomBonus + 1);
            if (size <= 0) return;

            ItemStackKey key = new ItemStackKey(item.copyWithCount(1));
            net.getUnifiedStorage().insert(key, size, false);
            net.setDirty();
            ci.cancel();
        } catch (Throwable ignored) {}
    }
}
