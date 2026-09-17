package com.solr98.beyondintegration.handler;

/**
 * 附魔分离开关访问器：由维度网络实现，供分离处理器查询/修改
 * 网络级附魔分离（附魔书）功能的启用状态。
 */
public interface EnchantSeparationAccessor {
    /** 查询该网络是否启用了附魔分离 */
    boolean beyond$isEnchantSeparationEnabled();
    /** 设置该网络的附魔分离启用状态 */
    void beyond$setEnchantSeparationEnabled(boolean enabled);
}
