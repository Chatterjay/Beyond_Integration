package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.revive.ReviveSupport;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin 版 setHealth 复活实现：注入 {@code LivingEntity.checkTotemDeathProtection} 的 RETURN，
 * 在原版图腾检查失败（返回 false）时消耗网络图腾并强制返回 true，
 * 使死亡流程不进入 {@code die()}（不产生死亡事件），从而抢在死亡事件之前完成复活。
 * <p>
 * 该注入点与 FE 等模组尊重的原版图腾检查一致（FE 通过 accessor 调用同一方法），
 * 因此对走该路径的击杀同样有效。
 * <p>
 * 配置 {@code revive.mixin_enabled=false} 时方法体内直接返回（不消耗、不干预）。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityTotemReviveMixin {

    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"), cancellable = true)
    private void beyondIntegration$reviveByNetworkTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (!CommandConfig.reviveMixinEnabled()) return;
        if (!((Object) this instanceof ServerPlayer player)) return;
        // 原版/其他模组的图腾保护已生效：不重复消耗
        if (Boolean.TRUE.equals(cir.getReturnValue())) return;
        if (CommandConfig.reviveRespectBypassesInvulnerability()
                && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (ReviveSupport.isBlacklisted(source)) return;
        if (ReviveSupport.onCooldown(player)) return;

        // 保守判定：无法确认由原版伤害链致死时，一律按 setHealth 直杀处理（多扣图腾）
        boolean setHealthDeath = !ReviveSupport.isExpectedLethalDeath(player);
        ReviveSupport.clearDeathData(player);

        if (!ReviveSupport.tryRevive(player, setHealthDeath)) return;
        ReviveSupport.markUsed(player);
        // 强制"图腾保护已触发"，阻止后续 die() 与死亡事件
        cir.setReturnValue(true);
    }
}
