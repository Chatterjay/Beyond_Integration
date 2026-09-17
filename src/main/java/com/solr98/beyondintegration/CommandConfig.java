package com.solr98.beyondintegration;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import com.solr98.beyondintegration.core.config.ConfigCommentLang;

import java.util.Arrays;
import java.util.List;

/**
 * 模组服务端配置文件定义：集中管理所有 COMMON 类型配置项，
 * 涵盖命令语言、附魔分离、载具充电、物品黑名单、铁砧工作站、自动图腾与弹药轮询等。
 */
public class CommandConfig {
    /** 构建完成的配置规格对象，供 NeoForge 注册使用。 */
    public static final ModConfigSpec SERVER_SPEC;
    /** 配置值访问入口，通过它读取各配置项的当前值。 */
    public static final ServerConfig SERVER;
    static {
        // 构建配置规格并将配置值与规格对象解耦存放
        final Pair<ServerConfig, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(ServerConfig::new);
        SERVER_SPEC = specPair.getRight();
        SERVER = specPair.getLeft();
    }

    /** 命令输出语言选项。 */
    public enum Language { EN_US("en_us"), ZH_CN("zh_cn");
        private final String code;
        Language(String code) { this.code = code; }
        public String getCode() { return code; }
    }

    /** 载具充电模式：关闭 / 固定速率 / 按缺失能量百分比。 */
    public enum ChargeMode { OFF, FLAT_RATE, PERCENTAGE }
    /** 载具燃料来源：FE（网络能量）或 FLUID（流体）。 */
    public enum FuelSource { FE, FLUID }
    /** 铁砧经验收费模式：等级或经验点数。 */
    public enum AnvilChargeMode { LEVEL, POINTS }
    /** 物品装备位网络充电模式：固定值 / 缺失能量百分比 / 百分比 + 固定值。 */
    public enum EnergyChargeMode { RATE, PERCENTAGE, PERCENTAGE_PLUS_RATE }
    /**
     * 附魔台（网络化）附魔功率来源：
     * FIXED       = 固定功率（enchantFixedPower）；
     * NETWORK_XP  = 网络经验流体存量换算（每 enchantXpPerPower 点 = 1 功率）；
     * PLAYER_LEVEL= 玩家经验等级换算（每 enchantLevelPerPower 级 = 1 功率）。
     */
    public enum EnchantPowerMode { FIXED, NETWORK_XP, PLAYER_LEVEL }
    /**
     * 铁砧附魔等级上限模式：
     * OFF      = 原版：钳制附魔最高等级（5 级书 + 5 级书 → 6，但封顶 5）；
     * PLUS     = 叠级突破：保留原版合成规则（同附魔 5+5=6、异附魔取 max），仅解除封顶；
     * ADDITIVE = 相加突破：仅同类附魔等级直接相加（5+5=10），不同附魔仍取 max。
     */
    public enum BreakLevelMode { OFF, PLUS, ADDITIVE }

    /**
     * 经验棒经验给予方式：
     * BATCH  = 分批 giveExperiencePoints（每 tick 一批，合法升级上限 238609312；
     *          再往上升级所需经验超过 int 上限，经验条会异常；totalExperience 从 21863 级起饱和）；
     * DIRECT = 直设式（等级计算 + 设定等级/进度 + 网络经验补差），不依赖原版升级公式。
     */
    public enum XpGrantMode { BATCH, DIRECT }

    /**
     * 服务端配置类：定义所有配置项的默认值与范围，
     * 通过 builder 按分区（push/pop）组织配置界面。
     */
    public static class ServerConfig {
        public final ModConfigSpec.EnumValue<Language> language;
        public final ModConfigSpec.IntValue maxNetworksPerPage;

        // Enchantment separation
        public final ModConfigSpec.BooleanValue enchantSeparation;
        public final ModConfigSpec.IntValue enchantBaseCost;
        public final ModConfigSpec.DoubleValue enchantLevelMult;
        public final ModConfigSpec.DoubleValue enchantDefaultMult;
        public final ModConfigSpec.ConfigValue<List<? extends String>> enchantHighCostList;
        public final ModConfigSpec.BooleanValue enchantDebug;

        // Vehicle energy charge
        public final ModConfigSpec.IntValue swVehicleEnergyChargeRate;
        public final ModConfigSpec.IntValue swVehicleChargeInterval;
        public final ModConfigSpec.DoubleValue swVehicleChargePercentage;

        // Ywzj vehicle energy charge
        public final ModConfigSpec.EnumValue<ChargeMode> ywzjChargeMode;
        public final ModConfigSpec.EnumValue<FuelSource> ywzjFuelSource;
        public final ModConfigSpec.IntValue ywzjVehicleEnergyChargeRate;
        public final ModConfigSpec.IntValue ywzjVehicleChargeInterval;
        public final ModConfigSpec.DoubleValue ywzjVehicleChargePercentage;
        public final ModConfigSpec.IntValue ywzjVehicleEnergyConversion;

// Item blacklist
        public final ModConfigSpec.BooleanValue ENABLE_ITEM_BLACKLIST;
        public final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_BLACKLIST;

        /** 阻止 taczaddon 将 BD 网络方块识别为合成材料容器源 */
        public final ModConfigSpec.BooleanValue BLOCK_BD_CONTAINER_READER;

        // Workstation availability (server-side)
        public final ModConfigSpec.ConfigValue<List<? extends String>> WORKSTATIONS_ENABLED;

        // 工作台献祭激活（可选平衡项）：开启后 anvil/cut/grind/smith/enchant 需先献祭对应原版工作台激活（网络级）
        public final ModConfigSpec.BooleanValue WORKSTATION_ACTIVATION_ENABLED;
        public final ModConfigSpec.ConfigValue<List<? extends String>> WORKSTATION_ACTIVATION_COSTS;

        // FTB Quests 集成（可选；检测到 rs_integration 时让路禁用）
        public final ModConfigSpec.BooleanValue FTB_INTEGRATION_ENABLED;

        // TACZ ammo polling
        public final ModConfigSpec.BooleanValue TACZ_AMMO_POLL_ENABLED;
        public final ModConfigSpec.IntValue TACZ_AMMO_POLL_INTERVAL_TICKS;

        public final ModConfigSpec.BooleanValue SW_AMMO_POLL_ENABLED;
        public final ModConfigSpec.IntValue SW_AMMO_POLL_INTERVAL_TICKS;

        // SW energy ammo network charging
        public final ModConfigSpec.BooleanValue ENERGY_AMMO_CHARGE_ENABLED;
        public final ModConfigSpec.IntValue ENERGY_AMMO_CHARGE_INTERVAL;
        public final ModConfigSpec.IntValue ENERGY_AMMO_CHARGE_RATE;
        public final ModConfigSpec.ConfigValue<List<? extends String>> ENERGY_AMMO_CHARGE_WHITELIST;
        public final ModConfigSpec.BooleanValue ENERGY_AMMO_CHARGE_CURIOS;
        public final ModConfigSpec.BooleanValue ENERGY_AMMO_CHARGE_MAID_BAUBLES;
        public final ModConfigSpec.EnumValue<EnergyChargeMode> ENERGY_AMMO_CHARGE_MODE;
        public final ModConfigSpec.DoubleValue ENERGY_AMMO_CHARGE_PERCENTAGE;

        // Anvil workstation
        public final ModConfigSpec.EnumValue<AnvilChargeMode> anvilCostMode;
        public final ModConfigSpec.IntValue anvilLevelCap;
        public final ModConfigSpec.LongValue anvilPointsCap;
        public final ModConfigSpec.EnumValue<BreakLevelMode> anvilBreakLevelMode;
        public final ModConfigSpec.BooleanValue anvilIgnoreConflict;
        public final ModConfigSpec.BooleanValue anvilIgnoreSupport;
        public final ModConfigSpec.BooleanValue anvilUnrestricted;
        public final ModConfigSpec.IntValue anvilConflictPenalty;
        public final ModConfigSpec.IntValue anvilSupportPenalty;
        public final ModConfigSpec.IntValue anvilConflictPercent;
        public final ModConfigSpec.IntValue anvilSupportPercent;
        public final ModConfigSpec.IntValue anvilBreakLevelPercent;
        public final ModConfigSpec.IntValue anvilUnrestrictedPercent;

        // Enchantment table workstation
        public final ModConfigSpec.EnumValue<EnchantPowerMode> enchantPowerMode;
        public final ModConfigSpec.IntValue enchantFixedPower;
        public final ModConfigSpec.IntValue enchantXpPerPower;
        public final ModConfigSpec.IntValue enchantLevelPerPower;
        public final ModConfigSpec.IntValue enchantApothQuanta;
        public final ModConfigSpec.IntValue enchantApothArcana;
        // Enchantment table workstation boosts/uncaps
        public final ModConfigSpec.BooleanValue enchantIgnoreEnchanted;
        public final ModConfigSpec.BooleanValue enchantIgnoreConflict;
        public final ModConfigSpec.BooleanValue enchantNoLapis;
        public final ModConfigSpec.BooleanValue enchantLevelGateIgnore;
        public final ModConfigSpec.BooleanValue enchantUncapPower;
        public final ModConfigSpec.IntValue enchantCostPercent;
        public final ModConfigSpec.IntValue enchantRefreshLapis;
        public final ModConfigSpec.BooleanValue enchantPreviewEnabled;
        public final ModConfigSpec.BooleanValue enchantRefreshEnabled;
        public final ModConfigSpec.BooleanValue enchantAllowTreasure;

        // Auto totem (network only)
        // BD modifications (tweaks applied to Beyond Dimensions)
        public final ModConfigSpec.BooleanValue xpRodTweaksEnabled;
        /** 熔炉烧网络终端：批量烧炼网络内全部可烧炼物品（默认关闭） */
        public final ModConfigSpec.BooleanValue furnaceTerminalSmeltAllEnabled;
        public final ModConfigSpec.IntValue xpRodMaxTargetLevel;
        public final ModConfigSpec.IntValue xpRodGrantBatchSize;
        public final ModConfigSpec.EnumValue<XpGrantMode> xpRodGrantMode;

        public final ModConfigSpec.BooleanValue AUTO_TOTEM_ENABLED;
        public final ModConfigSpec.IntValue AUTO_TOTEM_COOLDOWN_SECONDS;
        public final ModConfigSpec.ConfigValue<List<? extends String>> AUTO_TOTEM_DAMAGE_BLACKLIST;
        public final ModConfigSpec.BooleanValue AUTO_TOTEM_RESPECT_BYPASSES;
        public final ModConfigSpec.BooleanValue AUTO_TOTEM_RESTORE_MAX_HEALTH;
        public final ModConfigSpec.BooleanValue AUTO_TOTEM_HEAL_TO_FULL;

        // SetHealth revive (network only, event + mixin implementations)
        public final ModConfigSpec.BooleanValue REVIVE_EVENT_ENABLED;
        public final ModConfigSpec.BooleanValue REVIVE_MIXIN_ENABLED;
        public final ModConfigSpec.IntValue REVIVE_COOLDOWN_SECONDS;
        public final ModConfigSpec.ConfigValue<List<? extends String>> REVIVE_DAMAGE_BLACKLIST;
        public final ModConfigSpec.BooleanValue REVIVE_RESPECT_BYPASSES;
        public final ModConfigSpec.BooleanValue REVIVE_EXTRA_TOTEM_ON_SET_HEALTH_DEATH;
        public final ModConfigSpec.IntValue REVIVE_EXTRA_TOTEM_COUNT;
        public final ModConfigSpec.BooleanValue REVIVE_RESTORE_MAX_HEALTH;
        public final ModConfigSpec.BooleanValue REVIVE_HEAL_TO_FULL;
        public final ModConfigSpec.BooleanValue REVIVE_RESET_DEATH_TIME;

        /**
         * ServerConfig 构造函数：按功能分区定义全部配置项及其默认值、校验范围。
         */
        public ServerConfig(ModConfigSpec.Builder builder) {
            builder.comment(ConfigCommentLang.comment("general")).push("general");
            // 命令输出语言
            language = builder
                    .comment(ConfigCommentLang.comment("general.command_language"))
                    .defineEnum("command_language", Language.EN_US);
            // 网络列表每页最大显示数量
            maxNetworksPerPage = builder
                    .comment(ConfigCommentLang.comment("general.max_networks_per_page"))
                    .defineInRange("max_networks_per_page", 10, 1, 100);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("enchant")).push("enchant");
            enchantSeparation = builder
                    .comment(ConfigCommentLang.comment("enchant.separation"))
                    .define("separation", false);
            enchantBaseCost = builder
                    .comment(ConfigCommentLang.comment("enchant.base_cost"))
                    .defineInRange("base_cost", 5, 0, 100);
            enchantLevelMult = builder
                    .comment(ConfigCommentLang.comment("enchant.level_mult"))
                    .defineInRange("level_mult", 1.0, 0.0, 100.0);
            enchantDefaultMult = builder
                    .comment(ConfigCommentLang.comment("enchant.default_mult"))
                    .defineInRange("default_mult", 1.0, 0.0, 100.0);
            enchantHighCostList = builder
                    .comment(ConfigCommentLang.comment("enchant.high_cost"))
                    .defineList("high_cost",
                            Arrays.asList("minecraft:mending:3.0", "minecraft:frost_walker:3.0",
                                    "minecraft:sharpness:1.2", "minecraft:protection:1.2"),
                            obj -> obj instanceof String);
            enchantDebug = builder
                    .comment(ConfigCommentLang.comment("enchant.debug"))
                    .define("debug", false);
            builder.pop();


            builder.comment(ConfigCommentLang.comment("vehicle")).push("vehicle");
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

            builder.comment(ConfigCommentLang.comment("ywzj_vehicle")).push("ywzj_vehicle");
            ywzjChargeMode = builder.comment(ConfigCommentLang.comment("ywzj_vehicle.chargeMode")).defineEnum("chargeMode", ChargeMode.FLAT_RATE);
            ywzjFuelSource = builder.comment(ConfigCommentLang.comment("ywzj_vehicle.fuelSource")).defineEnum("fuelSource", FuelSource.FE);
            ywzjVehicleEnergyChargeRate = builder.comment(ConfigCommentLang.comment("ywzj_vehicle.energyChargeRate")).defineInRange("energyChargeRate", 500000, 0, Integer.MAX_VALUE);
            ywzjVehicleChargeInterval = builder.comment(ConfigCommentLang.comment("ywzj_vehicle.chargeInterval")).defineInRange("chargeInterval", 20, 1, 1200);
            ywzjVehicleChargePercentage = builder.comment(ConfigCommentLang.comment("ywzj_vehicle.chargePercentage")).defineInRange("chargePercentage", 5.0, 0.1, 100.0);
            ywzjVehicleEnergyConversion = builder.comment(ConfigCommentLang.comment("ywzj_vehicle.energyConversion")).defineInRange("energyConversion", 1000, 1, Integer.MAX_VALUE);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("blacklist")).push("blacklist");
            ENABLE_ITEM_BLACKLIST = builder.comment(ConfigCommentLang.comment("blacklist.enable")).define("enable", false);
            ITEM_BLACKLIST = builder.comment(ConfigCommentLang.comment("blacklist.items")).defineList("items",
                    Arrays.asList("minecraft:barrier", "minecraft:command_block"), obj -> obj instanceof String);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("craft")).push("craft");
            BLOCK_BD_CONTAINER_READER = builder
                    .comment(ConfigCommentLang.comment("craft.block_bd_reader"))
                    .define("block_bd_reader", true);
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

            builder.comment(ConfigCommentLang.comment("anvil")).push("anvil");
            anvilCostMode = builder.comment(ConfigCommentLang.comment("anvil.costMode"))
                    .defineEnum("costMode", AnvilChargeMode.POINTS);
            anvilLevelCap = builder.comment(ConfigCommentLang.comment("anvil.levelCap"))
                    .defineInRange("levelCap", 40, 1, Integer.MAX_VALUE);
            anvilPointsCap = builder.comment(ConfigCommentLang.comment("anvil.pointsCap"))
                    .defineInRange("pointsCap", Long.MAX_VALUE, 1, Long.MAX_VALUE);
            anvilBreakLevelMode = builder.comment(ConfigCommentLang.comment("anvil.breakMaxLevelMode"))
                    .defineEnum("breakMaxLevelMode", BreakLevelMode.OFF);
            anvilIgnoreConflict = builder.comment(ConfigCommentLang.comment("anvil.ignoreConflict"))
                    .define("ignoreConflict", false);
            anvilIgnoreSupport = builder.comment(ConfigCommentLang.comment("anvil.ignoreSupport"))
                    .define("ignoreSupport", false);
            anvilUnrestricted = builder.comment(ConfigCommentLang.comment("anvil.unrestricted"))
                    .define("unrestricted", false);
            anvilConflictPenalty = builder.comment(ConfigCommentLang.comment("anvil.conflictPenalty"))
                    .defineInRange("conflictPenalty", 2, 0, 1000000);
            anvilSupportPenalty = builder.comment(ConfigCommentLang.comment("anvil.supportPenalty"))
                    .defineInRange("supportPenalty", 5, 0, 1000000);
            anvilConflictPercent = builder.comment(ConfigCommentLang.comment("anvil.conflictPercent"))
                    .defineInRange("conflictPercent", 0, 0, 1000000);
            anvilSupportPercent = builder.comment(ConfigCommentLang.comment("anvil.supportPercent"))
                    .defineInRange("supportPercent", 0, 0, 1000000);
            anvilBreakLevelPercent = builder.comment(ConfigCommentLang.comment("anvil.breakLevelPercent"))
                    .defineInRange("breakLevelPercent", 0, 0, 1000000);
            anvilUnrestrictedPercent = builder.comment(ConfigCommentLang.comment("anvil.unrestrictedPercent"))
                    .defineInRange("unrestrictedPercent", 100, 0, 1000000);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("enchant")).push("enchant");
            enchantPowerMode = builder.comment(ConfigCommentLang.comment("enchant.powerMode"))
                    .defineEnum("powerMode", EnchantPowerMode.FIXED);
            enchantFixedPower = builder.comment(ConfigCommentLang.comment("enchant.fixedPower"))
                    .defineInRange("fixedPower", 15, 0, 100);
            enchantXpPerPower = builder.comment(ConfigCommentLang.comment("enchant.xpPerPower"))
                    .defineInRange("xpPerPower", 100, 1, Integer.MAX_VALUE);
            enchantLevelPerPower = builder.comment(ConfigCommentLang.comment("enchant.levelPerPower"))
                    .defineInRange("levelPerPower", 1, 1, Integer.MAX_VALUE);
            enchantApothQuanta = builder.comment(ConfigCommentLang.comment("enchant.apothQuanta"))
                    .defineInRange("apothQuanta", 15, 0, 100);
            enchantApothArcana = builder.comment(ConfigCommentLang.comment("enchant.apothArcana"))
                    .defineInRange("apothArcana", 0, 0, 100);
            enchantIgnoreEnchanted = builder.comment(ConfigCommentLang.comment("enchant.ignoreEnchanted"))
                    .define("ignoreEnchanted", false);
            enchantIgnoreConflict = builder.comment(ConfigCommentLang.comment("enchant.ignoreConflict"))
                    .define("ignoreConflict", false);
            enchantNoLapis = builder.comment(ConfigCommentLang.comment("enchant.noLapis"))
                    .define("noLapis", false);
            enchantLevelGateIgnore = builder.comment(ConfigCommentLang.comment("enchant.levelGateIgnore"))
                    .define("levelGateIgnore", false);
            enchantUncapPower = builder.comment(ConfigCommentLang.comment("enchant.uncapPower"))
                    .define("uncapPower", false);
            enchantCostPercent = builder.comment(ConfigCommentLang.comment("enchant.costPercent"))
                    .defineInRange("costPercent", 100, 10, 500);
            enchantRefreshLapis = builder.comment(ConfigCommentLang.comment("enchant.refreshLapis"))
                    .defineInRange("refreshLapis", 1, 0, 64);
            enchantPreviewEnabled = builder.comment(ConfigCommentLang.comment("enchant.previewEnabled"))
                    .define("previewEnabled", true);
            enchantRefreshEnabled = builder.comment(ConfigCommentLang.comment("enchant.refreshEnabled"))
                    .define("refreshEnabled", true);
            enchantAllowTreasure = builder.comment(ConfigCommentLang.comment("enchant.allowTreasure"))
                    .define("allowTreasure", false);
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
        }
    }

    public static Language getCommandLanguage() {
        try {
            return SERVER.language.get();
        } catch (IllegalStateException e) {
            return Language.EN_US;
        }
    }
    /** 返回命令列表每页显示的网络数量上限。 */
    public static int maxNetworksPerPage() { return SERVER.maxNetworksPerPage.get(); }
    public static boolean enableItemBlacklist() { return SERVER.ENABLE_ITEM_BLACKLIST.get(); }
    public static List<? extends String> itemBlacklist() { return SERVER.ITEM_BLACKLIST.get(); }

    /** 是否阻止 taczaddon 将 BD 网络方块识别为合成材料容器源（默认开启） */
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
    public static int vehicleChargeInterval() { return SERVER.swVehicleChargeInterval.get(); }
    public static double vehicleChargePercentage() { return SERVER.swVehicleChargePercentage.get(); }

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
    public static int enchantApothQuanta() { return SERVER.enchantApothQuanta.get(); }
    public static int enchantApothArcana() { return SERVER.enchantApothArcana.get(); }
    public static boolean enchantIgnoreEnchanted() { return SERVER.enchantIgnoreEnchanted.get(); }
    public static boolean enchantIgnoreConflict() { return SERVER.enchantIgnoreConflict.get(); }
    public static boolean enchantNoLapis() { return SERVER.enchantNoLapis.get(); }
    public static boolean enchantLevelGateIgnore() { return SERVER.enchantLevelGateIgnore.get(); }
    public static boolean enchantUncapPower() { return SERVER.enchantUncapPower.get(); }
    public static int enchantCostPercent() { return SERVER.enchantCostPercent.get(); }
    public static int enchantRefreshLapis() { return SERVER.enchantRefreshLapis.get(); }
    public static boolean enchantPreviewEnabled() { return SERVER.enchantPreviewEnabled.get(); }
    public static boolean enchantRefreshEnabled() { return SERVER.enchantRefreshEnabled.get(); }
    public static boolean enchantAllowTreasure() { return SERVER.enchantAllowTreasure.get(); }
    public static boolean enchantDebug() { return SERVER.enchantDebug.get(); }

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
    /** 返回自动图腾不触发的伤害类型黑名单。 */
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

    public static ChargeMode ywzjChargeMode() { return SERVER.ywzjChargeMode.get(); }
    public static FuelSource ywzjFuelSource() { return SERVER.ywzjFuelSource.get(); }
    public static int ywzjVehicleEnergyChargeRate() { return SERVER.ywzjVehicleEnergyChargeRate.get(); }
    public static int ywzjVehicleChargeInterval() { return SERVER.ywzjVehicleChargeInterval.get(); }
    public static double ywzjVehicleChargePercentage() { return SERVER.ywzjVehicleChargePercentage.get(); }
    public static int ywzjVehicleEnergyConversion() { return SERVER.ywzjVehicleEnergyConversion.get(); }

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
}

