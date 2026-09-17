package com.solr98.beyondintegration.handler;

/**
 * 网络自定义名称提供器：由维度网络实现，
 * 用于对外提供网络的自定义显示名称（默认返回空字符串）。
 */
public interface NetworkNameProvider {
    /** 获取自定义名称，未设置时返回空字符串 */
    default String getCustomName() { return ""; }
}

