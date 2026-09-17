package com.solr98.beyondintegration.api;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.world.entity.Entity;

/**
 * 车辆能量系统接口：为与维度网络（BD）绑定的载具提供能量读写抽象，
 * 外部系统可通过该接口对载具进行能量充放与网络绑定查询。
 */
public interface IVehicleEnergySystem {

    /** 获取载具最大能量值 */
    float getMaxEnergy();

    /** 获取载具当前能量值 */
    float getCurrentEnergy();

    /** 向载具注入能量，返回实际注入量 */
    float addEnergy(float amount);

    /** 获取载具对应的实体 */
    Entity getVehicleEntity();

    /** 获取载具绑定的网络 ID，无绑定时返回 -1 */
    int getBoundNetId();

    /** 获取绑定网络实例，未绑定或网络不存在时返回 null */
    default DimensionsNet getBoundNetwork() {
        int id = getBoundNetId();
        return id >= 0 ? DimensionsNet.getNetFromId(id) : null;
    }
}
