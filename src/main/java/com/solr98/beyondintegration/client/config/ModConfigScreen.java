package com.solr98.beyondintegration.client.config;

import com.solr98.beyondintegration.ClientConfig;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.client.gui.WorkstationModeConstants;
import com.solr98.beyondintegration.network.OpenStorageMenuPacket;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Cloth Config 配置界面：按功能模块统一分类展示服务端/客户端配置。
 * 依赖模组未加载的配置项自动隐藏，并在分类内以只读说明标记；
 * 服务端不可用的工作台（workstations.enabled 未列出）在顺序列表与服务端列表中隐藏并标记。
 */
public class ModConfigScreen {

    /** 模组是否加载（用于隐藏/标记不可用配置项） */
    private static boolean modLoaded(String id) {
        return net.minecraftforge.fml.ModList.get().isLoaded(id);
    }

    /** 添加只读标记说明行 */
    private static void addMark(ConfigCategory cat, ConfigEntryBuilder eb, Component text) {
        cat.addEntry(eb.startTextDescription(text.copy().withStyle(ChatFormatting.YELLOW)).build());
    }

    /** 服务端当前禁用（不在可用列表）的工作台名列表（用于标记说明） */
    private static List<String> disabledWorkstations() {
        List<String> disabled = new ArrayList<>();
        for (OpenStorageMenuPacket.Type t : WorkstationModeConstants.MODES) {
            if (!CommandConfig.isWorkstationEnabled(t.id())) disabled.add(t.name());
        }
        return disabled;
    }

    /** 创建配置界面（parent 为返回界面），通过 Cloth Config API 构建各分类条目 */
    public static Screen createScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("beyond_integration.config.title"));

        ConfigEntryBuilder eb = builder.entryBuilder();
        final boolean hasTacz = modLoaded("tacz");
        final boolean hasSw = modLoaded("superbwarfare");

        // ========== 1. 通用 ==========
        ConfigCategory general = builder.getOrCreateCategory(
                Component.translatable("beyond_integration.config.general"));
        general.addEntry(eb.startEnumSelector(
                Component.translatable("beyond_integration.config.general.language"),
                CommandConfig.Language.class,
                CommandConfig.SERVER.language.get())
                .setDefaultValue(CommandConfig.Language.EN_US)
                .setSaveConsumer(CommandConfig.SERVER.language::set)
                .build());
        general.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.general.max_page"),
                CommandConfig.SERVER.maxNetworksPerPage.get())
                .setDefaultValue(10)
                .setMin(1).setMax(100)
                .setSaveConsumer(CommandConfig.SERVER.maxNetworksPerPage::set)
                .build());

        // ========== 2. 客户端（UI 偏好） ==========
        ConfigCategory clientCat = builder.getOrCreateCategory(
                Component.translatable("beyond_integration.config.client"));

        boolean taczHidden = false;
        if (hasTacz) {
            clientCat.addEntry(eb.startBooleanToggle(
                    Component.translatable("beyond_integration.config.client.smith_use_network"),
                    ClientConfig.CLIENT.taczSmithUseNetwork.get())
                    .setDefaultValue(true)
                    .setSaveConsumer(ClientConfig::setTaczSmithUseNetwork)
                    .build());
            clientCat.addEntry(eb.startBooleanToggle(
                    Component.translatable("beyond_integration.config.client.smith_output_network"),
                    ClientConfig.CLIENT.taczSmithOutputToNetwork.get())
                    .setDefaultValue(false)
                    .setSaveConsumer(ClientConfig::setTaczSmithOutputToNetwork)
                    .build());
        } else {
            taczHidden = true;
        }

        // 右侧工作站切换按钮顺序/可见集（拖拽排序；服务端禁用的模式不可选且不显示）
        clientCat.addEntry(new DraggableModeListEntry(
                Component.translatable("beyond_integration.config.client.workstation_order"),
                new ArrayList<>(ClientConfig.workstationOrder()),
                () -> java.util.Optional.of(new Component[]{
                        Component.translatable("beyond_integration.config.client.workstation_order.tooltip")}),
                list -> ClientConfig.setWorkstationOrder(new ArrayList<>(list)),
                () -> new ArrayList<>(Arrays.asList("ANVIL", "CUT", "GRIND", "SMITH", "CRAFT", "ENCHANT")),
                Component.translatable("text.cloth-config.reset_value")));

        // 标记：服务端已禁用而从列表隐藏的工作台
        List<String> disabledWs = disabledWorkstations();
        if (!disabledWs.isEmpty()) {
            addMark(clientCat, eb, Component.translatable(
                    "beyond_integration.config.client.workstation_order.disabled",
                    String.join(", ", disabledWs)));
        }
        // 标记：依赖 TACZ 而隐藏的配置项
        if (taczHidden) {
            addMark(clientCat, eb, Component.translatable("beyond_integration.config.hidden.tacz"));
        }

        clientCat.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.client.enchant_preview"),
                ClientConfig.CLIENT.enchantPreviewOn.get())
                .setDefaultValue(true)
                .setSaveConsumer(ClientConfig::setEnchantPreviewOn)
                .build());

        // ========== 3. 工作台（服务端可用列表） ==========
        ConfigCategory workstation = builder.getOrCreateCategory(
                Component.translatable("beyond_integration.config.workstation"));
        workstation.addEntry(eb.startStrList(
                Component.translatable("beyond_integration.config.workstation.enabled"),
                new ArrayList<>(CommandConfig.SERVER.WORKSTATIONS_ENABLED.get()))
                // storage 实为 BD 终端界面（非工作台），不在可用列表内
                .setDefaultValue(Arrays.asList("craft", "anvil", "cut", "grind", "smith", "enchant"))
                .setSaveConsumer(list -> CommandConfig.SERVER.WORKSTATIONS_ENABLED.set(new ArrayList<>(list)))
                .build());
        // 献祭激活（可选平衡项）：开启后未激活的工作台点击=献祭而非打开
        workstation.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.workstation.activation_enable"),
                CommandConfig.SERVER.WORKSTATION_ACTIVATION_ENABLED.get())
                .setDefaultValue(false)
                .setSaveConsumer(CommandConfig.SERVER.WORKSTATION_ACTIVATION_ENABLED::set)
                .build());
        workstation.addEntry(eb.startStrList(
                Component.translatable("beyond_integration.config.workstation.activation_costs"),
                new ArrayList<>(CommandConfig.SERVER.WORKSTATION_ACTIVATION_COSTS.get()))
                .setDefaultValue(Arrays.asList(
                        "anvil:minecraft:anvil:1",
                        "cut:minecraft:stonecutter:1",
                        "grind:minecraft:grindstone:1",
                        "smith:minecraft:smithing_table:1",
                        "enchant:minecraft:enchanting_table:1"))
                .setSaveConsumer(list -> CommandConfig.SERVER.WORKSTATION_ACTIVATION_COSTS.set(new ArrayList<>(list)))
                .build());
        if (!disabledWs.isEmpty()) {
            addMark(workstation, eb, Component.translatable(
                    "beyond_integration.config.workstation.disabled_now", String.join(", ", disabledWs)));
        }

        // ========== 4. 附魔台工作站 ==========
        ConfigCategory enchantTable = builder.getOrCreateCategory(
                Component.translatable("beyond_integration.config.enchant_table"));
        enchantTable.addEntry(eb.startEnumSelector(
                Component.translatable("beyond_integration.config.enchant.power_mode"),
                CommandConfig.EnchantPowerMode.class,
                CommandConfig.SERVER.enchantPowerMode.get())
                .setDefaultValue(CommandConfig.EnchantPowerMode.FIXED)
                .setSaveConsumer(CommandConfig.SERVER.enchantPowerMode::set)
                .build());
        enchantTable.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.enchant.fixed_power"),
                CommandConfig.SERVER.enchantFixedPower.get())
                .setDefaultValue(15).setMin(0).setMax(100)
                .setSaveConsumer(CommandConfig.SERVER.enchantFixedPower::set)
                .build());
        enchantTable.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.enchant.xp_per_power"),
                CommandConfig.SERVER.enchantXpPerPower.get())
                .setDefaultValue(100).setMin(1).setMax(Integer.MAX_VALUE)
                .setSaveConsumer(CommandConfig.SERVER.enchantXpPerPower::set)
                .build());
        enchantTable.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.enchant.level_per_power"),
                CommandConfig.SERVER.enchantLevelPerPower.get())
                .setDefaultValue(1).setMin(1).setMax(Integer.MAX_VALUE)
                .setSaveConsumer(CommandConfig.SERVER.enchantLevelPerPower::set)
                .build());
        enchantTable.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.enchant.ignore_enchanted"),
                CommandConfig.SERVER.enchantIgnoreEnchanted.get())
                .setDefaultValue(false)
                .setSaveConsumer(CommandConfig.SERVER.enchantIgnoreEnchanted::set)
                .build());
        enchantTable.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.enchant.ignore_conflict"),
                CommandConfig.SERVER.enchantIgnoreConflict.get())
                .setDefaultValue(false)
                .setSaveConsumer(CommandConfig.SERVER.enchantIgnoreConflict::set)
                .build());
        enchantTable.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.enchant.no_lapis"),
                CommandConfig.SERVER.enchantNoLapis.get())
                .setDefaultValue(false)
                .setSaveConsumer(CommandConfig.SERVER.enchantNoLapis::set)
                .build());
        enchantTable.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.enchant.level_gate_ignore"),
                CommandConfig.SERVER.enchantLevelGateIgnore.get())
                .setDefaultValue(false)
                .setSaveConsumer(CommandConfig.SERVER.enchantLevelGateIgnore::set)
                .build());
        enchantTable.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.enchant.uncap_power"),
                CommandConfig.SERVER.enchantUncapPower.get())
                .setDefaultValue(false)
                .setSaveConsumer(CommandConfig.SERVER.enchantUncapPower::set)
                .build());
        enchantTable.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.enchant.cost_percent"),
                CommandConfig.SERVER.enchantCostPercent.get())
                .setDefaultValue(100).setMin(10).setMax(500)
                .setSaveConsumer(CommandConfig.SERVER.enchantCostPercent::set)
                .build());
        enchantTable.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.enchant.preview_enabled"),
                CommandConfig.SERVER.enchantPreviewEnabled.get())
                .setDefaultValue(true)
                .setSaveConsumer(CommandConfig.SERVER.enchantPreviewEnabled::set)
                .build());
        enchantTable.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.enchant.refresh_enabled"),
                CommandConfig.SERVER.enchantRefreshEnabled.get())
                .setDefaultValue(true)
                .setSaveConsumer(CommandConfig.SERVER.enchantRefreshEnabled::set)
                .build());
        enchantTable.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.enchant.refresh_lapis"),
                CommandConfig.SERVER.enchantRefreshLapis.get())
                .setDefaultValue(1).setMin(0).setMax(64)
                .setSaveConsumer(CommandConfig.SERVER.enchantRefreshLapis::set)
                .build());
        enchantTable.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.enchant.allow_treasure"),
                CommandConfig.SERVER.enchantAllowTreasure.get())
                .setDefaultValue(false)
                .setSaveConsumer(CommandConfig.SERVER.enchantAllowTreasure::set)
                .build());
        // 标记：神化附魔功能暂未完成，相关配置项无条件隐藏
        addMark(enchantTable, eb, Component.translatable("beyond_integration.config.hidden.apoth"));

        // ========== 5. 铁砧 ==========
        ConfigCategory anvil = builder.getOrCreateCategory(
                Component.translatable("beyond_integration.config.anvil"));
        anvil.addEntry(eb.startEnumSelector(
                Component.translatable("beyond_integration.config.anvil.cost_mode"),
                CommandConfig.AnvilChargeMode.class,
                CommandConfig.SERVER.anvilCostMode.get())
                .setDefaultValue(CommandConfig.AnvilChargeMode.LEVEL)
                .setSaveConsumer(CommandConfig.SERVER.anvilCostMode::set)
                .build());
        anvil.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.anvil.level_cap"),
                CommandConfig.SERVER.anvilLevelCap.get())
                .setDefaultValue(30).setMin(0).setMax(1000)
                .setSaveConsumer(CommandConfig.SERVER.anvilLevelCap::set)
                .build());
        anvil.addEntry(eb.startLongField(
                Component.translatable("beyond_integration.config.anvil.points_cap"),
                CommandConfig.SERVER.anvilPointsCap.get())
                .setDefaultValue(5000L).setMin(0).setMax(Long.MAX_VALUE)
                .setSaveConsumer(CommandConfig.SERVER.anvilPointsCap::set)
                .build());
        anvil.addEntry(eb.startEnumSelector(
                Component.translatable("beyond_integration.config.anvil.break_level_mode"),
                CommandConfig.BreakLevelMode.class,
                CommandConfig.SERVER.anvilBreakLevelMode.get())
                .setDefaultValue(CommandConfig.BreakLevelMode.OFF)
                .setSaveConsumer(CommandConfig.SERVER.anvilBreakLevelMode::set)
                .build());
        anvil.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.anvil.ignore_conflict"),
                CommandConfig.SERVER.anvilIgnoreConflict.get())
                .setDefaultValue(false)
                .setSaveConsumer(CommandConfig.SERVER.anvilIgnoreConflict::set)
                .build());
        anvil.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.anvil.ignore_support"),
                CommandConfig.SERVER.anvilIgnoreSupport.get())
                .setDefaultValue(false)
                .setSaveConsumer(CommandConfig.SERVER.anvilIgnoreSupport::set)
                .build());
        anvil.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.anvil.unrestricted"),
                CommandConfig.SERVER.anvilUnrestricted.get())
                .setDefaultValue(false)
                .setSaveConsumer(CommandConfig.SERVER.anvilUnrestricted::set)
                .build());
        anvil.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.anvil.conflict_penalty"),
                CommandConfig.SERVER.anvilConflictPenalty.get())
                .setDefaultValue(2).setMin(0).setMax(1000000)
                .setSaveConsumer(CommandConfig.SERVER.anvilConflictPenalty::set)
                .build());
        anvil.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.anvil.support_penalty"),
                CommandConfig.SERVER.anvilSupportPenalty.get())
                .setDefaultValue(5).setMin(0).setMax(1000000)
                .setSaveConsumer(CommandConfig.SERVER.anvilSupportPenalty::set)
                .build());
        anvil.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.anvil.conflict_percent"),
                CommandConfig.SERVER.anvilConflictPercent.get())
                .setDefaultValue(0).setMin(0).setMax(1000000)
                .setSaveConsumer(CommandConfig.SERVER.anvilConflictPercent::set)
                .build());
        anvil.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.anvil.support_percent"),
                CommandConfig.SERVER.anvilSupportPercent.get())
                .setDefaultValue(0).setMin(0).setMax(1000000)
                .setSaveConsumer(CommandConfig.SERVER.anvilSupportPercent::set)
                .build());
        anvil.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.anvil.break_level_percent"),
                CommandConfig.SERVER.anvilBreakLevelPercent.get())
                .setDefaultValue(0).setMin(0).setMax(1000000)
                .setSaveConsumer(CommandConfig.SERVER.anvilBreakLevelPercent::set)
                .build());
        anvil.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.anvil.unrestricted_percent"),
                CommandConfig.SERVER.anvilUnrestrictedPercent.get())
                .setDefaultValue(100).setMin(0).setMax(1000000)
                .setSaveConsumer(CommandConfig.SERVER.anvilUnrestrictedPercent::set)
                .build());

        // ========== 6. 载具（依赖 Superb Warfare） ==========
        ConfigCategory vehicle = builder.getOrCreateCategory(
                Component.translatable("beyond_integration.config.vehicle"));
        if (hasSw) {
            vehicle.addEntry(eb.startEnumSelector(
                    Component.translatable("beyond_integration.config.vehicle.charge_mode"),
                    CommandConfig.VehicleChargeMode.class,
                    CommandConfig.SERVER.swVehicleChargeMode.get())
                    .setDefaultValue(CommandConfig.VehicleChargeMode.RATE)
                    .setSaveConsumer(CommandConfig.SERVER.swVehicleChargeMode::set)
                    .build());
            vehicle.addEntry(eb.startIntField(
                    Component.translatable("beyond_integration.config.vehicle.charge_rate"),
                    CommandConfig.SERVER.swVehicleEnergyChargeRate.get())
                    .setDefaultValue(500000).setMin(0).setMax(Integer.MAX_VALUE)
                    .setSaveConsumer(CommandConfig.SERVER.swVehicleEnergyChargeRate::set)
                    .build());
            vehicle.addEntry(eb.startIntField(
                    Component.translatable("beyond_integration.config.vehicle.charge_interval"),
                    CommandConfig.SERVER.swVehicleChargeInterval.get())
                    .setDefaultValue(20).setMin(1).setMax(1200)
                    .setSaveConsumer(CommandConfig.SERVER.swVehicleChargeInterval::set)
                    .build());
            vehicle.addEntry(eb.startDoubleField(
                    Component.translatable("beyond_integration.config.vehicle.charge_percentage"),
                    CommandConfig.SERVER.swVehicleChargePercentage.get())
                    .setDefaultValue(0.0).setMin(0.0).setMax(100.0)
                    .setSaveConsumer(CommandConfig.SERVER.swVehicleChargePercentage::set)
                    .build());
        } else {
            addMark(vehicle, eb, Component.translatable("beyond_integration.config.hidden.sw"));
        }

        // ========== 7. 弹药（依赖 TACZ / Superb Warfare） ==========
        ConfigCategory ammo = builder.getOrCreateCategory(
                Component.translatable("beyond_integration.config.ammo"));
        if (hasTacz) {
            ammo.addEntry(eb.startBooleanToggle(
                    Component.translatable("beyond_integration.config.ammo.tacz_poll_enabled"),
                    CommandConfig.SERVER.TACZ_AMMO_POLL_ENABLED.get())
                    .setDefaultValue(true)
                    .setSaveConsumer(CommandConfig.SERVER.TACZ_AMMO_POLL_ENABLED::set)
                    .build());
            ammo.addEntry(eb.startIntField(
                    Component.translatable("beyond_integration.config.ammo.tacz_poll_interval"),
                    CommandConfig.SERVER.TACZ_AMMO_POLL_INTERVAL_TICKS.get())
                    .setDefaultValue(10).setMin(1).setMax(200)
                    .setSaveConsumer(CommandConfig.SERVER.TACZ_AMMO_POLL_INTERVAL_TICKS::set)
                    .build());
        }
        if (hasSw) {
            ammo.addEntry(eb.startBooleanToggle(
                    Component.translatable("beyond_integration.config.ammo.sw_poll_enabled"),
                    CommandConfig.SERVER.SW_AMMO_POLL_ENABLED.get())
                    .setDefaultValue(true)
                    .setSaveConsumer(CommandConfig.SERVER.SW_AMMO_POLL_ENABLED::set)
                    .build());
            ammo.addEntry(eb.startIntField(
                    Component.translatable("beyond_integration.config.ammo.sw_poll_interval"),
                    CommandConfig.SERVER.SW_AMMO_POLL_INTERVAL_TICKS.get())
                    .setDefaultValue(10).setMin(1).setMax(200)
                    .setSaveConsumer(CommandConfig.SERVER.SW_AMMO_POLL_INTERVAL_TICKS::set)
                    .build());
            ammo.addEntry(eb.startStrList(
                    Component.translatable("beyond_integration.config.ammo.extract_mappings"),
                    new ArrayList<>(CommandConfig.SERVER.AMMO_EXTRACT_MAPPINGS.get()))
                    .setDefaultValue(java.util.Collections.emptyList())
                    .setSaveConsumer(list -> CommandConfig.SERVER.AMMO_EXTRACT_MAPPINGS.set(new ArrayList<>(list)))
                    .build());
        }
        if (!hasTacz) addMark(ammo, eb, Component.translatable("beyond_integration.config.hidden.tacz"));
        if (!hasSw) addMark(ammo, eb, Component.translatable("beyond_integration.config.hidden.sw"));

        // ========== 物品自动充电（装备位 / 饰品） ==========
        ConfigCategory energyCharge = builder.getOrCreateCategory(
                Component.translatable("beyond_integration.config.energy_charge"));
        energyCharge.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.energy_ammo.charge_enabled"),
                CommandConfig.SERVER.ENERGY_AMMO_CHARGE_ENABLED.get())
                .setDefaultValue(true)
                .setSaveConsumer(CommandConfig.SERVER.ENERGY_AMMO_CHARGE_ENABLED::set)
                .build());
        energyCharge.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.energy_ammo.charge_interval"),
                CommandConfig.SERVER.ENERGY_AMMO_CHARGE_INTERVAL.get())
                .setDefaultValue(20).setMin(1).setMax(1200)
                .setSaveConsumer(CommandConfig.SERVER.ENERGY_AMMO_CHARGE_INTERVAL::set)
                .build());
        energyCharge.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.energy_ammo.charge_rate"),
                CommandConfig.SERVER.ENERGY_AMMO_CHARGE_RATE.get())
                .setDefaultValue(10000).setMin(1).setMax(Integer.MAX_VALUE)
                .setSaveConsumer(CommandConfig.SERVER.ENERGY_AMMO_CHARGE_RATE::set)
                .build());
        energyCharge.addEntry(eb.startEnumSelector(
                Component.translatable("beyond_integration.config.energy_ammo.charge_mode"),
                CommandConfig.EnergyChargeMode.class,
                CommandConfig.SERVER.ENERGY_AMMO_CHARGE_MODE.get())
                .setDefaultValue(CommandConfig.EnergyChargeMode.RATE)
                .setSaveConsumer(CommandConfig.SERVER.ENERGY_AMMO_CHARGE_MODE::set)
                .build());
        energyCharge.addEntry(eb.startDoubleField(
                Component.translatable("beyond_integration.config.energy_ammo.charge_percentage"),
                CommandConfig.SERVER.ENERGY_AMMO_CHARGE_PERCENTAGE.get())
                .setDefaultValue(10.0).setMin(0.0).setMax(100.0)
                .setSaveConsumer(CommandConfig.SERVER.ENERGY_AMMO_CHARGE_PERCENTAGE::set)
                .build());
        energyCharge.addEntry(eb.startStrList(
                Component.translatable("beyond_integration.config.energy_ammo.charge_whitelist"),
                new ArrayList<>(CommandConfig.SERVER.ENERGY_AMMO_CHARGE_WHITELIST.get()))
                .setDefaultValue(Arrays.asList())
                .setSaveConsumer(list -> CommandConfig.SERVER.ENERGY_AMMO_CHARGE_WHITELIST.set(new ArrayList<>(list)))
                .build());
        energyCharge.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.energy_ammo.charge_curios"),
                CommandConfig.SERVER.ENERGY_AMMO_CHARGE_CURIOS.get())
                .setDefaultValue(true)
                .setSaveConsumer(CommandConfig.SERVER.ENERGY_AMMO_CHARGE_CURIOS::set)
                .build());
        energyCharge.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.energy_ammo.charge_maid_baubles"),
                CommandConfig.SERVER.ENERGY_AMMO_CHARGE_MAID_BAUBLES.get())
                .setDefaultValue(true)
                .setSaveConsumer(CommandConfig.SERVER.ENERGY_AMMO_CHARGE_MAID_BAUBLES::set)
                .build());

        // ========== 8. 自动图腾 ==========
        ConfigCategory totem = builder.getOrCreateCategory(
                Component.translatable("beyond_integration.config.totem"));
        totem.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.totem.enabled"),
                CommandConfig.SERVER.AUTO_TOTEM_ENABLED.get())
                .setDefaultValue(true)
                .setSaveConsumer(CommandConfig.SERVER.AUTO_TOTEM_ENABLED::set)
                .build());
        totem.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.totem.respect_bypasses"),
                CommandConfig.SERVER.AUTO_TOTEM_RESPECT_BYPASSES.get())
                .setDefaultValue(false)
                .setSaveConsumer(CommandConfig.SERVER.AUTO_TOTEM_RESPECT_BYPASSES::set)
                .build());
        totem.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.totem.restore_max_health"),
                CommandConfig.SERVER.AUTO_TOTEM_RESTORE_MAX_HEALTH.get())
                .setDefaultValue(true)
                .setSaveConsumer(CommandConfig.SERVER.AUTO_TOTEM_RESTORE_MAX_HEALTH::set)
                .build());
        totem.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.totem.heal_to_full"),
                CommandConfig.SERVER.AUTO_TOTEM_HEAL_TO_FULL.get())
                .setDefaultValue(false)
                .setSaveConsumer(CommandConfig.SERVER.AUTO_TOTEM_HEAL_TO_FULL::set)
                .build());
        totem.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.totem.cooldown"),
                CommandConfig.SERVER.AUTO_TOTEM_COOLDOWN_SECONDS.get())
                .setDefaultValue(60).setMin(0).setMax(3600)
                .setSaveConsumer(CommandConfig.SERVER.AUTO_TOTEM_COOLDOWN_SECONDS::set)
                .build());
        totem.addEntry(eb.startStrList(
                Component.translatable("beyond_integration.config.totem.damage_blacklist"),
                new ArrayList<>(CommandConfig.SERVER.AUTO_TOTEM_DAMAGE_BLACKLIST.get()))
                .setDefaultValue(Arrays.asList("minecraft:out_of_world"))
                .setSaveConsumer(list -> CommandConfig.SERVER.AUTO_TOTEM_DAMAGE_BLACKLIST.set(new ArrayList<>(list)))
                .build());

        // ========== 9. 物品黑名单 ==========
        ConfigCategory blacklist = builder.getOrCreateCategory(
                Component.translatable("beyond_integration.config.blacklist"));
        blacklist.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.blacklist.enable"),
                CommandConfig.SERVER.ENABLE_ITEM_BLACKLIST.get())
                .setDefaultValue(false)
                .setSaveConsumer(CommandConfig.SERVER.ENABLE_ITEM_BLACKLIST::set)
                .build());
        blacklist.addEntry(eb.startStrList(
                Component.translatable("beyond_integration.config.blacklist.items"),
                new ArrayList<>(CommandConfig.SERVER.ITEM_BLACKLIST.get()))
                .setDefaultValue(Arrays.asList("minecraft:barrier", "minecraft:command_block"))
                .setSaveConsumer(list -> CommandConfig.SERVER.ITEM_BLACKLIST.set(new ArrayList<>(list)))
                .build());

        // ========== 10. 合成 ==========
        ConfigCategory craft = builder.getOrCreateCategory(
                Component.translatable("beyond_integration.config.craft"));
        craft.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.craft.cooldown"),
                CommandConfig.SERVER.CRAFT_COOLDOWN_MS.get())
                .setDefaultValue(1000).setMin(0).setMax(60000)
                .setSaveConsumer(CommandConfig.SERVER.CRAFT_COOLDOWN_MS::set)
                .build());
        craft.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.craft.max_depth"),
                CommandConfig.SERVER.CRAFT_MAX_DEPTH.get())
                .setDefaultValue(6).setMin(1).setMax(20)
                .setSaveConsumer(CommandConfig.SERVER.CRAFT_MAX_DEPTH::set)
                .build());
        craft.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.craft.block_reader"),
                CommandConfig.SERVER.BLOCK_BD_CONTAINER_READER.get())
                .setDefaultValue(true)
                .setSaveConsumer(CommandConfig.SERVER.BLOCK_BD_CONTAINER_READER::set)
                .build());

        // ========== 11. BD 修改（经验棒：等级上限 / 分批 / 直设） ==========
        ConfigCategory xpRod = builder.getOrCreateCategory(
                Component.translatable("beyond_integration.config.bd_tweaks"));
        xpRod.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.bd_tweaks.xp_rod_enabled"),
                CommandConfig.SERVER.xpRodTweaksEnabled.get())
                .setDefaultValue(true)
                .setSaveConsumer(CommandConfig.SERVER.xpRodTweaksEnabled::set)
                .build());
        xpRod.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.bd_tweaks.furnace_terminal_smelt_all"),
                CommandConfig.SERVER.furnaceTerminalSmeltAllEnabled.get())
                .setDefaultValue(false)
                .setSaveConsumer(CommandConfig.SERVER.furnaceTerminalSmeltAllEnabled::set)
                .build());
        xpRod.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.xp_rod.max_target_level"),
                CommandConfig.SERVER.xpRodMaxTargetLevel.get())
                .setDefaultValue(238609312).setMin(0).setMax(Integer.MAX_VALUE)
                .setSaveConsumer(CommandConfig.SERVER.xpRodMaxTargetLevel::set)
                .build());
        xpRod.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.xp_rod.grant_batch_size"),
                CommandConfig.SERVER.xpRodGrantBatchSize.get())
                .setDefaultValue(Integer.MAX_VALUE - 1).setMin(0).setMax(Integer.MAX_VALUE)
                .setSaveConsumer(CommandConfig.SERVER.xpRodGrantBatchSize::set)
                .build());
        xpRod.addEntry(eb.startEnumSelector(
                Component.translatable("beyond_integration.config.xp_rod.grant_mode"),
                CommandConfig.XpGrantMode.class,
                CommandConfig.SERVER.xpRodGrantMode.get())
                .setDefaultValue(CommandConfig.XpGrantMode.BATCH)
                .setSaveConsumer(CommandConfig.SERVER.xpRodGrantMode::set)
                .build());

        // ========== 12. 附魔分离 ==========
        ConfigCategory enchant = builder.getOrCreateCategory(
                Component.translatable("beyond_integration.config.enchant_separation"));
        enchant.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.enchant.separation"),
                CommandConfig.SERVER.ENABLE_ENCHANTMENT_SEPARATION.get())
                .setDefaultValue(false)
                .setSaveConsumer(CommandConfig.SERVER.ENABLE_ENCHANTMENT_SEPARATION::set)
                .build());
        enchant.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.enchant.base_cost"),
                CommandConfig.SERVER.ENCHANTMENT_SEPARATION_BASE_COST.get())
                .setDefaultValue(10).setMin(0).setMax(1000)
                .setSaveConsumer(CommandConfig.SERVER.ENCHANTMENT_SEPARATION_BASE_COST::set)
                .build());
        enchant.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.enchant.level_mult"),
                CommandConfig.SERVER.ENCHANTMENT_SEPARATION_LEVEL_MULTIPLIER.get())
                .setDefaultValue(5).setMin(0).setMax(100)
                .setSaveConsumer(CommandConfig.SERVER.ENCHANTMENT_SEPARATION_LEVEL_MULTIPLIER::set)
                .build());
        enchant.addEntry(eb.startDoubleField(
                Component.translatable("beyond_integration.config.enchant.default_mult"),
                CommandConfig.SERVER.DEFAULT_ENCHANTMENT_MULTIPLIER.get())
                .setDefaultValue(1.0).setMin(0.1).setMax(10.0)
                .setSaveConsumer(CommandConfig.SERVER.DEFAULT_ENCHANTMENT_MULTIPLIER::set)
                .build());
        enchant.addEntry(eb.startStrField(
                Component.translatable("beyond_integration.config.enchant.formula"),
                CommandConfig.SERVER.COST_FORMULA.get())
                .setDefaultValue("base + (level - 1) * multiplier")
                .setSaveConsumer(CommandConfig.SERVER.COST_FORMULA::set)
                .build());
        enchant.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.enchant.use_formula"),
                CommandConfig.SERVER.USE_FORMULA.get())
                .setDefaultValue(false)
                .setSaveConsumer(CommandConfig.SERVER.USE_FORMULA::set)
                .build());
        enchant.addEntry(eb.startStrList(
                Component.translatable("beyond_integration.config.enchant.high_cost"),
                new ArrayList<>(CommandConfig.SERVER.HIGH_COST_ENCHANTMENTS.get()))
                .setDefaultValue(Arrays.asList(
                        "minecraft:mending:3.0", "minecraft:frost_walker:3.0",
                        "minecraft:sharpness:1.2", "minecraft:protection:1.2"))
                .setSaveConsumer(list -> CommandConfig.SERVER.HIGH_COST_ENCHANTMENTS.set(new ArrayList<>(list)))
                .build());

        return builder.build();
    }
}
