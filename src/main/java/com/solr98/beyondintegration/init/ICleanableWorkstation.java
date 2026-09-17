package com.solr98.beyondintegration.init;

/**
 * 工作站清空接口：输入/结果槽按方向归还（toStorage=true→网络优先，false→背包优先）。
 * 各工作站菜单（铁砧/合成/切石/磨石/锻造）实现该接口以统一关闭界面时的物品归还行为。
 */
public interface ICleanableWorkstation {
    // 按指定方向清空工作站槽位中的物品
    void cleanSlots(boolean toStorage);
}
