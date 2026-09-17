package com.solr98.beyondintegration.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.solr98.beyondintegration.jei.BeyondJeiNetworkHelper;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.input.CombinedRecipeFocusSource;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.input.handlers.NullInputHandler;
import mezz.jei.gui.input.handlers.SameElementInputHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * JEI 点击取物品（在 JEI 点击处理入口 {@code FocusInputHandler#handleClick} 处拦截）：
 * 打开 BD 终端时，<b>Shift+左键</b>从网络取一组、<b>Shift+右键</b>取 1 个到背包；
 * 无库存或未打开终端时放行给 JEI 默认行为（显示配方/用途）。
 * <p>
 * 注意两点：
 * <ul>
 *   <li>JEI 鼠标点击的 {@code UserInput.modifiers} 恒为 0，Shift 判定必须用 {@link Screen#hasShiftDown()}；</li>
 *   <li>SIMULATE 阶段返回同元素处理器（{@link SameElementInputHandler}），
 *       EXECUTE 阶段才会回到本处理器真正发包（JEI 只把执行阶段发给模拟阶段接管的处理器）。</li>
 * </ul>
 * JEI 未安装时（@Pseudo）自动跳过；JEI 内部结构变化时 require=0 静默失效。
 */
@Pseudo
@Mixin(targets = "mezz.jei.gui.input.handlers.FocusInputHandler", remap = false)
public class JeiFocusInputMixin {

    /** JEI 的"鼠标下原料"来源（用于定位被点击的物品条目） */
    @Shadow(remap = false) private CombinedRecipeFocusSource focusSource;

    @Inject(method = "handleClick", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void beyond$pullFromNetwork(UserInput input, IInternalKeyMappings keyBindings,
                                        CallbackInfoReturnable<Optional<IUserInputHandler>> cir) {
        try {
            if (input.getKey().getType() != InputConstants.Type.MOUSE) return;
            // 鼠标点击的 UserInput.modifiers 恒为 0，Shift 需取当前键盘状态
            if (!Screen.hasShiftDown()) return;
            int button = input.getKey().getValue();
            if (button != 0 && button != 1) return;

            for (IClickableIngredientInternal<?> clicked : focusSource.getIngredientUnderMouse(input, keyBindings).toList()) {
                // 仅处理 JEI 物品列表/书签列表条目（GUI 内槽位的 canClickToFocus=false，避免误判终端内的物品）
                if (!clicked.canClickToFocus()) continue;
                ItemStack stack = clicked.getElement().getTypedIngredient()
                        .getItemStack().orElse(ItemStack.EMPTY);
                if (stack.isEmpty()) continue;
                if (BeyondJeiNetworkHelper.getNetworkCount(stack) <= 0) continue;

                if (input.isSimulate()) {
                    // 模拟阶段：锁定到同元素处理器，EXECUTE 阶段会回到本处理器
                    cir.setReturnValue(Optional.of(new SameElementInputHandler(
                            (IUserInputHandler) (Object) this, clicked::isMouseOver)));
                } else {
                    // 执行阶段：真正发包（Shift+左键一组 / Shift+右键 1 个）
                    int amount = button == 0 ? Math.max(1, stack.getMaxStackSize()) : 1;
                    BeyondJeiNetworkHelper.requestExtract(stack, amount);
                    cir.setReturnValue(Optional.of(NullInputHandler.INSTANCE));
                }
                return;
            }
        } catch (Throwable ignored) {}
    }
}
