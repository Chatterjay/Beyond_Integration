package com.solr98.beyondintegration.client;

import java.util.HashMap;
import java.util.Map;

/**
 * 附魔分离开关客户端独立状态（与 SW 弹药快照解耦）。
 *
 * 背景：该开关只依赖 BD 网络数据（服务端持久化于 beyond_integration_data.dat），
 * 与 Superb Warfare 弹药轮询无关。原实现挂在 SuperbAmmoCache 的 NetSnapshot 上，
 * 未安装 SW 时快照永不建立（snap == null），导致按钮状态恒为默认值、点击切换无效。
 * 独立后：状态按网络 ID 单独存储（默认关闭），由服务端 Sync 包
 * （切换应答 / 打开界面请求应答）更新，仅 BD+BI 时也可正常点击切换。
 */
public enum EnchantSeparationState {
    INSTANCE;

    /** 网络 ID → 附魔分离开关（默认关闭） */
    private final Map<Integer, Boolean> states = new HashMap<>();

    /** 查询指定网络的附魔分离开关（无记录默认关闭） */
    public boolean get(int netId) {
        return states.getOrDefault(netId, false);
    }

    /** 设置指定网络的附魔分离开关（服务端同步包 / 本地切换写入） */
    public void set(int netId, boolean enabled) {
        states.put(netId, enabled);
    }

    /** 移除指定网络的状态记录（网络销毁时调用） */
    public void remove(int netId) {
        states.remove(netId);
    }

    /** 清空全部状态（玩家登录/登出/服务器切换时调用） */
    public void clear() {
        states.clear();
    }
}
