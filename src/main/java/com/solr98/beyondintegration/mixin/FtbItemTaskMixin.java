package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.ftb.FtbIntegrationHelper;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FTB Quests 集成：消耗型物品任务提交时，背包不足的部分从 BD 网络自动补足。
 * <p>
 * FTB 原生 {@code ItemTask.submitTask} 只从玩家背包扣除物品；这里在提交前把缺口
 * 从主网络提取到背包，随后由原生逻辑正常消耗并计入进度。
 * 仅处理"点击提交"路径（craftedItem 为空），不影响合成提交与背包监听。
 * <p>
 * FTB Quests 未安装（@Pseudo）/RI 加载（MixinPlugin 让路）/配置关闭时自动跳过。
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.quest.task.ItemTask", remap = false)
public class FtbItemTaskMixin {

    @Inject(method = "submitTask", at = @At("HEAD"), remap = false, require = 0)
    private void beyond$supplyFromNetwork(TeamData teamData, ServerPlayer player, ItemStack craftedItem, CallbackInfo ci) {
        if (!FtbIntegrationHelper.isEnabled()) return;
        try {
            // 仅处理点击提交路径（合成提交由 FTB 自身处理）
            if (craftedItem != null && !craftedItem.isEmpty()) return;
            ItemTask self = (ItemTask) (Object) this;
            if (!self.consumesResources() || self.isTaskScreenOnly()) return;
            if (teamData.isCompleted(self)) return;

            long need = self.getMaxProgress() - teamData.getProgress(self);
            if (need <= 0) return;

            // 统计背包中已匹配数量，仅补足缺口
            long have = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (!s.isEmpty() && self.test(s)) have += s.getCount();
            }
            long missing = need - have;
            if (missing <= 0) return;

            FtbIntegrationHelper.supplyToInventory(player, self::test, missing);
        } catch (Throwable ignored) {}
    }
}
