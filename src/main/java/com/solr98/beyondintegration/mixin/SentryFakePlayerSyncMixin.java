package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.handler.FakePlayerNetMarker;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 哨戒假玩家同步 Mixin（Pseudo）：注入哨戒机械臂模组的 SentryArmBlockEntity 的
 * tick 与 onChunkUnloaded。在哨戒 AI 逻辑执行前把其弹药箱内容标记到哨戒假玩家，
 * 供弹药消耗追踪；区块卸载时清理标记，防止残留。
 */
@Pseudo
@Mixin(targets = "euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity", remap = false)
public class SentryFakePlayerSyncMixin {

    /** 反射缓存的哨戒臂方块实体类；加载失败时为 null（哨戒模组未安装） */
    @Unique
    private static Class<?> beyond$sentryClass;
    /** 反射缓存的哨戒假玩家类 */
    @Unique
    private static Class<?> beyond$fpClass;
    /** 反射缓存的假玩家获取方法（静态 get(SentryArmBlockEntity)） */
    @Unique
    private static java.lang.reflect.Method beyond$fpGet;
    /** 反射缓存的哨戒弹药箱列表字段 attachedAmmoBoxes */
    @Unique
    private static java.lang.reflect.Field beyond$ammoBoxesField;
    /** 静态初始化：反射缓存哨戒方块实体/假玩家类及其获取方法与弹药箱字段 */
    static {
        try {
            beyond$sentryClass = Class.forName("euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity");
            beyond$fpClass = Class.forName("euphy.upo.sentrymechanicalarm.util.SentryFakePlayer");
            beyond$fpGet = beyond$fpClass.getMethod("get", beyond$sentryClass);
            beyond$ammoBoxesField = beyond$sentryClass.getField("attachedAmmoBoxes");
        } catch (Exception e) {
            beyond$sentryClass = null;
        }
    }

    /** 哨戒 AI 逻辑执行前，把当前弹药箱内容标记到哨戒假玩家，供弹药消耗追踪 */
    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Leuphy/upo/sentrymechanicalarm/content/SentryArmBlockEntity;sentryLogic()V",
            shift = At.Shift.BEFORE), remap = false)
    private void beyond$beforeSentryLogic(CallbackInfo ci) {
        if (beyond$sentryClass == null) return;
        try {
            Object self = beyond$sentryClass.cast(this);
            FakePlayer fp = (FakePlayer) beyond$fpGet.invoke(null, self);
            if (fp == null) return;
            java.util.List<ItemStack> boxes = (java.util.List<ItemStack>) beyond$ammoBoxesField.get(self);
            FakePlayerNetMarker.markFromBoxes(fp, boxes);
        } catch (Exception e) {
            // Sentry mod not loaded, skip
        }
    }

    /** 区块卸载时，清除哨戒假玩家的弹药标记，防止数据残留 */
    @Inject(method = "onChunkUnloaded", at = @At("HEAD"), remap = false)
    private void beyond$onChunkUnloaded(CallbackInfo ci) {
        if (beyond$sentryClass == null) return;
        try {
            Object self = beyond$sentryClass.cast(this);
            FakePlayer fp = (FakePlayer) beyond$fpGet.invoke(null, self);
            if (fp != null) FakePlayerNetMarker.clearFor(fp);
        } catch (Exception e) {
            // Sentry mod not loaded, skip
        }
    }
}
