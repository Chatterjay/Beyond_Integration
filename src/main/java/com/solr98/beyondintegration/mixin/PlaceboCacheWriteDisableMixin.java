package com.solr98.beyondintegration.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * 禁止 Placebo 词缀缓存写入（CachedObjectSource）。
 * 神化通过 Placebo 的 CachedObjectSource（mixin 注入到 ItemStack 的 getOrCreate 实例方法）
 * 在读取词缀（tooltip/名称/稀有度）时缓存计算结果——部分版本会同步到物品 NBT/组件缓存，
 * 导致物品 NBT 动态变化，进入超越维度网络后存储 key 与之不匹配（无法取出/重复堆积）。
 * 此处拦截该实例方法：跳过缓存读写，每次直接计算（词缀读取频率低，开销可接受），
 * 保持物品 NBT/组件稳定。未安装 placebo 时注入点不存在（defaultRequire=0 静默跳过）。
 */
@Mixin(ItemStack.class)
public class PlaceboCacheWriteDisableMixin {

    @Inject(method = "getOrCreate", at = @At("HEAD"), cancellable = true, remap = false)
    private <T> void beyond$skipCacheWrite(ResourceLocation id,
                                           Function<ItemStack, T> factory,
                                           ToIntFunction<ItemStack> hasher,
                                           CallbackInfoReturnable<T> cir) {
        // 直接计算返回，不读写缓存，避免污染物品 NBT/组件
        cir.setReturnValue(factory.apply((ItemStack) (Object) this));
    }
}
