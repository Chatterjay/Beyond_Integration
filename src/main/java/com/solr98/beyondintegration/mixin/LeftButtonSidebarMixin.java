package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.client.gui.BeyondSidebarAccess;
import com.wintercogs.beyonddimensions.client.gui.widget.LeftButtonSidebar;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * BD 左侧按钮栏接管：拦截 {@code LeftButtonSidebar.addButton}，
 * 按添加顺序记录所有按钮（BD 原生 + 本模组 + 其他模组），
 * 供 {@link com.solr98.beyondintegration.client.gui.LeftSidebarLayout} 统一重排与溢出分列。
 */
@Mixin(value = LeftButtonSidebar.class, remap = false)
public class LeftButtonSidebarMixin implements BeyondSidebarAccess {

    /** 按添加顺序记录的侧栏按钮 */
    @Unique private List<AbstractButton> beyond$tracked = new ArrayList<>();

    @Inject(method = "addButton", at = @At("RETURN"), remap = false)
    private void beyond$trackAdded(AbstractButton button, CallbackInfoReturnable<AbstractButton> cir) {
        AbstractButton added = cir.getReturnValue();
        if (added != null && !beyond$tracked.contains(added)) beyond$tracked.add(added);
    }

    @Override
    public List<AbstractButton> beyond$trackedButtons() {
        return beyond$tracked;
    }
}
