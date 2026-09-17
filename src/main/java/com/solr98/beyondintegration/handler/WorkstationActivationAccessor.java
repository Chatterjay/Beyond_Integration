package com.solr98.beyondintegration.handler;

/**
 * 工作台献祭激活访问器接口。
 * 由 DimensionsNetMixin 注入到 BD 的 DimensionsNet，
 * 以网络为单位持久化"已献祭激活的工作台"集合（存于 NetworkAmmoData）。
 */
public interface WorkstationActivationAccessor {
    /** 查询该网络是否已激活指定工作台 */
    boolean beyond$isWorkstationActivated(String id);
    /** 激活指定工作台（网络级持久化） */
    void beyond$activateWorkstation(String id);
    /** 重置指定工作台的激活状态（网络级持久化） */
    void beyond$resetWorkstation(String id);
}
