package com.solr98.beyondintegration.api;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;

/**
 * 网络弹药供应接口：为使用维度网络库存供弹的武器/炮塔系统提供统一的
 * 弹药类型标识、弹药数量查询与消耗抽象。
 */
public interface INetworkAmmoProvider {

    /** 获取弹药类型键（用于在网络中匹配对应的弹药物品/流体） */
    String getAmmoTypeKey();

    /** 查询指定网络中该类型弹药的可供数量 */
    long getAmmoCount(DimensionsNet net);

    /** 判断该弹药是否无限（无需消耗） */
    boolean hasInfiniteAmmo(DimensionsNet net);

    /** 从网络消耗指定数量弹药，返回实际消耗量 */
    long consumeAmmo(DimensionsNet net, long amount);
}
