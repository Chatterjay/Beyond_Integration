package com.solr98.beyondintegration.core.subscribe;

import com.mojang.logging.LogUtils;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import org.slf4j.Logger;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一的 BD（Beyond Dimensions）网络存储订阅中心。
 *
 * 背景：BD 的 UnifiedStorage 提供 subscribeDelta / subscribeDeltaWeak 订阅接口，
 * 本模组多处（DimensionsNetMixin / TaczAmmoTracker / SwAmmoTracker / VehicleNetCache）
 * 直接调用该接口，存在订阅句柄被丢弃（无法退订）、强订阅不释放、
 * 异常被 catch(Throwable) 静默吞掉等问题。
 *
 * 本中心提供：
 * 1. 统一订阅入口：全部走 BD 的弱引用订阅（subscribeDeltaWeak），
 *    回调统一为 onDelta(IStackKey, long, boolean) 三参形式；
 * 2. 订阅注册表：netId → owner → 句柄，同一 owner 重复订阅自动替换旧订阅；
 *    注册表对 owner 仅持弱引用，owner 被回收后条目惰性清理并关闭句柄；
 * 3. 生命周期管理：网络销毁（onNetDestroyed）/ 服务器停止（clearAll）时批量退订；
 * 4. 订阅失败记录 WARN 日志，不再静默吞掉。
 *
 * 注意：回调闭包不应捕获 owner 对象自身（否则弱引用语义失效，订阅将随 owner
 * 存活，直至网络销毁 / 服务器停止才被统一清理）。
 */
public final class BdSubscriptionHub {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 订阅回调：key 变化键、size 变化量、insert true=插入 / false=移除 */
    @FunctionalInterface
    public interface DeltaHandler {
        void onDelta(IStackKey<?> key, long size, boolean insert);
    }

    /** 注册表条目：owner 弱引用 + BD 订阅句柄 */
    private static final class Entry {
        final WeakReference<Object> ownerRef;
        final AutoCloseable handle;

        Entry(Object owner, AutoCloseable handle) {
            this.ownerRef = new WeakReference<>(owner);
            this.handle = handle;
        }

        boolean isOwner(Object owner) {
            Object ref = ownerRef.get();
            return ref != null && ref == owner;
        }

        boolean isOwnerGone() {
            return ownerRef.get() == null;
        }
    }

    /** 注册表：netId -> (owner -> entry) */
    private static final Map<Integer, Map<Object, Entry>> SUBSCRIPTIONS = new ConcurrentHashMap<>();

    private BdSubscriptionHub() {}

    /**
     * 订阅指定网络的 storage delta 事件（按网络对象）。
     * 同 owner 已订阅该网络时自动替换旧订阅。
     *
     * @return 订阅句柄（close 等价于 unsubscribe）；参数非法或订阅失败返回 null
     */
    public static AutoCloseable subscribe(DimensionsNet net, Object owner, DeltaHandler handler) {
        if (net == null || owner == null || handler == null) {
            LOGGER.warn("BdSubscriptionHub: invalid subscribe args (net={}, owner={}, handler={})",
                    net != null, owner != null, handler != null);
            return null;
        }
        return doSubscribe(net.getId(), net, owner, handler);
    }

    /** 重入保护：按 ID 订阅时若正处于订阅流程（网络加载链中），跳过本次，防止 getNetFromId 重入递归 */
    private static final ThreadLocal<Boolean> IN_SUBSCRIBE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * 订阅指定网络的 storage delta 事件（按网络 ID）。
     *
     * @return 订阅句柄；网络不存在或参数非法返回 null
     */
    public static AutoCloseable subscribe(int netId, Object owner, DeltaHandler handler) {
        if (owner == null || handler == null) return null;
        if (IN_SUBSCRIBE.get()) {
            LOGGER.warn("BdSubscriptionHub: reentrant subscribe net {} skipped (network loading)", netId);
            return null;
        }
        IN_SUBSCRIBE.set(Boolean.TRUE);
        try {
            DimensionsNet net = DimensionsNet.getNetFromId(netId);
            if (net == null) {
                LOGGER.warn("BdSubscriptionHub: cannot subscribe net {} (not found)", netId);
                return null;
            }
            return doSubscribe(netId, net, owner, handler);
        } finally {
            IN_SUBSCRIBE.remove();
        }
    }

    /** 订阅实现：先替换同 owner 旧订阅，再以 BD 弱引用订阅登记注册表 */
    private static AutoCloseable doSubscribe(int netId, DimensionsNet net, Object owner, DeltaHandler handler) {
        synchronized (SUBSCRIPTIONS) {
            Map<Object, Entry> byOwner = SUBSCRIPTIONS.computeIfAbsent(netId, k -> new HashMap<>());
            purgeGcEntries(byOwner);

            Entry old = byOwner.remove(owner);
            if (old != null) closeEntry(old);

            try {
                UnifiedStorage storage = net.getUnifiedStorage();
                if (storage == null) {
                    LOGGER.warn("BdSubscriptionHub: subscribe net {} skipped (storage not ready)", netId);
                    return null;
                }
                AutoCloseable handle = storage.subscribeDeltaWeak(owner,
                        (self, key, size, insert) -> handler.onDelta(key, size, insert));
                byOwner.put(owner, new Entry(owner, handle));
                return () -> unsubscribe(netId, owner);
            } catch (Throwable t) {
                LOGGER.warn("BdSubscriptionHub: subscribe net {} failed", netId, t);
                return null;
            }
        }
    }

    /** 退订：关闭并移除该网络下指定 owner 的订阅 */
    public static void unsubscribe(int netId, Object owner) {
        if (owner == null) return;
        synchronized (SUBSCRIPTIONS) {
            Map<Object, Entry> byOwner = SUBSCRIPTIONS.get(netId);
            if (byOwner == null) return;
            Entry entry = byOwner.remove(owner);
            if (entry != null) closeEntry(entry);
            if (byOwner.isEmpty()) SUBSCRIPTIONS.remove(netId);
        }
    }

    /** 网络销毁：关闭并移除该网络的全部订阅（幂等） */
    public static void onNetDestroyed(int netId) {
        synchronized (SUBSCRIPTIONS) {
            Map<Object, Entry> byOwner = SUBSCRIPTIONS.remove(netId);
            if (byOwner == null) return;
            for (Entry entry : byOwner.values()) closeEntry(entry);
            LOGGER.debug("BdSubscriptionHub: cleared {} subscriptions of net {}", byOwner.size(), netId);
        }
    }

    /** 服务器停止：关闭全部订阅并清空注册表 */
    public static void clearAll() {
        synchronized (SUBSCRIPTIONS) {
            int total = 0;
            for (Map<Object, Entry> byOwner : SUBSCRIPTIONS.values()) {
                total += byOwner.size();
                for (Entry entry : byOwner.values()) closeEntry(entry);
            }
            SUBSCRIPTIONS.clear();
            if (total > 0) LOGGER.debug("BdSubscriptionHub: cleared {} subscriptions", total);
        }
    }

    /** 惰性清理：移除 owner 已被 GC 的条目并关闭其句柄（防止注册表膨胀） */
    private static void purgeGcEntries(Map<Object, Entry> byOwner) {
        byOwner.entrySet().removeIf(e -> {
            if (e.getValue().isOwnerGone()) {
                closeEntry(e.getValue());
                return true;
            }
            return false;
        });
    }

    /** 关闭 BD 订阅句柄（失败仅 WARN，不影响其余订阅） */
    private static void closeEntry(Entry entry) {
        try {
            entry.handle.close();
        } catch (Exception e) {
            LOGGER.warn("BdSubscriptionHub: failed to close subscription", e);
        }
    }
}
