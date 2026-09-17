package com.solr98.beyondintegration;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import com.solr98.beyondintegration.core.config.ConfigCommentLang;

import java.util.Arrays;
import java.util.List;

/**
 * 模组通用配置（ForgeConfigSpec 服务端配置）。
 * 集中定义全部可配置项：语言、网络列表分页、附魔分离、载具充能、
 * 物品黑名单、合成冷却、弹药提取映射、TACZ/SW 弹药轮询、
 * 铁砧工作台计费与自动图腾等，并提供静态访问入口。
 */
public class CommandConfig
{
    /** 服务端配置规格（同步到客户端） */
    public static final ForgeConfigSpec SERVER_SPEC;
    /** 服务端配置对象实例 */
    public static final ServerConfig SERVER;

    /** 构建配置规格与配置实例 */
    static
    {
        final Pair<ServerConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(ServerConfig::new);
        SERVER_SPEC = specPair.getRight();
        SERVER = specPair.getLeft();
    }

    /** 命令输出语言选项 */
    public enum Language
    {
        EN_US("en_us"), ZH_CN("zh_cn");

        private final String code;
        Language(String code) { this.code = code; }
        public String getCode() { return code; }
    }

    /** 载具充能模式：按固定速率（FE/tick）或按缺失能量的百分比 */
    public enum VehicleChargeMode
    {
        RATE, PERCENTAGE
    }

    /** 铁砧工作台计费模式：按等级或按经验点数 */
    public enum AnvilChargeMode
    {
        LEVEL, POINTS
    }

    /** 物品装备位网络充电模式：固定值 / 缺失能量百分比 / 百分比 + 固定值 */
    public enum EnergyChargeMode
    {
        RATE, PERCENTAGE, PERCENTAGE_PLUS_RATE
    }

    /**
     * 附魔台（网络化）附魔功率来源：
     * FIXED=固定功率；NETWORK_XP=网络经验流体换算；PLAYER_LEVEL=玩家经验等级换算。
     */
    public enum EnchantPowerMode
    {
        FIXED, NETWORK_XP, PLAYER_LEVEL
    }

    /**
     * 铁砧附魔等级上限模式：
     * OFF      = 原版：钳制附魔最高等级（5 级书 + 5 级书 → 6，但封顶 5）；
     * PLUS     = 叠级突破：保留原版合成规则（同附魔 5+5=6、异附魔取 max），仅解除封顶；
     * ADDITIVE = 相加突破：仅同类附魔等级直接相加（5+5=10），不同附魔仍取 max。
     */
    public enum BreakLevelMode
    {
        OFF, PLUS, ADDITIVE
    }

    /**
     * 经验棒经验给予方式：
     * BATCH  = 分批 giveExperiencePoints（每 tick 一批，合法升级上限 238609312；
     *          再往上升级所需经验超过 int 上限，经验条会异常）；
     * DIRECT = 直设式（等级计算 + 设定等级/进度 + 网络经验补差），不依赖原版升级公式。
     */
    public enum XpGrantMode
    {
        BATCH, DIRECT
    }

    /** 配置项定义类：在构造器中分区注册全部配置条目 */
    public static class ServerConfig
    {
        public final ForgeConfigSpec.EnumValue<Language> language;
        public final ForgeConfigSpec.IntValue maxNetworksPerPage;

        public final ForgeConfigSpec.BooleanValue ENABLE_ENCHANTMENT_SEPARATION;


        // Enchantment separation debug
        public final ForgeConfigSpec.BooleanValue ENCHANTMENT_SEPARATION_DEBUG;
        public final ForgeConfigSpec.IntValue ENCHANTMENT_SEPARATION_BASE_COST;
        public final ForgeConfigSpec.IntValue ENCHANTMENT_SEPARATION_LEVEL_MULTIPLIER;
        public final ForgeConfigSpec.DoubleValue DEFAULT_ENCHANTMENT_MULTIPLIER;
        public final ForgeConfigSpec.ConfigValue<String> COST_FORMULA;
        public final ForgeConfigSpec.BooleanValue USE_FORMULA;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> HIGH_COST_ENCHANTMENTS;

        // Vehicle energy charge
        public final ForgeConfigSpec.EnumValue<VehicleChargeMode> swVehicleChargeMode;
        public final ForgeConfigSpec.IntValue swVehicleEnergyChargeRate;
        public final ForgeConfigSpec.IntValue swVehicleChargeInterval;
        public final ForgeConfigSpec.DoubleValue swVehicleChargePercentage;


        public final ForgeConfigSpec.BooleanValue ENABLE_ITEM_BLACKLIST;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_BLACKLIST;
        public final ForgeConfigSpec.IntValue CRAFT_COOLDOWN_MS;
        public final ForgeConfigSpec.IntValue CRAFT_MAX_DEPTH;
        public final ForgeConfigSpec.BooleanValue BLOCK_BD_CONTAINER_READER;

        /** 服务端启用的工作台 ID 列表（含 storage/craft/anvil/cut/grind/smith/enchant） */
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> WORKSTATIONS_ENABLED;

        // 工作台献祭激活（可选平衡项）：开启后 anvil/cut/grind/smith/enchant 需先献祭对应原版工作台激活（网络级）
        public final ForgeConfigSpec.BooleanValue WORKSTATION_ACTIVATION_ENABLED;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> WORKSTATION_ACTIVATION_COSTS;

        // FTB Quests 集成（可选；检测到 rs_integration 时让路禁用）
        public final ForgeConfigSpec.BooleanValue FTB_INTEGRATION_ENABLED;

        public final ForgeConfigSpec.ConfigValue<List<? extends String>> AMMO_EXTRACT_MAPPINGS;

        // TACZ ammo polling
        public final ForgeConfigSpec.BooleanValue TACZ_AMMO_POLL_ENABLED;
        public final ForgeConfigSpec.IntValue TACZ_AMMO_POLL_INTERVAL_TICKS;

        // SW ammo polling
        public final ForgeConfigSpec.BooleanValue SW_AMMO_POLL_ENABLED;
        public final ForgeConfigSpec.IntValue SW_AMMO_POLL_INTERVAL_TICKS;

        // SW energy ammo network charging
        public final ForgeConfigSpec.BooleanValue ENERGY_AMMO_CHARGE_ENABLED;
        public final ForgeConfigSpec.IntValue ENERGY_AMMO_CHARGE_INTERVAL;
        public final ForgeConfigSpec.IntValue ENERGY_AMMO_CHARGE_RATE;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> ENERGY_AMMO_CHARGE_WHITELIST;
        public final ForgeConfigSpec.BooleanValue ENERGY_AMMO_CHARGE_CURIOS;
        public final ForgeConfigSpec.BooleanValue ENERGY_AMMO_CHARGE_MAID_BAUBLES;
        public final ForgeConfigSpec.EnumValue<EnergyChargeMode> ENERGY_AMMO_CHARGE_MODE;
        public final ForgeConfigSpec.DoubleValue ENERGY_AMMO_CHARGE_PERCENTAGE;

        // Anvil workstation
        public final ForgeConfigSpec.EnumValue<AnvilChargeMode> anvilCostMode;
        public final ForgeConfigSpec.IntValue anvilLevelCap;
        public final ForgeConfigSpec.LongValue anvilPointsCap;
        public final ForgeConfigSpec.EnumValue<BreakLevelMode> anvilBreakLevelMode;
        public final ForgeConfigSpec.BooleanValue anvilIgnoreConflict;
        public final ForgeConfigSpec.BooleanValue anvilIgnoreSupport;
        public final ForgeConfigSpec.BooleanValue anvilUnrestricted;
        public final ForgeConfigSpec.IntValue anvilConflictPenalty;
        public final ForgeConfigSpec.IntValue anvilSupportPenalty;
        public final ForgeConfigSpec.IntValue anvilConflictPercent;
        public final ForgeConfigSpec.IntValue anvilSupportPercent;
        public final ForgeConfigSpec.IntValue anvilBreakLevelPercent;
        public final ForgeConfigSpec.IntValue anvilUnrestrictedPercent;

        // Enchantment table workstation
        public final ForgeConfigSpec.EnumValue<EnchantPowerMode> enchantPowerMode;
        public final ForgeConfigSpec.IntValue enchantFixedPower;
        public final ForgeConfigSpec.IntValue enchantXpPerPower;
        public final ForgeConfigSpec.IntValue enchantLevelPerPower;
        // 增强 / 解限
        public final ForgeConfigSpec.BooleanValue enchantIgnoreEnchanted;
        public final ForgeConfigSpec.BooleanValue enchantIgnoreConflict;
        public final ForgeConfigSpec.BooleanValue enchantNoLapis;
        public final ForgeConfigSpec.BooleanValue enchantLevelGateIgnore;
        public final ForgeConfigSpec.BooleanValue enchantUncapPower;
        public final ForgeConfigSpec.IntValue enchantCostPercent;
        public final ForgeConfigSpec.BooleanValue enchantAllowTreasure;
        // 预览 / 刷新（服务端开关）
        public final ForgeConfigSpec.BooleanValue enchantPreviewEnabled;
        public final ForgeConfigSpec.BooleanValue enchantRefreshEnabled;
        public final ForgeConfigSpec.IntValue enchantRefreshLapis;

        // BD modifications (tweaks applied to Beyond Dimensions)
        public final ForgeConfigSpec.BooleanValue xpRodTweaksEnabled;
        /** 熔炉烧网络终端：批量烧炼网络内全部可烧炼物品（默认关闭） */
        public final ForgeConfigSpec.BooleanValue furnaceTerminalSmeltAllEnabled;
        public final ForgeConfigSpec.IntValue xpRodMaxTargetLevel;
        public final ForgeConfigSpec.IntValue xpRodGrantBatchSize;
        public final ForgeConfigSpec.EnumValue<XpGrantMode> xpRodGrantMode;

        // Auto totem (network only)
        public final ForgeConfigSpec.BooleanValue AUTO_TOTEM_ENABLED;
        public final ForgeConfigSpec.IntValue AUTO_TOTEM_COOLDOWN_SECONDS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> AUTO_TOTEM_DAMAGE_BLACKLIST;
        public final ForgeConfigSpec.BooleanValue AUTO_TOTEM_RESPECT_BYPASSES;
        public final ForgeConfigSpec.BooleanValue AUTO_TOTEM_RESTORE_MAX_HEALTH;
        public final ForgeConfigSpec.BooleanValue AUTO_TOTEM_HEAL_TO_FULL;

        // SetHealth revive (network only, event + mixin implementations)
        public final ForgeConfigSpec.BooleanValue REVIVE_EVENT_ENABLED;
        public final ForgeConfigSpec.BooleanValue REVIVE_MIXIN_ENABLED;
        public final ForgeConfigSpec.IntValue REVIVE_COOLDOWN_SECONDS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> REVIVE_DAMAGE_BLACKLIST;
        public final ForgeConfigSpec.BooleanValue REVIVE_RESPECT_BYPASSES;
        public final ForgeConfigSpec.BooleanValue REVIVE_EXTRA_TOTEM_ON_SET_HEALTH_DEATH;
        public final ForgeConfigSpec.IntValue REVIVE_EXTRA_TOTEM_COUNT;
        public final ForgeConfigSpec.BooleanValue REVIVE_RESTORE_MAX_HEALTH;
        public final ForgeConfigSpec.BooleanValue REVIVE_HEAL_TO_FULL;
        public final ForgeConfigSpec.BooleanValue REVIVE_RESET_DEATH_TIME;

        public ServerConfig(ForgeConfigSpec.Builder builder)
        {
            builder.comment(ConfigCommentLang.comment("language")).push("language");
            language = builder.defineEnum("command_language", Language.EN_US);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("network_list")).push("network_list");
            maxNetworksPerPage = builder.defineInRange("max_networks_per_page", 10, 1, 100);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("enchantment_separation")).push("enchantment_separation");

            ENABLE_ENCHANTMENT_SEPARATION = builder
                    .comment(ConfigCommentLang.comment("enchantment_separation.enable"))
                    .define("enable", false);

            ENCHANTMENT_SEPARATION_BASE_COST = builder
                    .comment(ConfigCommentLang.comment("enchantment_separation.base_cost"))
                    .defineInRange("base_cost", 10, 0, 1000);

            ENCHANTMENT_SEPARATION_LEVEL_MULTIPLIER = builder
                    .comment(ConfigCommentLang.comment("enchantment_separation.level_multiplier"))
                    .defineInRange("level_multiplier", 5, 0, 100);

            DEFAULT_ENCHANTMENT_MULTIPLIER = builder
                    .comment(ConfigCommentLang.comment("enchantment_separation.default_multiplier"))
                    .defineInRange("default_multiplier", 1.0, 0.1, 10.0);

            COST_FORMULA = builder
                    .comment(ConfigCommentLang.comment("enchantment_separation.cost_formula"))
                    .define("cost_formula", "base + (level - 1) * multiplier");

            USE_FORMULA = builder
                    .comment(ConfigCommentLang.comment("enchantment_separation.use_formula"))
                    .define("use_formula", false);

            HIGH_COST_ENCHANTMENTS = builder
                    .comment(ConfigCommentLang.comment("enchantment_separation.high_cost_list"))
                    .defineList("high_cost_list",
                            Arrays.asList("minecraft:mending:3.0", "minecraft:sharpness:1.2"),
                            obj -> obj instanceof String);

            ENCHANTMENT_SEPARATION_DEBUG = builder
                    .comment(ConfigCommentLang.comment("enchantment_separation.debug"))
                    .define("debug", false);

            builder.pop();

            builder.comment(ConfigCommentLang.comment("vehicle")).push("vehicle");
            swVehicleChargeMode = builder
                    .comment(ConfigCommentLang.comment("vehicle.charge_mode"))
                    .defineEnum("charge_mode", VehicleChargeMode.RATE);
            swVehicleEnergyChargeRate = builder
                    .comment(ConfigCommentLang.comment("vehicle.energyChargeRate"))
                    .defineInRange("energyChargeRate", 500000, 0, Integer.MAX_VALUE);
            swVehicleChargeInterval = builder
                    .comment(ConfigCommentLang.comment("vehicle.chargeInterval"))
                    .defineInRange("chargeInterval", 20, 1, 1200);
            swVehicleChargePercentage = builder
                    .comment(ConfigCommentLang.comment("vehicle.chargePercentage"))
                    .defineInRange("chargePercentage", 0.0, 0.0, 100.0);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("blacklist")).push("blacklist");
            ENABLE_ITEM_BLACKLIST = builder.comment(ConfigCommentLang.comment("blacklist.enable")).define("enable", false);
            ITEM_BLACKLIST = builder.comment(ConfigCommentLang.comment("blacklist.items")).defineList("items",
                    Arrays.asList("minecraft:barrier", "minecraft:command_block"), obj -> obj instanceof String);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("craft")).push("craft");
            CRAFT_COOLDOWN_MS = builder.comment(ConfigCommentLang.comment("craft.cooldown_ms")).defineInRange("cooldown_ms", 1000, 0, 60000);
            CRAFT_MAX_DEPTH = builder.comment(ConfigCommentLang.comment("craft.max_depth")).defineInRange("max_depth", 6, 1, 20);
            BLOCK_BD_CONTAINER_READER = builder.comment(ConfigCommentLang.comment("craft.block_bd_reader")).define("block_bd_reader", true);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("workstations")).push("workstations");
            // 注意：storage 实为 BD 终端界面（网络存储/合成终端），不属于工作台，不受本列表控制
            WORKSTATIONS_ENABLED = builder
                    .comment(ConfigCommentLang.comment("workstations.enabled"))
                    .defineList("enabled",
                            Arrays.asList("craft", "anvil", "cut", "grind", "smith", "enchant"),
                            obj -> obj instanceof String);

            // 献祭激活（可选平衡）：开启后除合成台外的工作台需先献祭对应原版工作台激活（网络级）
            builder.comment(ConfigCommentLang.comment("workstations.activation")).push("activation");
            WORKSTATION_ACTIVATION_ENABLED = builder
                    .comment(ConfigCommentLang.comment("workstations.activation.enable"))
                    .define("enable", false);
            WORKSTATION_ACTIVATION_COSTS = builder
                    .comment(ConfigCommentLang.comment("workstations.activation.costs"))
                    .defineList("costs",
                            Arrays.asList(
                                    "anvil:minecraft:anvil:1",
                                    "cut:minecraft:stonecutter:1",
                                    "grind:minecraft:grindstone:1",
                                    "smith:minecraft:smithing_table:1",
                                    "enchant:minecraft:enchanting_table:1"),
                            obj -> obj instanceof String);
            builder.pop();
            builder.pop();

            builder.comment(ConfigCommentLang.comment("ftb_integration")).push("ftb_integration");
            FTB_INTEGRATION_ENABLED = builder
                    .comment(ConfigCommentLang.comment("ftb_integration.enable"))
                    .define("enable", true);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("ammo_extract")).push("ammo_extract");
            AMMO_EXTRACT_MAPPINGS = builder
                    .comment(ConfigCommentLang.comment("ammo_extract.mappings"))
                    .defineList("mappings",
                            Arrays.asList(
                                    "superbwarfare:handgun_ammo:HandgunAmmo",
                                    "superbwarfare:handgun_ammo_box:HandgunAmmo",
                                    "superbwarfare:rifle_ammo:RifleAmmo",
                                    "superbwarfare:rifle_ammo_box:RifleAmmo",
                                    "superbwarfare:shotgun_ammo:ShotgunAmmo",
                                    "superbwarfare:shotgun_ammo_box:ShotgunAmmo",
                                    "superbwarfare:sniper_ammo:SniperAmmo",
                                    "superbwarfare:sniper_ammo_box:SniperAmmo",
                                    "superbwarfare:heavy_ammo:HeavyAmmo"
                            ),
                            obj -> obj instanceof String);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("tacz_ammo")).push("tacz_ammo");
            TACZ_AMMO_POLL_ENABLED = builder
                    .comment(ConfigCommentLang.comment("tacz_ammo.ammo_poll_enabled"))
                    .define("ammo_poll_enabled", true);
            TACZ_AMMO_POLL_INTERVAL_TICKS = builder
                    .comment(ConfigCommentLang.comment("tacz_ammo.ammo_poll_interval_ticks"))
                    .defineInRange("ammo_poll_interval_ticks", 10, 1, 1200);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("sw_ammo")).push("sw_ammo");
            SW_AMMO_POLL_ENABLED = builder
                    .comment(ConfigCommentLang.comment("sw_ammo.ammo_poll_enabled"))
                    .define("ammo_poll_enabled", true);
            SW_AMMO_POLL_INTERVAL_TICKS = builder
                    .comment(ConfigCommentLang.comment("sw_ammo.ammo_poll_interval_ticks"))
                    .defineInRange("ammo_poll_interval_ticks", 10, 1, 1200);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("energy_ammo")).push("energy_ammo");
            ENERGY_AMMO_CHARGE_ENABLED = builder
                    .comment(ConfigCommentLang.comment("energy_ammo.charge_enabled"))
                    .define("charge_enabled", true);
            ENERGY_AMMO_CHARGE_INTERVAL = builder
                    .comment(ConfigCommentLang.comment("energy_ammo.charge_interval"))
                    .defineInRange("charge_interval", 20, 1, 1200);
            ENERGY_AMMO_CHARGE_RATE = builder
                    .comment(ConfigCommentLang.comment("energy_ammo.charge_rate"))
                    .defineInRange("charge_rate", 10000, 1, Integer.MAX_VALUE);
            ENERGY_AMMO_CHARGE_MODE = builder
                    .comment(ConfigCommentLang.comment("energy_ammo.charge_mode"))
                    .defineEnum("charge_mode", EnergyChargeMode.RATE);
            ENERGY_AMMO_CHARGE_PERCENTAGE = builder
                    .comment(ConfigCommentLang.comment("energy_ammo.charge_percentage"))
                    .defineInRange("charge_percentage", 10.0, 0.0, 100.0);
            ENERGY_AMMO_CHARGE_WHITELIST = builder
                    .comment(ConfigCommentLang.comment("energy_ammo.charge_whitelist"))
                    .defineList("charge_whitelist", Arrays.asList(
                            "ae2:wireless_terminal",
                            "ae2:wireless_crafting_terminal",
                            "ae2wtlib:wireless_crafting_terminal",
                            "ae2wtlib:wireless_pattern_access_terminal",
                            "ae2wtlib:wireless_pattern_encoding_terminal",
                            "ae2wtlib:wireless_universal_terminal",
                            "wcwt:wireless_comprehensive_work_terminal",
                            "refinedstorage:wireless_grid",
                            "refinedstorage:wireless_fluid_grid",
                            "refinedstorage:wireless_crafting_monitor"
                    ), obj -> obj instanceof String);
            ENERGY_AMMO_CHARGE_CURIOS = builder
                    .comment(ConfigCommentLang.comment("energy_ammo.charge_curios"))
                    .define("charge_curios", true);
            ENERGY_AMMO_CHARGE_MAID_BAUBLES = builder
                    .comment(ConfigCommentLang.comment("energy_ammo.charge_maid_baubles"))
                    .define("charge_maid_baubles", true);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("anvil")).push("anvil");
            anvilCostMode = builder
                    .comment(ConfigCommentLang.comment("anvil.costMode"))
                    .defineEnum("costMode", AnvilChargeMode.POINTS);
            anvilLevelCap = builder
                    .comment(ConfigCommentLang.comment("anvil.levelCap"))
                    .defineInRange("levelCap", 40, 1, Integer.MAX_VALUE);
            anvilPointsCap = builder
                    .comment(ConfigCommentLang.comment("anvil.pointsCap"))
                    .defineInRange("pointsCap", Long.MAX_VALUE, 1L, Long.MAX_VALUE);
            anvilBreakLevelMode = builder
                    .comment(ConfigCommentLang.comment("anvil.breakMaxLevelMode"))
                    .defineEnum("breakMaxLevelMode", BreakLevelMode.OFF);
            anvilIgnoreConflict = builder
                    .comment(ConfigCommentLang.comment("anvil.ignoreConflict"))
                    .define("ignoreConflict", false);
            anvilIgnoreSupport = builder
                    .comment(ConfigCommentLang.comment("anvil.ignoreSupport"))
                    .define("ignoreSupport", false);
            anvilUnrestricted = builder
                    .comment(ConfigCommentLang.comment("anvil.unrestricted"))
                    .define("unrestricted", false);
            anvilConflictPenalty = builder
                    .comment(ConfigCommentLang.comment("anvil.conflictPenalty"))
                    .defineInRange("conflictPenalty", 2, 0, 1000000);
            anvilSupportPenalty = builder
                    .comment(ConfigCommentLang.comment("anvil.supportPenalty"))
                    .defineInRange("supportPenalty", 5, 0, 1000000);
            anvilConflictPercent = builder
                    .comment(ConfigCommentLang.comment("anvil.conflictPercent"))
                    .defineInRange("conflictPercent", 0, 0, 1000000);
            anvilSupportPercent = builder
                    .comment(ConfigCommentLang.comment("anvil.supportPercent"))
                    .defineInRange("supportPercent", 0, 0, 1000000);
            anvilBreakLevelPercent = builder
                    .comment(ConfigCommentLang.comment("anvil.breakLevelPercent"))
                    .defineInRange("breakLevelPercent", 0, 0, 1000000);
            anvilUnrestrictedPercent = builder
                    .comment(ConfigCommentLang.comment("anvil.unrestrictedPercent"))
                    .defineInRange("unrestrictedPercent", 100, 0, 1000000);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("enchant_table")).push("enchant_table");
            enchantPowerMode = builder.comment(ConfigCommentLang.comment("enchant_table.powerMode"))
                    .defineEnum("powerMode", EnchantPowerMode.FIXED);
            enchantFixedPower = builder.comment(ConfigCommentLang.comment("enchant_table.fixedPower"))
                    .defineInRange("fixedPower", 15, 0, 100);
            enchantXpPerPower = builder.comment(ConfigCommentLang.comment("enchant_table.xpPerPower"))
                    .defineInRange("xpPerPower", 100, 1, Integer.MAX_VALUE);
            enchantLevelPerPower = builder.comment(ConfigCommentLang.comment("enchant_table.levelPerPower"))
                    .defineInRange("levelPerPower", 1, 1, Integer.MAX_VALUE);
            enchantIgnoreEnchanted = builder.comment(ConfigCommentLang.comment("enchant_table.ignoreEnchanted"))
                    .define("ignoreEnchanted", false);
            enchantIgnoreConflict = builder.comment(ConfigCommentLang.comment("enchant_table.ignoreConflict"))
                    .define("ignoreConflict", false);
            enchantNoLapis = builder.comment(ConfigCommentLang.comment("enchant_table.noLapis"))
                    .define("noLapis", false);
            enchantLevelGateIgnore = builder.comment(ConfigCommentLang.comment("enchant_table.levelGateIgnore"))
                    .define("levelGateIgnore", false);
            enchantUncapPower = builder.comment(ConfigCommentLang.comment("enchant_table.uncapPower"))
                    .define("uncapPower", false);
            enchantCostPercent = builder.comment(ConfigCommentLang.comment("enchant_table.costPercent"))
                    .defineInRange("costPercent", 100, 10, 500);
            enchantAllowTreasure = builder.comment(ConfigCommentLang.comment("enchant_table.allowTreasure"))
                    .define("allowTreasure", false);
            enchantPreviewEnabled = builder.comment(ConfigCommentLang.comment("enchant_table.previewEnabled"))
                    .define("previewEnabled", true);
            enchantRefreshEnabled = builder.comment(ConfigCommentLang.comment("enchant_table.refreshEnabled"))
                    .define("refreshEnabled", true);
            enchantRefreshLapis = builder.comment(ConfigCommentLang.comment("enchant_table.refreshLapis"))
                    .defineInRange("refreshLapis", 1, 0, 64);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("bd_tweaks")).push("bd_tweaks");
            xpRodTweaksEnabled = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.xp_rod_enabled"))
                    .define("xp_rod_enabled", true);
            furnaceTerminalSmeltAllEnabled = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.furnace_terminal_smelt_all"))
                    .define("furnace_terminal_smelt_all", false);
            xpRodMaxTargetLevel = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.max_target_level"))
                    .defineInRange("max_target_level", 238609312, 0, Integer.MAX_VALUE);
            xpRodGrantBatchSize = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.grant_batch_size"))
                    .defineInRange("grant_batch_size", Integer.MAX_VALUE - 1, 0, Integer.MAX_VALUE);
            xpRodGrantMode = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.grant_mode"))
                    .defineEnum("grant_mode", XpGrantMode.BATCH);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("auto_totem")).push("auto_totem");
            AUTO_TOTEM_ENABLED = builder
                    .comment(ConfigCommentLang.comment("auto_totem.enabled"))
                    .define("enabled", true);
            AUTO_TOTEM_COOLDOWN_SECONDS = builder
                    .comment(ConfigCommentLang.comment("auto_totem.cooldown_seconds"))
                    .defineInRange("cooldown_seconds", 10, 0, 3600);
            AUTO_TOTEM_DAMAGE_BLACKLIST = builder
                    .comment(ConfigCommentLang.comment("auto_totem.damage_blacklist"))
                    .defineList("damage_blacklist",
                            Arrays.asList("outOfWorld", "fellOutOfWorld", "genericKill", "command"),
                            obj -> obj instanceof String);
            AUTO_TOTEM_RESPECT_BYPASSES = builder
                    .comment(ConfigCommentLang.comment("auto_totem.respect_bypasses_invulnerability"))
                    .define("respect_bypasses_invulnerability", false);
            AUTO_TOTEM_RESTORE_MAX_HEALTH = builder
                    .comment(ConfigCommentLang.comment("auto_totem.restore_max_health"))
                    .define("restore_max_health", true);
            AUTO_TOTEM_HEAL_TO_FULL = builder
                    .comment(ConfigCommentLang.comment("auto_totem.heal_to_full"))
                    .define("heal_to_full", false);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("revive")).push("revive");
            REVIVE_EVENT_ENABLED = builder
                    .comment(ConfigCommentLang.comment("revive.event_enabled"))
                    .define("event_enabled", true);
            REVIVE_MIXIN_ENABLED = builder
                    .comment(ConfigCommentLang.comment("revive.mixin_enabled"))
                    .define("mixin_enabled", true);
            REVIVE_COOLDOWN_SECONDS = builder
                    .comment(ConfigCommentLang.comment("revive.cooldown_seconds"))
                    .defineInRange("cooldown_seconds", 10, 0, 3600);
            REVIVE_DAMAGE_BLACKLIST = builder
                    .comment(ConfigCommentLang.comment("revive.damage_blacklist"))
                    .defineList("damage_blacklist",
                            Arrays.asList("outOfWorld", "fellOutOfWorld", "genericKill", "command"),
                            obj -> obj instanceof String);
            REVIVE_RESPECT_BYPASSES = builder
                    .comment(ConfigCommentLang.comment("revive.respect_bypasses_invulnerability"))
                    .define("respect_bypasses_invulnerability", false);
            REVIVE_EXTRA_TOTEM_ON_SET_HEALTH_DEATH = builder
                    .comment(ConfigCommentLang.comment("revive.extra_totem_on_set_health_death"))
                    .define("extra_totem_on_set_health_death", true);
            REVIVE_EXTRA_TOTEM_COUNT = builder
                    .comment(ConfigCommentLang.comment("revive.extra_totem_count"))
                    .defineInRange("extra_totem_count", 1, 0, 64);
            REVIVE_RESTORE_MAX_HEALTH = builder
                    .comment(ConfigCommentLang.comment("revive.restore_max_health"))
                    .define("restore_max_health", true);
            REVIVE_HEAL_TO_FULL = builder
                    .comment(ConfigCommentLang.comment("revive.heal_to_full"))
                    .define("heal_to_full", false);
            REVIVE_RESET_DEATH_TIME = builder
                    .comment(ConfigCommentLang.comment("revive.reset_death_time"))
                    .define("reset_death_time", true);
            builder.pop();
        }

    }

    // ─── 配置读取静态入口（供各处代码调用） ───
    /** 获取命令输出语言；配置尚未生成/加载时回退到默认 EN_US */
    public static Language getCommandLanguage() {
        try {
            return SERVER.language.get();
        } catch (IllegalStateException e) {
            return Language.EN_US;
        }
    }
    public static int maxNetworksPerPage() { return SERVER.maxNetworksPerPage.get(); }

    public static boolean enableEnchantmentSeparation() { return SERVER.ENABLE_ENCHANTMENT_SEPARATION.get(); }
    public static boolean enchantmentSeparationDebug() { return SERVER.ENCHANTMENT_SEPARATION_DEBUG.get(); }
    public static int enchantmentSeparationBaseCost() { return SERVER.ENCHANTMENT_SEPARATION_BASE_COST.get(); }
    public static int enchantmentSeparationLevelMultiplier() { return SERVER.ENCHANTMENT_SEPARATION_LEVEL_MULTIPLIER.get(); }
    public static double defaultEnchantmentMultiplier() { return SERVER.DEFAULT_ENCHANTMENT_MULTIPLIER.get(); }
    public static String costFormula() { return SERVER.COST_FORMULA.get(); }
    public static boolean useFormula() { return SERVER.USE_FORMULA.get(); }
    public static List<? extends String> highCostEnchantments() { return SERVER.HIGH_COST_ENCHANTMENTS.get(); }

    public static boolean enableItemBlacklist() { return SERVER.ENABLE_ITEM_BLACKLIST.get(); }
    public static List<? extends String> itemBlacklist() { return SERVER.ITEM_BLACKLIST.get(); }
    public static VehicleChargeMode vehicleChargeMode() { return SERVER.swVehicleChargeMode.get(); }
    public static int vehicleChargeRate() { return SERVER.swVehicleEnergyChargeRate.get(); }
    public static int vehicleChargeInterval() { return SERVER.swVehicleChargeInterval.get(); }
    public static double vehicleChargePercentage() { return SERVER.swVehicleChargePercentage.get(); }

    public static int craftCooldownMs() { return SERVER.CRAFT_COOLDOWN_MS.get(); }
    public static int craftMaxDepth() { return SERVER.CRAFT_MAX_DEPTH.get(); }
    public static boolean blockBdContainerReader() { return SERVER.BLOCK_BD_CONTAINER_READER.get(); }

    /** 工作台是否在服务端启用列表中（ID 忽略大小写精确匹配；null/空列表视为全部禁用） */
    public static boolean isWorkstationEnabled(String id) {
        if (id == null || id.isEmpty()) return false;
        for (String s : SERVER.WORKSTATIONS_ENABLED.get()) {
            if (s != null && s.equalsIgnoreCase(id)) return true;
        }
        return false;
    }

    /** 是否启用工作台献祭激活（可选平衡项；默认关闭） */
    public static boolean isWorkstationActivationEnabled() { return SERVER.WORKSTATION_ACTIVATION_ENABLED.get(); }

    /** 是否启用 FTB Quests 集成（检测到 rs_integration 时运行时让路禁用） */
    public static boolean ftbIntegrationEnabled() { return SERVER.FTB_INTEGRATION_ENABLED.get(); }

    /** 工作台献祭成本列表（格式 "<工作台ID>:<物品ID>:<数量>"） */
    public static List<? extends String> workstationActivationCosts() { return SERVER.WORKSTATION_ACTIVATION_COSTS.get(); }

    /**
     * 解析某工作台的献祭成本物品（格式 {@code <工作台ID>:<命名空间>:<路径>:<数量>}）。
     * 未配置/物品无效时返回 {@link net.minecraft.world.item.ItemStack#EMPTY}（视为无需献祭，直接激活）。
     */
    public static net.minecraft.world.item.ItemStack getWorkstationActivationCost(String id) {
        if (id == null || id.isEmpty()) return net.minecraft.world.item.ItemStack.EMPTY;
        for (String raw : workstationActivationCosts()) {
            if (raw == null) continue;
            String[] parts = raw.split(":", 4);
            if (parts.length != 4 || !parts[0].trim().equalsIgnoreCase(id)) continue;
            var rl = net.minecraft.resources.ResourceLocation.tryParse(parts[1].trim() + ":" + parts[2].trim());
            if (rl == null) continue;
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
            if (item == null || item == net.minecraft.world.item.Items.AIR) continue;
            int count;
            try { count = Math.max(1, Integer.parseInt(parts[3].trim())); } catch (NumberFormatException e) { count = 1; }
            return new net.minecraft.world.item.ItemStack(item, count);
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    public static List<? extends String> ammoExtractMappings() { return SERVER.AMMO_EXTRACT_MAPPINGS.get(); }

    public static boolean taczAmmoPollEnabled() { return SERVER.TACZ_AMMO_POLL_ENABLED.get(); }
    public static int taczAmmoPollIntervalTicks() { return SERVER.TACZ_AMMO_POLL_INTERVAL_TICKS.get(); }

    public static boolean swAmmoPollEnabled() { return SERVER.SW_AMMO_POLL_ENABLED.get(); }
    public static int swAmmoPollIntervalTicks() { return SERVER.SW_AMMO_POLL_INTERVAL_TICKS.get(); }
    public static boolean energyAmmoChargeEnabled() { return SERVER.ENERGY_AMMO_CHARGE_ENABLED.get(); }
    public static int energyAmmoChargeInterval() { return SERVER.ENERGY_AMMO_CHARGE_INTERVAL.get(); }
    public static int energyAmmoChargeRate() { return SERVER.ENERGY_AMMO_CHARGE_RATE.get(); }
    public static List<? extends String> energyAmmoChargeWhitelist() { return SERVER.ENERGY_AMMO_CHARGE_WHITELIST.get(); }
    public static boolean energyAmmoChargeCuriosEnabled() { return SERVER.ENERGY_AMMO_CHARGE_CURIOS.get(); }
    public static boolean energyAmmoChargeMaidBaublesEnabled() { return SERVER.ENERGY_AMMO_CHARGE_MAID_BAUBLES.get(); }
    public static EnergyChargeMode energyAmmoChargeMode() { return SERVER.ENERGY_AMMO_CHARGE_MODE.get(); }
    public static double energyAmmoChargePercentage() { return SERVER.ENERGY_AMMO_CHARGE_PERCENTAGE.get(); }

    public static AnvilChargeMode anvilCostMode() { return SERVER.anvilCostMode.get(); }
    public static int anvilLevelCap() { return SERVER.anvilLevelCap.get(); }
    public static long anvilPointsCap() { return SERVER.anvilPointsCap.get(); }
    public static BreakLevelMode anvilBreakLevelMode() { return SERVER.anvilBreakLevelMode.get(); }
    public static boolean anvilIgnoreConflict() { return SERVER.anvilIgnoreConflict.get(); }
    public static boolean anvilIgnoreSupport() { return SERVER.anvilIgnoreSupport.get(); }
    public static boolean anvilUnrestricted() { return SERVER.anvilUnrestricted.get(); }
    public static int anvilConflictPenalty() { return SERVER.anvilConflictPenalty.get(); }
    public static int anvilSupportPenalty() { return SERVER.anvilSupportPenalty.get(); }
    public static int anvilConflictPercent() { return SERVER.anvilConflictPercent.get(); }
    public static int anvilSupportPercent() { return SERVER.anvilSupportPercent.get(); }
    public static int anvilBreakLevelPercent() { return SERVER.anvilBreakLevelPercent.get(); }
    public static int anvilUnrestrictedPercent() { return SERVER.anvilUnrestrictedPercent.get(); }

    public static EnchantPowerMode enchantPowerMode() { return SERVER.enchantPowerMode.get(); }
    public static int enchantFixedPower() { return SERVER.enchantFixedPower.get(); }
    public static int enchantXpPerPower() { return SERVER.enchantXpPerPower.get(); }
    public static int enchantLevelPerPower() { return SERVER.enchantLevelPerPower.get(); }
    public static boolean enchantIgnoreEnchanted() { return SERVER.enchantIgnoreEnchanted.get(); }
    public static boolean enchantIgnoreConflict() { return SERVER.enchantIgnoreConflict.get(); }
    public static boolean enchantNoLapis() { return SERVER.enchantNoLapis.get(); }
    public static boolean enchantLevelGateIgnore() { return SERVER.enchantLevelGateIgnore.get(); }
    public static boolean enchantUncapPower() { return SERVER.enchantUncapPower.get(); }
    public static int enchantCostPercent() { return SERVER.enchantCostPercent.get(); }
    public static boolean enchantAllowTreasure() { return SERVER.enchantAllowTreasure.get(); }
    public static boolean enchantPreviewEnabled() { return SERVER.enchantPreviewEnabled.get(); }
    public static boolean enchantRefreshEnabled() { return SERVER.enchantRefreshEnabled.get(); }
    public static int enchantRefreshLapis() { return SERVER.enchantRefreshLapis.get(); }

    /** BD 修改：经验棒修改总开关（等级上限/分批/直设，关闭 = BD 原行为） */
    public static boolean xpRodTweaksEnabled() { return SERVER.xpRodTweaksEnabled.get(); }

    /** BD 修改：熔炉烧网络终端触发批量烧炼（默认关闭；终端不消耗，产物即终端，取出时触发） */
    public static boolean furnaceTerminalSmeltAllEnabled() { return SERVER.furnaceTerminalSmeltAllEnabled.get(); }

    /** 经验棒可设定的目标等级上限（默认 21863，受经验总量 int 边界约束） */
    public static int xpRodMaxTargetLevel() { return SERVER.xpRodMaxTargetLevel.get(); }

    /** 经验棒每 tick 发放批次上限（0 = 不分批，一次性发放安全量） */
    public static int xpRodGrantBatchSize() { return SERVER.xpRodGrantBatchSize.get(); }

    /** 经验棒经验给予方式（BATCH 分批 / DIRECT 直设式） */
    public static XpGrantMode xpRodGrantMode() { return SERVER.xpRodGrantMode.get(); }

    public static boolean autoTotemEnabled() { return SERVER.AUTO_TOTEM_ENABLED.get(); }
    public static int autoTotemCooldownSeconds() { return SERVER.AUTO_TOTEM_COOLDOWN_SECONDS.get(); }
    public static List<? extends String> autoTotemDamageBlacklist() { return SERVER.AUTO_TOTEM_DAMAGE_BLACKLIST.get(); }
    public static boolean autoTotemRespectBypassesInvulnerability() { return SERVER.AUTO_TOTEM_RESPECT_BYPASSES.get(); }
    public static boolean autoTotemRestoreMaxHealth() { return SERVER.AUTO_TOTEM_RESTORE_MAX_HEALTH.get(); }
    public static boolean autoTotemHealToFull() { return SERVER.AUTO_TOTEM_HEAL_TO_FULL.get(); }

    /** 事件版复活实现开关（配置 false 时不注册处理器，类不加载） */
    public static boolean reviveEventEnabled() { return SERVER.REVIVE_EVENT_ENABLED.get(); }
    /** Mixin 版复活实现开关（注入 checkTotemDeathProtection，配置 false 时方法体内直接返回） */
    public static boolean reviveMixinEnabled() { return SERVER.REVIVE_MIXIN_ENABLED.get(); }
    public static int reviveCooldownSeconds() { return SERVER.REVIVE_COOLDOWN_SECONDS.get(); }
    public static List<? extends String> reviveDamageBlacklist() { return SERVER.REVIVE_DAMAGE_BLACKLIST.get(); }
    public static boolean reviveRespectBypassesInvulnerability() { return SERVER.REVIVE_RESPECT_BYPASSES.get(); }
    public static boolean reviveExtraTotemOnSetHealthDeath() { return SERVER.REVIVE_EXTRA_TOTEM_ON_SET_HEALTH_DEATH.get(); }
    public static int reviveExtraTotemCount() { return SERVER.REVIVE_EXTRA_TOTEM_COUNT.get(); }
    public static boolean reviveRestoreMaxHealth() { return SERVER.REVIVE_RESTORE_MAX_HEALTH.get(); }
    public static boolean reviveHealToFull() { return SERVER.REVIVE_HEAL_TO_FULL.get(); }
    public static boolean reviveResetDeathTime() { return SERVER.REVIVE_RESET_DEATH_TIME.get(); }
}
