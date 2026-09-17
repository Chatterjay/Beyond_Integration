package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.ftb.FtbIntegrationHelper;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * FTB Quests 集成：把 BD 网络物品并入 FTB 的玩家背包汇总（{@code PlayerInventorySummary}）。
 * <p>
 * FTB 的非消耗型物品任务通过该汇总统计"玩家拥有"的物品数量（{@code ItemTask.submitTask} →
 * {@code countMatchingItems(PlayerInventorySummary.getRelevantItems(...))}），
 * 这里在背包汇总完成后追加主网络物品（<b>含已有存量</b>），使网络库存自动计入任务进度。
 * <p>
 * FTB Quests 未安装（@Pseudo）/RI 加载（MixinPlugin 让路）/配置关闭时自动跳过。
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.util.PlayerInventorySummary", remap = false)
public class FtbPlayerInventorySummaryMixin {

    @Shadow(remap = false) private static List<ItemStack> nonEmptyStacks;
    @Shadow(remap = false) private static Map<Item, List<ItemStack>> stacksByItem;

    /** 背包汇总完成后追加网络物品（含存量），使非消耗型物品任务自动计入网络库存 */
    @Inject(method = "build", at = @At("RETURN"), remap = false, require = 0)
    private static void beyond$includeNetworkItems(ServerPlayer player, CallbackInfo ci) {
        if (!FtbIntegrationHelper.isEnabled()) return;
        try {
            for (KeyAmount ka : FtbIntegrationHelper.networkItems(player)) {
                if (!(ka.key() instanceof ItemStackKey ik)) continue;
                ItemStack stack = ik.getReadOnlyStack();
                if (stack.isEmpty()) continue;
                ItemStack copy = stack.copyWithCount((int) Math.min(ka.amount(), Integer.MAX_VALUE));
                nonEmptyStacks.add(copy);
                stacksByItem.computeIfAbsent(copy.getItem(), k -> new ArrayList<>()).add(copy);
            }
        } catch (Throwable ignored) {}
    }
}
