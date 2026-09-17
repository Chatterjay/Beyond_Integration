package com.solr98.beyondintegration;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import com.solr98.beyondintegration.core.config.ConfigCommentLang;

import java.util.List;

/**
 * 模组客户端配置（ModConfigSpec，CLIENT 类型，仅客户端生效）。
 * 记录客户端 UI 偏好：TACZ 枪械工作台的"网络模式/原版模式"等开关。
 */
public class ClientConfig {

    public static final ModConfigSpec CLIENT_SPEC;
    public static final ClientValues CLIENT;

    static {
        final Pair<ClientValues, ModConfigSpec> specPair =
                new ModConfigSpec.Builder().configure(ClientValues::new);
        CLIENT_SPEC = specPair.getRight();
        CLIENT = specPair.getLeft();
    }

    public static class ClientValues {
        /** TACZ 枪械工作台是否使用网络材料模式（默认开启） */
        public final ModConfigSpec.BooleanValue taczSmithUseNetwork;
        /** TACZ 枪械工作台合成产物是否输出到网络（默认关闭） */
        public final ModConfigSpec.BooleanValue taczSmithOutputToNetwork;
        /** 工作站归还方向按钮持久化：关闭/清空时物品的优先归还方向（false=背包优先，true=网络优先） */
        public final ModConfigSpec.BooleanValue workstationReturnToStorage;
        /** 附魔台工作站模式按钮持久化：true=神化(Apothic)模式，false=原版模式（默认） */
        public final ModConfigSpec.BooleanValue enchantTableApothMode;
        /** 右侧工作站切换按钮的顺序/可见集（有序枚举名列表；可隐藏或重排） */
        public final ModConfigSpec.ConfigValue<List<? extends String>> workstationOrder;
        /** 附魔台悬停预览开关（默认开）：悬停费用按钮直接显示将获得的附魔列表 */
        public final ModConfigSpec.BooleanValue enchantPreviewOn;

        ClientValues(ModConfigSpec.Builder builder) {
            taczSmithUseNetwork = builder
                    .comment(ConfigCommentLang.comment("tacz_smith_use_network"))
                    .define("tacz_smith_use_network", true);
            taczSmithOutputToNetwork = builder
                    .comment(ConfigCommentLang.comment("tacz_smith_output_to_network"))
                    .define("tacz_smith_output_to_network", false);
            workstationReturnToStorage = builder
                    .comment(ConfigCommentLang.comment("workstation_return_to_storage"))
                    .define("workstation_return_to_storage", false);
            enchantTableApothMode = builder
                    .comment(ConfigCommentLang.comment("enchant_table_apoth_mode"))
                    .define("enchant_table_apoth_mode", false);
            workstationOrder = builder
                    .comment(ConfigCommentLang.comment("workstation_order"))
                    .defineList("workstation_order",
                            java.util.Arrays.asList("ANVIL", "CUT", "GRIND", "SMITH", "CRAFT", "ENCHANT"),
                            obj -> obj instanceof String);
            enchantPreviewOn = builder
                    .comment(ConfigCommentLang.comment("enchant_preview_on"))
                    .define("enchant_preview_on", true);
        }
    }

    /** 读取 TACZ 工作台网络模式（客户端配置） */
    public static boolean taczSmithUseNetwork() { return CLIENT.taczSmithUseNetwork.get(); }

    /** 写入 TACZ 工作台网络模式并保存配置 */
    public static void setTaczSmithUseNetwork(boolean v) {
        CLIENT.taczSmithUseNetwork.set(v);
        CLIENT_SPEC.save();
    }

    /** 读取 TACZ 工作台产物入网络（客户端配置） */
    public static boolean taczSmithOutputToNetwork() { return CLIENT.taczSmithOutputToNetwork.get(); }

    /** 写入 TACZ 工作台产物入网络并保存配置 */
    public static void setTaczSmithOutputToNetwork(boolean v) {
        CLIENT.taczSmithOutputToNetwork.set(v);
        CLIENT_SPEC.save();
    }

    /** 读取工作站归还方向（客户端 UI 偏好） */
    public static boolean workstationReturnToStorage() { return CLIENT.workstationReturnToStorage.get(); }

    /** 写入工作站归还方向并保存客户端配置 */
    public static void setWorkstationReturnToStorage(boolean v) {
        CLIENT.workstationReturnToStorage.set(v);
        CLIENT_SPEC.save();
    }

    /** 读取附魔台工作站模式：true=神化(Apothic)，false=原版 */
    public static boolean enchantTableApothMode() { return CLIENT.enchantTableApothMode.get(); }

    /** 写入附魔台工作站模式并保存客户端配置 */
    public static void setEnchantTableApothMode(boolean v) {
        CLIENT.enchantTableApothMode.set(v);
        CLIENT_SPEC.save();
    }

    /** 读取右侧工作站按钮顺序/可见集（有序模式名列表） */
    public static List<? extends String> workstationOrder() { return CLIENT.workstationOrder.get(); }

    /** 写入右侧工作站按钮顺序/可见集并保存客户端配置 */
    public static void setWorkstationOrder(List<? extends String> v) {
        CLIENT.workstationOrder.set(v);
        CLIENT_SPEC.save();
    }

    /** 读取附魔台悬停预览开关 */
    public static boolean enchantPreviewOn() { return CLIENT.enchantPreviewOn.get(); }

    /** 写入附魔台悬停预览开关并保存客户端配置 */
    public static void setEnchantPreviewOn(boolean v) {
        CLIENT.enchantPreviewOn.set(v);
        CLIENT_SPEC.save();
    }
}
