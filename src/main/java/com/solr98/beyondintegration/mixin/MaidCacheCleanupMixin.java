package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.maid.MaidNetworkCache;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 注入目标：东方 Little Maid 的 {@code EntityMaid}（女仆实体）。
 * 目的：女仆被移除（死亡/卸载）时同步清理其对应的网络缓存条目，
 * 防止缓存泄漏或残留导致旧数据被误用。使用 @Pseudo 标记以允许可选模组目标。
 */
@Pseudo
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid", remap = false)
public class MaidCacheCleanupMixin {

    // 拦截 remove：实体移除时按 UUID 清理女仆网络缓存
    @Inject(method = "remove", at = @At("HEAD"))
    private void beyond$onRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        MaidNetworkCache.remove(((Entity) (Object) this).getUUID());
    }
}
