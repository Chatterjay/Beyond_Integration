package com.solr98.beyondintegration.client.gui;

import net.minecraft.client.gui.components.AbstractButton;

import java.util.List;

/**
 * BD 左侧按钮栏（LeftButtonSidebar）的接管访问接口。
 * 由 LeftButtonSidebarMixin 实现，暴露 addButton 拦截记录的全部按钮（按添加顺序）。
 */
public interface BeyondSidebarAccess {
    /** 侧栏中按添加顺序记录的全部按钮 */
    List<AbstractButton> beyond$trackedButtons();
}
