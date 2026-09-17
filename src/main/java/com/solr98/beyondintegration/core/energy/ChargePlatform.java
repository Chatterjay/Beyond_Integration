package com.solr98.beyondintegration.core.energy;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;

/**
 * 物品充电平台接口（跨加载器抽象）。
 * Forge/NeoForge 各提供一份实现并在初始化时注册；充电主体逻辑（EnergyAmmoChargeHandler）
 * 只依赖本接口，从而保证两分支的主体源码完全一致，平台差异收敛在本接口的实现里。
 */
public interface ChargePlatform {

    /** 注册平台实现（各分支主类初始化时调用一次） */
    static void set(ChargePlatform platform) {
        if (platform != null) {
            Holder.instance = platform;
        }
    }

    /** 获取平台实现（未注册时返回安全的空实现） */
    static ChargePlatform get() {
        return Holder.instance;
    }

    /** 模组是否加载 */
    boolean isModLoaded(String modId);

    /** 取物品的能量槽访问句柄（无能量槽返回 null） */
    EnergyHandle energy(ItemStack stack);

    /** 主手攻击伤害属性判定（模组工具/武器兜底判定用） */
    boolean hasMainHandAttackDamage(ItemStack stack);

    /**
     * 工具/武器标签判定。
     * 默认使用原版细分类工具标签；Forge 实现额外覆盖聚合 tools 标签。
     */
    default boolean isToolTag(ItemStack stack) {
        return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES);
    }

    /** 物品能量槽访问接口（平台无关，屏蔽 Forge/NeoForge capability 差异） */
    interface EnergyHandle {
        /** 最大可存储能量 */
        int maxStored();

        /** 当前已存能量 */
        int stored();

        /** 是否可接收能量 */
        boolean canReceive();

        /** 接收能量并返回实际接收量 */
        int receive(int amount);
    }

    /** 平台实现持有者（未注册时的空实现保证充电逻辑安全跳过） */
    final class Holder {
        private static ChargePlatform instance = new ChargePlatform() {
            @Override public boolean isModLoaded(String modId) { return false; }

            @Override public EnergyHandle energy(ItemStack stack) { return null; }

            @Override public boolean hasMainHandAttackDamage(ItemStack stack) { return false; }
        };
    }
}
