package com.solr98.beyondintegration.handler;

import java.util.Map;

/**
 * Superb Warfare 弹药数据访问器接口。
 * 由 Mixin 注入到超神战争（Superb Warfare）相关类，
 * 用于读写弹药数据（键为弹药标识，值为数量）。
 */
public interface SuperbAmmoAccessor {
    /** 获取弹药数据映射（键为弹药标识，值为数量）。 */
    Map<String, Long> getSuperbAmmo();
    /** 设置弹药数据映射。 */
    void setSuperbAmmo(Map<String, Long> map);
    /** 延迟补建存储增量订阅（加载结束后安全时机；默认无操作）。 */
    default void beyond$ensureDeltaHook() {}
}
