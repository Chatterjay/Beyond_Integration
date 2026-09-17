package com.solr98.beyondintegration.api;

import net.neoforged.fml.ModList;

/**
 * 模组集成接口：定义与其他模组进行联动的统一入口，
 * 仅在目标模组加载时才会执行实际的注册逻辑。
 */
public interface IModIntegration {
    /** 目标模组的 ID */
    String modId();

    /** 执行实际的联动注册逻辑 */
    void doRegister();

    /** 判断目标模组当前是否已加载 */
    default boolean isLoaded() {
        return ModList.get().isLoaded(modId());
    }

    /** 统一注册入口：目标模组未加载时直接跳过 */
    default void register() {
        if (!isLoaded()) return;
        doRegister();
    }
}
