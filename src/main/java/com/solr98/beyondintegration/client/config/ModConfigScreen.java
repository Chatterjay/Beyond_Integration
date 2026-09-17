package com.solr98.beyondintegration.client.config;

import com.solr98.beyondintegration.CommandConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Cloth Config 配置界面：按功能模块统一分类展示服务端/客户端配置。
 * 依赖模组未加载的配置项自动隐藏，并在分类内以只读说明标记；
 * 服务端不可用的工作台（workstations.enabled 未列出）在顺序列表与服务端列表中隐藏并标记。
 */
public class ModConfigScreen {

    /** 模组是否加载（用于隐藏/标记不可用配置项） */
    private static boolean modLoaded(String id) {
        return net.neoforged.fml.ModList.get().isLoaded(id);
    }

    /** 添加只读标记说明行 */
    private static void addMark(ConfigCategory cat, ConfigEntryBuilder eb, Component text) {
        cat.addEntry(eb.startTextDescription(text.copy().withStyle(ChatFormatting.YELLOW)).build());
    }

    /** 服务端当前禁用（不在可用列表）的工作台名列表（用于标记说明） */
    private static List<String> disabledWorkstations() {
        List<String> disabled = new ArrayList<>();
        for (var m : com.solr98.beyondintegration.client.gui.WorkstationModeConstants.MODES) {
            if (!CommandConfig.isWorkstationEnabled(m.name().toLowerCase(java.util.Locale.ROOT))) {
                disabled.add(m.name());
            }
        }
        return disabled;
    }

    public static Screen createScreen(Screen parent) {
        var builder = ConfigBuilder.create().setParentScreen(parent).setTitle(Component.translatable("beyond_integration.config.title"));
        var eb = builder.entryBuilder();
        var cfg = CommandConfig.SERVER;
        final boolean hasTacz = modLoaded("tacz");
        final boolean hasSw = modLoaded("superbwarfare");
        final boolean hasYwzj = modLoaded("ywzj_vehicle");

        // ── 1. General ──
        var general = builder.getOrCreateCategory(Component.translatable("beyond_integration.config.general"));
        general.addEntry(eb.startEnumSelector(Component.translatable("beyond_integration.config.general.language"), CommandConfig.Language.class, cfg.language.get())
                .setDefaultValue(CommandConfig.Language.EN_US).setSaveConsumer(cfg.language::set).build());
        general.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.general.max_networks_per_page"), cfg.maxNetworksPerPage.get())
                .setDefaultValue(10).setMin(1).setMax(100).setSaveConsumer(cfg.maxNetworksPerPage::set).build());

        // ── 2. Client（客户端 UI 偏好；TACZ 项按加载情况隐藏+标记） ──
        var clientCat = builder.getOrCreateCategory(Component.translatable("beyond_integration.config.client"));
        if (hasTacz) {
            clientCat.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.client.smith_use_network"),
                    com.solr98.beyondintegration.ClientConfig.CLIENT.taczSmithUseNetwork.get())
                    .setDefaultValue(true)
                    .setSaveConsumer(v -> com.solr98.beyondintegration.ClientConfig.setTaczSmithUseNetwork(v)).build());
            clientCat.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.client.smith_output_network"),
                    com.solr98.beyondintegration.ClientConfig.CLIENT.taczSmithOutputToNetwork.get())
                    .setDefaultValue(false)
                    .setSaveConsumer(v -> com.solr98.beyondintegration.ClientConfig.setTaczSmithOutputToNetwork(v)).build());
        }
        // 右侧工作站切换按钮顺序/可见集（拖拽排序；服务端禁用的模式不可选且不显示）
        clientCat.addEntry(new DraggableModeListEntry(
                Component.translatable("beyond_integration.config.client.workstation_order"),
                new ArrayList<>(com.solr98.beyondintegration.ClientConfig.CLIENT.workstationOrder.get()),
                () -> Optional.of(new Component[]{Component.translatable("beyond_integration.config.client.workstation_order.tooltip")}),
                list -> com.solr98.beyondintegration.ClientConfig.setWorkstationOrder(new ArrayList<>(list)),
                () -> new ArrayList<>(Arrays.asList("ANVIL", "CUT", "GRIND", "SMITH", "CRAFT", "ENCHANT")),
                Component.translatable("text.cloth-config.reset_value")));
        List<String> disabledWs = disabledWorkstations();
        if (!disabledWs.isEmpty()) {
            addMark(clientCat, eb, Component.translatable(
                    "beyond_integration.config.client.workstation_order.disabled", String.join(", ", disabledWs)));
        }
        if (!hasTacz) {
            addMark(clientCat, eb, Component.translatable("beyond_integration.config.hidden.tacz"));
        }
        // 预览开关（客户端偏好；是否可用由服务端 enchantPreviewEnabled 决定）
        clientCat.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.client.enchant_preview_on"),
                com.solr98.beyondintegration.ClientConfig.CLIENT.enchantPreviewOn.get())
                .setDefaultValue(true)
                .setSaveConsumer(v -> com.solr98.beyondintegration.ClientConfig.setEnchantPreviewOn(v)).build());

        // ── 3. Workstations（服务端可用列表） ──
        var workstation = builder.getOrCreateCategory(Component.translatable("beyond_integration.config.workstation"));
        workstation.addEntry(eb.startStrList(Component.translatable("beyond_integration.config.workstation.enabled"),
                new ArrayList<>(cfg.WORKSTATIONS_ENABLED.get()))
                // storage 实为 BD 终端界面（非工作台），不在可用列表内
                .setDefaultValue(Arrays.asList("craft", "anvil", "cut", "grind", "smith", "enchant"))
                .setSaveConsumer(list -> cfg.WORKSTATIONS_ENABLED.set(new ArrayList<>(list))).build());
        // 献祭激活（可选平衡项）：开启后未激活的工作台点击=献祭而非打开
        workstation.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.workstation.activation_enable"),
                cfg.WORKSTATION_ACTIVATION_ENABLED.get())
                .setDefaultValue(false)
                .setSaveConsumer(cfg.WORKSTATION_ACTIVATION_ENABLED::set).build());
        workstation.addEntry(eb.startStrList(Component.translatable("beyond_integration.config.workstation.activation_costs"),
                new ArrayList<>(cfg.WORKSTATION_ACTIVATION_COSTS.get()))
                .setDefaultValue(Arrays.asList(
                        "anvil:minecraft:anvil:1",
                        "cut:minecraft:stonecutter:1",
                        "grind:minecraft:grindstone:1",
                        "smith:minecraft:smithing_table:1",
                        "enchant:minecraft:enchanting_table:1"))
                .setSaveConsumer(list -> cfg.WORKSTATION_ACTIVATION_COSTS.set(new ArrayList<>(list))).build());
        if (!disabledWs.isEmpty()) {
            addMark(workstation, eb, Component.translatable(
                    "beyond_integration.config.workstation.disabled_now", String.join(", ", disabledWs)));
        }

        // ── 4. Enchant Table（附魔台工作站；神化项按加载情况隐藏+标记） ──
        var enchant = builder.getOrCreateCategory(Component.translatable("beyond_integration.config.enchant_table"));
        enchant.addEntry(eb.startEnumSelector(Component.translatable("beyond_integration.config.enchant.power_mode"),
                CommandConfig.EnchantPowerMode.class, cfg.enchantPowerMode.get())
                .setDefaultValue(CommandConfig.EnchantPowerMode.FIXED).setSaveConsumer(cfg.enchantPowerMode::set).build());
        enchant.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.enchant.fixed_power"), cfg.enchantFixedPower.get())
                .setDefaultValue(15).setMin(0).setMax(100).setSaveConsumer(cfg.enchantFixedPower::set).build());
        enchant.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.enchant.xp_per_power"), cfg.enchantXpPerPower.get())
                .setDefaultValue(100).setMin(1).setMax(Integer.MAX_VALUE).setSaveConsumer(cfg.enchantXpPerPower::set).build());
        enchant.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.enchant.level_per_power"), cfg.enchantLevelPerPower.get())
                .setDefaultValue(1).setMin(1).setMax(Integer.MAX_VALUE).setSaveConsumer(cfg.enchantLevelPerPower::set).build());
        // 神化附魔功能暂未完成：相关配置项无条件隐藏并标记（不随 Apothic 是否加载显示）
        addMark(enchant, eb, Component.translatable("beyond_integration.config.hidden.apoth"));
        enchant.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.enchant.ignore_enchanted"), cfg.enchantIgnoreEnchanted.get())
                .setDefaultValue(false).setSaveConsumer(cfg.enchantIgnoreEnchanted::set).build());
        enchant.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.enchant.ignore_conflict"), cfg.enchantIgnoreConflict.get())
                .setDefaultValue(false).setSaveConsumer(cfg.enchantIgnoreConflict::set).build());
        enchant.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.enchant.no_lapis"), cfg.enchantNoLapis.get())
                .setDefaultValue(false).setSaveConsumer(cfg.enchantNoLapis::set).build());
        enchant.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.enchant.level_gate_ignore"), cfg.enchantLevelGateIgnore.get())
                .setDefaultValue(false).setSaveConsumer(cfg.enchantLevelGateIgnore::set).build());
        enchant.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.enchant.uncap_power"), cfg.enchantUncapPower.get())
                .setDefaultValue(false).setSaveConsumer(cfg.enchantUncapPower::set).build());
        enchant.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.enchant.cost_percent"), cfg.enchantCostPercent.get())
                .setDefaultValue(100).setMin(10).setMax(500).setSaveConsumer(cfg.enchantCostPercent::set).build());
        enchant.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.enchant.preview_enabled"), cfg.enchantPreviewEnabled.get())
                .setDefaultValue(true).setSaveConsumer(cfg.enchantPreviewEnabled::set).build());
        enchant.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.enchant.refresh_enabled"), cfg.enchantRefreshEnabled.get())
                .setDefaultValue(true).setSaveConsumer(cfg.enchantRefreshEnabled::set).build());
        enchant.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.enchant.refresh_lapis"), cfg.enchantRefreshLapis.get())
                .setDefaultValue(1).setMin(0).setMax(64).setSaveConsumer(cfg.enchantRefreshLapis::set).build());
        enchant.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.enchant.allow_treasure"), cfg.enchantAllowTreasure.get())
                .setDefaultValue(false).setSaveConsumer(cfg.enchantAllowTreasure::set).build());

        // ── 5. Anvil ──
        var anvil = builder.getOrCreateCategory(Component.translatable("beyond_integration.config.anvil"));
        anvil.addEntry(eb.startEnumSelector(Component.translatable("beyond_integration.config.anvil.cost_mode"), CommandConfig.AnvilChargeMode.class, cfg.anvilCostMode.get())
                .setDefaultValue(CommandConfig.AnvilChargeMode.LEVEL).setSaveConsumer(cfg.anvilCostMode::set).build());
        anvil.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.anvil.level_cap"), cfg.anvilLevelCap.get())
                .setDefaultValue(30).setMin(0).setMax(1000).setSaveConsumer(cfg.anvilLevelCap::set).build());
        anvil.addEntry(eb.startLongField(Component.translatable("beyond_integration.config.anvil.points_cap"), cfg.anvilPointsCap.get())
                .setDefaultValue(5000L).setMin(0).setMax(Long.MAX_VALUE).setSaveConsumer(cfg.anvilPointsCap::set).build());
        anvil.addEntry(eb.startEnumSelector(Component.translatable("beyond_integration.config.anvil.break_level_mode"), CommandConfig.BreakLevelMode.class, cfg.anvilBreakLevelMode.get())
                .setDefaultValue(CommandConfig.BreakLevelMode.OFF).setSaveConsumer(cfg.anvilBreakLevelMode::set).build());
        anvil.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.anvil.ignore_conflict"), cfg.anvilIgnoreConflict.get())
                .setDefaultValue(false).setSaveConsumer(cfg.anvilIgnoreConflict::set).build());
        anvil.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.anvil.ignore_support"), cfg.anvilIgnoreSupport.get())
                .setDefaultValue(false).setSaveConsumer(cfg.anvilIgnoreSupport::set).build());
        anvil.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.anvil.unrestricted"), cfg.anvilUnrestricted.get())
                .setDefaultValue(false).setSaveConsumer(cfg.anvilUnrestricted::set).build());
        anvil.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.anvil.conflict_penalty"), cfg.anvilConflictPenalty.get())
                .setDefaultValue(2).setMin(0).setMax(1000000).setSaveConsumer(cfg.anvilConflictPenalty::set).build());
        anvil.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.anvil.support_penalty"), cfg.anvilSupportPenalty.get())
                .setDefaultValue(5).setMin(0).setMax(1000000).setSaveConsumer(cfg.anvilSupportPenalty::set).build());
        anvil.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.anvil.conflict_percent"), cfg.anvilConflictPercent.get())
                .setDefaultValue(0).setMin(0).setMax(1000000).setSaveConsumer(cfg.anvilConflictPercent::set).build());
        anvil.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.anvil.support_percent"), cfg.anvilSupportPercent.get())
                .setDefaultValue(0).setMin(0).setMax(1000000).setSaveConsumer(cfg.anvilSupportPercent::set).build());
        anvil.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.anvil.break_level_percent"), cfg.anvilBreakLevelPercent.get())
                .setDefaultValue(0).setMin(0).setMax(1000000).setSaveConsumer(cfg.anvilBreakLevelPercent::set).build());
        anvil.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.anvil.unrestricted_percent"), cfg.anvilUnrestrictedPercent.get())
                .setDefaultValue(100).setMin(0).setMax(1000000).setSaveConsumer(cfg.anvilUnrestrictedPercent::set).build());

        // ── 6. Vehicle（依赖 Superb Warfare） ──
        var vehicle = builder.getOrCreateCategory(Component.translatable("beyond_integration.config.vehicle"));
        if (hasSw) {
            vehicle.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.vehicle.energyChargeRate"), cfg.swVehicleEnergyChargeRate.get())
                    .setDefaultValue(500000).setMin(0).setMax(Integer.MAX_VALUE).setSaveConsumer(cfg.swVehicleEnergyChargeRate::set).build());
            vehicle.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.vehicle.chargeInterval"), cfg.swVehicleChargeInterval.get())
                    .setDefaultValue(20).setMin(1).setMax(1200).setSaveConsumer(cfg.swVehicleChargeInterval::set).build());
            vehicle.addEntry(eb.startDoubleField(Component.translatable("beyond_integration.config.vehicle.chargePercentage"), cfg.swVehicleChargePercentage.get())
                    .setDefaultValue(0.0).setMin(0.0).setMax(100.0).setSaveConsumer(cfg.swVehicleChargePercentage::set).build());
        } else {
            addMark(vehicle, eb, Component.translatable("beyond_integration.config.hidden.sw"));
        }

        // ── 7. Ywzj Vehicle（依赖 ywzj_vehicle） ──
        var ywzj = builder.getOrCreateCategory(Component.translatable("beyond_integration.config.ywzj_vehicle"));
        if (hasYwzj) {
            ywzj.addEntry(eb.startEnumSelector(Component.translatable("beyond_integration.config.ywzj_vehicle.chargeMode"), CommandConfig.ChargeMode.class, cfg.ywzjChargeMode.get())
                    .setDefaultValue(CommandConfig.ChargeMode.FLAT_RATE).setSaveConsumer(cfg.ywzjChargeMode::set).build());
            ywzj.addEntry(eb.startEnumSelector(Component.translatable("beyond_integration.config.ywzj_vehicle.fuelSource"), CommandConfig.FuelSource.class, cfg.ywzjFuelSource.get())
                    .setDefaultValue(CommandConfig.FuelSource.FE).setSaveConsumer(cfg.ywzjFuelSource::set).build());
            ywzj.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.ywzj_vehicle.energyChargeRate"), cfg.ywzjVehicleEnergyChargeRate.get())
                    .setDefaultValue(500000).setMin(0).setMax(Integer.MAX_VALUE).setSaveConsumer(cfg.ywzjVehicleEnergyChargeRate::set).build());
            ywzj.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.ywzj_vehicle.chargeInterval"), cfg.ywzjVehicleChargeInterval.get())
                    .setDefaultValue(20).setMin(1).setMax(1200).setSaveConsumer(cfg.ywzjVehicleChargeInterval::set).build());
            ywzj.addEntry(eb.startDoubleField(Component.translatable("beyond_integration.config.ywzj_vehicle.chargePercentage"), cfg.ywzjVehicleChargePercentage.get())
                    .setDefaultValue(5.0).setMin(0.1).setMax(100.0).setSaveConsumer(cfg.ywzjVehicleChargePercentage::set).build());
            ywzj.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.ywzj_vehicle.energyConversion"), cfg.ywzjVehicleEnergyConversion.get())
                    .setDefaultValue(1000).setMin(1).setMax(Integer.MAX_VALUE).setSaveConsumer(cfg.ywzjVehicleEnergyConversion::set).build());
        } else {
            addMark(ywzj, eb, Component.translatable("beyond_integration.config.hidden.ywzj"));
        }

        // ── 8. Ammo（依赖 TACZ / Superb Warfare） ──
        var ammo = builder.getOrCreateCategory(Component.translatable("beyond_integration.config.ammo"));
        if (hasTacz) {
            ammo.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.ammo.tacz_poll_enabled"), cfg.TACZ_AMMO_POLL_ENABLED.get())
                    .setDefaultValue(true).setSaveConsumer(cfg.TACZ_AMMO_POLL_ENABLED::set).build());
            ammo.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.ammo.tacz_poll_interval"), cfg.TACZ_AMMO_POLL_INTERVAL_TICKS.get())
                    .setDefaultValue(10).setMin(1).setMax(200).setSaveConsumer(cfg.TACZ_AMMO_POLL_INTERVAL_TICKS::set).build());
        }
        if (hasSw) {
            ammo.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.ammo.sw_poll_enabled"), cfg.SW_AMMO_POLL_ENABLED.get())
                    .setDefaultValue(true).setSaveConsumer(cfg.SW_AMMO_POLL_ENABLED::set).build());
            ammo.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.ammo.sw_poll_interval"), cfg.SW_AMMO_POLL_INTERVAL_TICKS.get())
                    .setDefaultValue(10).setMin(1).setMax(200).setSaveConsumer(cfg.SW_AMMO_POLL_INTERVAL_TICKS::set).build());
        }
        if (!hasTacz) addMark(ammo, eb, Component.translatable("beyond_integration.config.hidden.tacz"));
        if (!hasSw) addMark(ammo, eb, Component.translatable("beyond_integration.config.hidden.sw"));

        // ── 物品自动充电（装备位 / 饰品） ──
        var energyCharge = builder.getOrCreateCategory(Component.translatable("beyond_integration.config.energy_charge"));
        energyCharge.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.energy_ammo.charge_enabled"), cfg.ENERGY_AMMO_CHARGE_ENABLED.get())
                .setDefaultValue(true).setSaveConsumer(cfg.ENERGY_AMMO_CHARGE_ENABLED::set).build());
        energyCharge.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.energy_ammo.charge_interval"), cfg.ENERGY_AMMO_CHARGE_INTERVAL.get())
                .setDefaultValue(20).setMin(1).setMax(1200).setSaveConsumer(cfg.ENERGY_AMMO_CHARGE_INTERVAL::set).build());
        energyCharge.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.energy_ammo.charge_rate"), cfg.ENERGY_AMMO_CHARGE_RATE.get())
                .setDefaultValue(10000).setMin(1).setMax(Integer.MAX_VALUE).setSaveConsumer(cfg.ENERGY_AMMO_CHARGE_RATE::set).build());
        energyCharge.addEntry(eb.startEnumSelector(Component.translatable("beyond_integration.config.energy_ammo.charge_mode"),
                CommandConfig.EnergyChargeMode.class, cfg.ENERGY_AMMO_CHARGE_MODE.get())
                .setDefaultValue(CommandConfig.EnergyChargeMode.RATE).setSaveConsumer(cfg.ENERGY_AMMO_CHARGE_MODE::set).build());
        energyCharge.addEntry(eb.startDoubleField(Component.translatable("beyond_integration.config.energy_ammo.charge_percentage"), cfg.ENERGY_AMMO_CHARGE_PERCENTAGE.get())
                .setDefaultValue(10.0).setMin(0.0).setMax(100.0).setSaveConsumer(cfg.ENERGY_AMMO_CHARGE_PERCENTAGE::set).build());
        energyCharge.addEntry(eb.startStrList(Component.translatable("beyond_integration.config.energy_ammo.charge_whitelist"), new ArrayList<>(cfg.ENERGY_AMMO_CHARGE_WHITELIST.get()))
                .setDefaultValue(Arrays.asList()).setSaveConsumer(list -> cfg.ENERGY_AMMO_CHARGE_WHITELIST.set(new ArrayList<>(list))).build());
        energyCharge.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.energy_ammo.charge_curios"), cfg.ENERGY_AMMO_CHARGE_CURIOS.get())
                .setDefaultValue(true).setSaveConsumer(cfg.ENERGY_AMMO_CHARGE_CURIOS::set).build());
        energyCharge.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.energy_ammo.charge_maid_baubles"), cfg.ENERGY_AMMO_CHARGE_MAID_BAUBLES.get())
                .setDefaultValue(true).setSaveConsumer(cfg.ENERGY_AMMO_CHARGE_MAID_BAUBLES::set).build());

        // ── 9. Auto Totem ──
        var totem = builder.getOrCreateCategory(Component.translatable("beyond_integration.config.totem"));
        totem.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.totem.enabled"), cfg.AUTO_TOTEM_ENABLED.get())
                .setDefaultValue(true).setSaveConsumer(cfg.AUTO_TOTEM_ENABLED::set).build());
        totem.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.totem.respect_bypasses"), cfg.AUTO_TOTEM_RESPECT_BYPASSES.get())
                .setDefaultValue(false).setSaveConsumer(cfg.AUTO_TOTEM_RESPECT_BYPASSES::set).build());
        totem.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.totem.restore_max_health"), cfg.AUTO_TOTEM_RESTORE_MAX_HEALTH.get())
                .setDefaultValue(true).setSaveConsumer(cfg.AUTO_TOTEM_RESTORE_MAX_HEALTH::set).build());
        totem.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.totem.heal_to_full"), cfg.AUTO_TOTEM_HEAL_TO_FULL.get())
                .setDefaultValue(false).setSaveConsumer(cfg.AUTO_TOTEM_HEAL_TO_FULL::set).build());
        totem.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.totem.cooldown"), cfg.AUTO_TOTEM_COOLDOWN_SECONDS.get())
                .setDefaultValue(60).setMin(0).setMax(3600).setSaveConsumer(cfg.AUTO_TOTEM_COOLDOWN_SECONDS::set).build());
        totem.addEntry(eb.startStrList(Component.translatable("beyond_integration.config.totem.damage_blacklist"), new ArrayList<>(cfg.AUTO_TOTEM_DAMAGE_BLACKLIST.get()))
                .setDefaultValue(Arrays.asList("minecraft:out_of_world"))
                .setSaveConsumer(list -> cfg.AUTO_TOTEM_DAMAGE_BLACKLIST.set(new ArrayList<>(list))).build());

        // ── 10. Blacklist ──
        var blacklist = builder.getOrCreateCategory(Component.translatable("beyond_integration.config.blacklist"));
        blacklist.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.blacklist.enable"), cfg.ENABLE_ITEM_BLACKLIST.get())
                .setDefaultValue(false).setSaveConsumer(cfg.ENABLE_ITEM_BLACKLIST::set).build());
        blacklist.addEntry(eb.startStrList(Component.translatable("beyond_integration.config.blacklist.items"), new ArrayList<>(cfg.ITEM_BLACKLIST.get()))
                .setDefaultValue(Arrays.asList("minecraft:barrier", "minecraft:command_block"))
                .setSaveConsumer(list -> cfg.ITEM_BLACKLIST.set(new ArrayList<>(list))).build());

        // ── 11. BD 修改（经验棒：等级上限 / 分批 / 直设） ──
        var xpRod = builder.getOrCreateCategory(Component.translatable("beyond_integration.config.bd_tweaks"));
        xpRod.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.bd_tweaks.xp_rod_enabled"), cfg.xpRodTweaksEnabled.get())
                .setDefaultValue(true).setSaveConsumer(cfg.xpRodTweaksEnabled::set).build());
        xpRod.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.bd_tweaks.furnace_terminal_smelt_all"), cfg.furnaceTerminalSmeltAllEnabled.get())
                .setDefaultValue(false).setSaveConsumer(cfg.furnaceTerminalSmeltAllEnabled::set).build());
        xpRod.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.xp_rod.max_target_level"), cfg.xpRodMaxTargetLevel.get())
                .setDefaultValue(238609312).setMin(0).setMax(Integer.MAX_VALUE).setSaveConsumer(cfg.xpRodMaxTargetLevel::set).build());
        xpRod.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.xp_rod.grant_batch_size"), cfg.xpRodGrantBatchSize.get())
                .setDefaultValue(Integer.MAX_VALUE - 1).setMin(0).setMax(Integer.MAX_VALUE).setSaveConsumer(cfg.xpRodGrantBatchSize::set).build());
        xpRod.addEntry(eb.startEnumSelector(Component.translatable("beyond_integration.config.xp_rod.grant_mode"),
                CommandConfig.XpGrantMode.class, cfg.xpRodGrantMode.get())
                .setDefaultValue(CommandConfig.XpGrantMode.BATCH).setSaveConsumer(cfg.xpRodGrantMode::set).build());

        // ── 12. 附魔分离（与附魔台同源但功能不同，置于后方） ──
        var enchSep = builder.getOrCreateCategory(Component.translatable("beyond_integration.config.enchant"));
        enchSep.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.enchant.separation"), cfg.enchantSeparation.get())
                .setDefaultValue(false).setSaveConsumer(cfg.enchantSeparation::set).build());
        enchSep.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.enchant.base_cost"), cfg.enchantBaseCost.get())
                .setDefaultValue(5).setMin(0).setMax(100).setSaveConsumer(cfg.enchantBaseCost::set).build());
        enchSep.addEntry(eb.startDoubleField(Component.translatable("beyond_integration.config.enchant.level_mult"), cfg.enchantLevelMult.get())
                .setDefaultValue(1.0).setMin(0.0).setMax(100.0).setSaveConsumer(cfg.enchantLevelMult::set).build());
        enchSep.addEntry(eb.startDoubleField(Component.translatable("beyond_integration.config.enchant.default_mult"), cfg.enchantDefaultMult.get())
                .setDefaultValue(1.0).setMin(0.0).setMax(100.0).setSaveConsumer(cfg.enchantDefaultMult::set).build());
        enchSep.addEntry(eb.startStrList(Component.translatable("beyond_integration.config.enchant.high_cost"), new ArrayList<>(cfg.enchantHighCostList.get()))
                .setDefaultValue(Arrays.asList("minecraft:mending:3.0", "minecraft:frost_walker:3.0",
                        "minecraft:sharpness:1.2", "minecraft:protection:1.2"))
                .setSaveConsumer(list -> cfg.enchantHighCostList.set(new ArrayList<>(list))).build());

        return builder.build();
    }
}
