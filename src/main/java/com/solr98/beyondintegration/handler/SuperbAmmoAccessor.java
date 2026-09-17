package com.solr98.beyondintegration.handler;
import java.util.Map;

/**
 * SW 模组虚拟弹药存储访问接口：由维度网络（DimensionsNet）实现，
 * 提供 SW 虚拟弹药（弹药类型名 → 数量）的读写访问。
 */
public interface SuperbAmmoAccessor {
    /** 获取虚拟弹药映射（弹药类型名 → 数量）。 */
    Map<String, Long> getSuperbAmmo();

    /** 设置虚拟弹药映射（弹药类型名 → 数量）。 */
    void setSuperbAmmo(Map<String, Long> map);

    /** 延迟补建存储增量订阅（加载结束后安全时机；默认无操作）。 */
    default void beyond$ensureDeltaHook() {}
}

