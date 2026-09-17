package com.solr98.beyondintegration.handler;

/**
 * 自动充电开关访问器接口。
 * 由 DimensionsNetMixin 注入到 DimensionsNet，用于持久化网络级"自动充电"开关状态
 * （网络终端左侧开关按钮切换；关闭后该网络不再为玩家/女仆的装备位充电）。
 */
public interface EnergyChargeAccessor {
    /** 查询网络是否启用自动充电。 */
    boolean beyond$isEnergyChargeEnabled();
    /** 设置网络的自动充电开关。 */
    void beyond$setEnergyChargeEnabled(boolean enabled);
}
