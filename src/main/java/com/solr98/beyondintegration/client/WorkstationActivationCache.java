package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.feature.workstation.WorkstationActivation;

import java.util.List;
import java.util.Set;

/**
 * 工作台献祭激活状态客户端缓存（当前界面快照）。
 * 服务端通过 WorkstationActivationSyncPacket 下发开关与已激活列表；
 * BD 终端界面打开时请求一次，激活成功后服务端主动推送更新。
 */
public final class WorkstationActivationCache {

    /** 献祭激活开关（false 时不做锁定显示） */
    private static volatile boolean enabled = false;
    /** 当前网络已激活的工作台 ID 集合 */
    private static volatile Set<String> activated = Set.of();

    private WorkstationActivationCache() {}

    /** S2C 写入：更新开关与已激活集合 */
    public static void update(boolean enabledIn, List<String> ids) {
        enabled = enabledIn;
        activated = ids == null ? Set.of() : Set.copyOf(ids);
    }

    /** 断开连接/切换界面时重置 */
    public static void reset() {
        enabled = false;
        activated = Set.of();
    }

    /** 该工作台是否处于"未激活锁定"状态（开关关闭或非可激活工作台时恒 false） */
    public static boolean isLocked(String id) {
        return enabled && WorkstationActivation.isActivatable(id)
                && !activated.contains(id == null ? "" : id.toLowerCase(java.util.Locale.ROOT));
    }
}
