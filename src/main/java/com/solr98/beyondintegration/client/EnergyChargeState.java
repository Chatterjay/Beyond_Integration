package com.solr98.beyondintegration.client;

import java.util.HashMap;
import java.util.Map;

/**
 * 自动充电开关客户端独立状态（单例枚举，按网络 ID 存储，默认启用）。
 * 由服务端 Sync 包（切换应答 / 打开界面请求应答）更新，
 * 供网络终端左侧的自动充电开关按钮显示与切换。
 */
public enum EnergyChargeState {
    INSTANCE;

    private final Map<Integer, Boolean> states = new HashMap<>();

    /** 查询指定网络的自动充电开关（无记录默认启用） */
    public boolean get(int netId) {
        return states.getOrDefault(netId, true);
    }

    /** 设置指定网络的自动充电开关（服务端同步包 / 本地切换写入） */
    public void set(int netId, boolean enabled) {
        states.put(netId, enabled);
    }

    /** 移除指定网络的状态记录 */
    public void remove(int netId) {
        states.remove(netId);
    }

    /** 清空全部状态 */
    public void clear() {
        states.clear();
    }
}
