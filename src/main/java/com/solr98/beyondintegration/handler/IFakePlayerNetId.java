package com.solr98.beyondintegration.handler;

/**
 * 假玩家网络 ID 访问器：由 FakePlayer 的 mixin 实现，
 * 用于存取假玩家当前绑定的维度网络 ID。
 */
public interface IFakePlayerNetId {
    /** 获取绑定的网络 ID（-1 表示未绑定） */
    int beyond$getNetId();
    /** 设置绑定的网络 ID（-1 表示解除绑定） */
    void beyond$setNetId(int netId);
}

