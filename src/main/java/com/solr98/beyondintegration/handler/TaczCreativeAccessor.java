package com.solr98.beyondintegration.handler;
import java.util.Map;
import java.util.Set;

/**
 * Tacz 创造弹药箱数据访问接口：由维度网络（DimensionsNet）实现，
 * 提供 Tacz 创造弹药类型集合、全类型创造标记及创造数量统计的读写访问。
 */
public interface TaczCreativeAccessor {
    /** 获取 Tacz 创造弹药类型集合（含 "*" 表示全类型）。 */
    Set<String> getTaczCreativeTypes();

    /** 设置 Tacz 创造弹药类型集合。 */
    void setTaczCreativeTypes(Set<String> types);

    /** 是否启用全类型创造（所有弹药视为无限）。 */
    boolean getCreativeAllType();

    /** 设置全类型创造开关。 */
    void setCreativeAllType(boolean allType);

    /** 获取各弹药类型的创造数量统计（类型 ID → 数量）。 */
    Map<String, Integer> getTaczCreativeCounts();

    /** 设置各弹药类型的创造数量统计。 */
    void setTaczCreativeCounts(Map<String, Integer> counts);
}
