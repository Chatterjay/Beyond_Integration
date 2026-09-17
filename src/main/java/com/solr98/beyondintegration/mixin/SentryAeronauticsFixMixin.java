package com.solr98.beyondintegration.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 哨戒臂航空兼容修复 Mixin（Pseudo，Sable 未加载时自动跳过）：
 * 注入哨戒机械臂模组的 AeronauticsHelper 子维度判断/坐标换算方法。
 * 未安装 Sable Companion 时禁用子维度检查并原样返回坐标，避免哨戒逻辑出错。
 */
@Pseudo
@Mixin(targets = "euphy.upo.sentrymechanicalarm.compat.AeronauticsHelper", remap = false)
public class SentryAeronauticsFixMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 未安装 Sable Companion 时，禁用 Sable 子维度判定（哨戒不在子维度中） */
    @Inject(method = "isInSableSubLevel", at = @At("HEAD"), cancellable = true)
    private static void beyond$fixSableCheck(Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        try {
            Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
        } catch (ClassNotFoundException e) {
            LOGGER.debug("SableCompanion not found, disabling Sable sub-level check");
            cir.setReturnValue(false);
        }
    }

    /** 未安装 Sable Companion 时，子维度坐标转世界坐标直接原样返回 */
    @Inject(method = "sableSubLevelToWorld", at = @At("HEAD"), cancellable = true)
    private static void beyond$fixSableToWorld(Level level, Vec3 localPos, CallbackInfoReturnable<Vec3> cir) {
        try {
            Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
        } catch (ClassNotFoundException e) {
            cir.setReturnValue(localPos);
        }
    }

    /** 未安装 Sable Companion 时，世界坐标转子维度坐标直接原样返回 */
    @Inject(method = "sableWorldToSubLevel", at = @At("HEAD"), cancellable = true)
    private static void beyond$fixSableToSub(Level level, Vec3 worldPos, BlockPos queryPos, CallbackInfoReturnable<Vec3> cir) {
        try {
            Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
        } catch (ClassNotFoundException e) {
            cir.setReturnValue(worldPos);
        }
    }
}

