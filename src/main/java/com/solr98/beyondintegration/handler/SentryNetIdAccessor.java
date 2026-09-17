package com.solr98.beyondintegration.handler;

/**
 * 哨兵实体网络 ID 访问器：由哨兵实体实现，
 * 用于存取/解除哨兵绑定的维度网络 ID。
 */
public interface SentryNetIdAccessor {
    /** 获取绑定的网络 ID（-1 表示未绑定） */
    int getSentryNetId();
    /** 解除哨兵的网络绑定 */
    void clearSentryBinding();
}
