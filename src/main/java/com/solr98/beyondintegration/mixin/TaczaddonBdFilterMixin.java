package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.CommandConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 禁止 taczaddon 将 BD（beyonddimensions）网络方块识别为合成材料源。
 *
 * taczaddon 的 NearbyInventorySourceResolver.resolve 用 NeoForge 方块能力
 * (Capabilities.ItemHandler.BLOCK) 识别附近容器——BD 方块暴露该能力时，
 * 整个网络会被当作"容器源"接入工作台（全量槽遍历 + 事务快照，性能风险）。
 * 本 Mixin 在解析结果返回处过滤：配置 block_bd_reader 开启时剔除 BD 方块源。
 *
 * 未安装 taczaddon 时注入点不存在（MixinPlugin 条件过滤）。
 */
@Mixin(targets = "com.mafuyu404.taczaddon.init.NearbyInventorySourceResolver", remap = false)
public abstract class TaczaddonBdFilterMixin {

    @Inject(method = "resolve", at = @At("RETURN"), cancellable = true, remap = false)
    private static void beyond$filterBdSources(ServerPlayer player, BlockPos anchor,
                                               int horizontalRadius, int verticalRadius,
                                               CallbackInfoReturnable<List<com.mafuyu404.taczaddon.init.NearbyInventorySourceResolver.Source>> cir) {
        if (!CommandConfig.blockBdContainerReader()) return;
        List<com.mafuyu404.taczaddon.init.NearbyInventorySourceResolver.Source> sources = cir.getReturnValue();
        if (sources == null || sources.isEmpty()) return;

        ServerLevel level = player.serverLevel();
        ArrayList<com.mafuyu404.taczaddon.init.NearbyInventorySourceResolver.Source> filtered =
                new ArrayList<>(sources.size());
        boolean removed = false;
        for (com.mafuyu404.taczaddon.init.NearbyInventorySourceResolver.Source source : sources) {
            BlockState state = level.getBlockState(source.pos());
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (id != null && "beyonddimensions".equals(id.getNamespace())) {
                removed = true;
                continue;
            }
            filtered.add(source);
        }
        if (removed) cir.setReturnValue(List.copyOf(filtered));
    }
}
