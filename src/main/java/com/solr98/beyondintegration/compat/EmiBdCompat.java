package com.solr98.beyondintegration.compat;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * BD 终端快捷操作（EMI 联动）的总开关。
 *
 * <p>两重门控，任一不满足即整套 BD 终端快捷操作<strong>整体不注册</strong>：</p>
 * <ol>
 *   <li><b>EmiLink 不能存在</b>——EmiLink 在自己的 BD 集成里挂钩了同一批 BD 界面与网络存储
 *       （网络槽 Shift+点击提取、Space+点击批量转移、结果槽 Space+点击批量合成、合成网格清理）。
 *       两个模组同时挂同一批界面的 {@code ScreenEvent.MouseButtonPressed.Pre} 会各自取消事件并各自发包，
 *       造成同一次点击被处理两次（双倍取物 / 双倍合成 / 取物与存物互相抵消）。
 *       因此一旦检测到 EmiLink，本模组直接让路，由 EmiLink 独占这些交互。</li>
 *   <li><b>EMI 必须存在</b>——这组快捷操作是 "EMI ↔ BD 终端" 联动的一部分
 *       （EMI 悬浮物品 / 侧栏与 BD 终端的协同）。缺少 EMI 时整个子系统没有意义。</li>
 * </ol>
 *
 * <p>注意：本条只控制"BD 终端快捷操作"。本模组其它的 JEI/BD 集成
 * （JEI 数量角标、JEI 点击取物 {@code ExtractNetworkItemPacket}、女仆网络、FTB 任务等）
 * 不受 EmiLink 影响，仍然照常工作。</p>
 */
public final class EmiBdCompat {

    /** 会与本模组争夺 BD 终端交互所有权的模组 ID。 */
    public static final String CONFLICT_MOD_ID = "emilink";
    /** 这组交互所依赖的物品管理器模组 ID。 */
    public static final String REQUIRED_MOD_ID = "emi";

    private static final Logger LOGGER = LogUtils.getLogger();
    /** 门控结果缓存：模组列表在运行期不会变化，只需判定一次。 */
    private static Boolean enabledCache;
    /** 是否已经输出过判定说明，避免重复刷日志。 */
    private static boolean logged;

    private EmiBdCompat() {}

    /** 是否允许注册 BD 终端快捷操作（EMI 存在 且 EmiLink 不存在）。 */
    public static boolean enabled() {
        if (enabledCache == null) {
            var modList = ModList.get();
            // 模组列表尚未就绪（极早期调用）：保守判定为不可用，不做缓存，等就绪后再判定
            if (modList == null) return false;
            boolean emi = modList.isLoaded(REQUIRED_MOD_ID);
            boolean conflict = modList.isLoaded(CONFLICT_MOD_ID);
            enabledCache = emi && !conflict;
            if (!logged) {
                logged = true;
                if (conflict) {
                    LOGGER.info("[beyond_integration] 检测到 {}，BD 终端快捷操作整体让路（避免与 EmiLink 重复处理同一次点击）",
                            CONFLICT_MOD_ID);
                } else if (!emi) {
                    LOGGER.info("[beyond_integration] 未检测到 {}，跳过 BD 终端快捷操作注册", REQUIRED_MOD_ID);
                } else {
                    LOGGER.info("[beyond_integration] BD 终端快捷操作已启用（提取 / 批量转移 / 批量合成 / 网格清理）");
                }
            }
        }
        return enabledCache;
    }

    /** 是否检测到与 EmiLink 的功能冲突（冲突时本模组放弃 BD 终端交互）。 */
    public static boolean conflictDetected() {
        var modList = ModList.get();
        return modList != null && modList.isLoaded(CONFLICT_MOD_ID);
    }
}
