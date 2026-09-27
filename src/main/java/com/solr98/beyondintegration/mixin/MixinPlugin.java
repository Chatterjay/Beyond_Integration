package com.solr98.beyondintegration.mixin;
import net.neoforged.fml.ModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;

/**
 * Mixin 配置插件：根据已加载的模组动态决定各 Mixin 是否应用。
 * 目的：所有注入目标属于可选模组（Superb Warfare / TacZ / 哨戒机械臂 / 载具）时，
 * 仅在该模组存在的情况下应用对应 Mixin，避免缺少模组时加载失败。
 */
public class MixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }

    // 按目标类所在模组判断：模组未加载则跳过对应 Mixin
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        var modList = ModList.get();
        if (modList == null) return true;
        // EmiLink 与本模组争夺同一批 EMI / BD-EMI 交互钩子（网络槽提取、Space 批量转移、
        // 结果槽批量合成、合成网格清理）。检测到 EmiLink 时本模组对所有 EMI 目标类一律不注入，
        // 由 EmiLink 独占，避免同一处点击被两个模组重复处理。
        // 常量在编译期内联，此处不会触发 EmiBdCompat 的类加载。
        if (targetClassName.startsWith("dev.emi.emi.")
                || targetClassName.startsWith("com.wintercogs.beyonddimensions.integration.module.emi.")) {
            return !modList.isLoaded(com.solr98.beyondintegration.compat.EmiBdCompat.CONFLICT_MOD_ID);
        }
        if (targetClassName.startsWith("com.atsuishio.superbwarfare.")) return modList.isLoaded("superbwarfare");
        if (targetClassName.startsWith("com.tacz.guns.")) return modList.isLoaded("tacz");
        if (targetClassName.startsWith("com.mafuyu404.taczaddon.")) {
            // NeoForge 侧 taczaddon 自 1.1.8.2 起大幅重构，仅重构后的版本结构一致；
            // 旧版（1.1.8 / 1.1.8-alpha / 1.1.8-fix / 1.1.8-fix2）注入点不同，不注入
            return modList.isLoaded("taczaddon")
                    && modList.getModContainerById("taczaddon")
                    .map(c -> isTaczAddonCompatible(c.getModInfo().getVersion().toString()))
                    .orElse(false);
        }
        if (targetClassName.startsWith("euphy.upo.sentrymechanicalarm.")) return modList.isLoaded("sentrymechanicalarm");
        if (targetClassName.startsWith("org.ywzj.vehicle.")) return modList.isLoaded("ywzj_vehicle");
        // JEI 集成（物品数量角标/点击取物品）仅在 JEI 加载时应用；
        // 检测到 rs_integration（RI）时让路禁用，避免与其同类功能冲突
        if (targetClassName.startsWith("mezz.jei.")) return modList.isLoaded("jei") && !modList.isLoaded("rs_integration");
        // FTB Quests 集成（网络物品计入任务/消耗型补足/奖励进网络）仅在 FTB Quests 加载时应用；
        // 检测到 rs_integration（RI）时让路禁用，避免与其同类功能冲突
        if (targetClassName.startsWith("dev.ftb.mods.ftbquests.")) return modList.isLoaded("ftbquests") && !modList.isLoaded("rs_integration");
        return true;
    }

    /**
     * taczaddon 版本兼容判定（NeoForge 侧）：接受 1.1.8.2 起的重构版本
     * （1.1.8.2 / 1.1.8.3 / 1.1.8.10 及带后缀版本）；旧结构版本不注入。
     */
    private static boolean isTaczAddonCompatible(String version) {
        if (version == null || version.isEmpty()) return false;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^1\\.1\\.8\\.(\\d+)").matcher(version);
        if (!m.find()) return false;
        try {
            return Integer.parseInt(m.group(1)) >= 2;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return List.of(); }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}

