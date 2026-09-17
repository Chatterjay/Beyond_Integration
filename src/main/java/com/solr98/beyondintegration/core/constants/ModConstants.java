package com.solr98.beyondintegration.core.constants;

/**
 * 模组全局常量集中定义类：集中管理网络 ID 范围、提取数量、缓存过期时间、
 * 同步间隔、界面缩放等各类数值常量，供模组各处统一引用。
 */
public final class ModConstants {
    private ModConstants() { throw new AssertionError("No instances"); }

    /** 网络 ID 上限（含校验边界）。 */
    public static final int MAX_NETWORK_ID = 10000;
    /** 命令中网络 ID 参数允许的最大值。 */
    public static final int MAX_NETWORK_ID_ARG = 9999;
    /** 普通提取默认数量。 */
    public static final int DEFAULT_EXTRACT_COUNT = 64;
    /** 按住 Shift 提取数量。 */
    public static final int SHIFT_EXTRACT_COUNT = 256;
    /** 最大堆叠数量。 */
    public static final int MAX_STACK_SIZE = 64;
    /** 批量合成最大堆叠数量。 */
    public static final int MAX_CRAFT_STACK = 64;
    /** 哨戒塔单次装填弹药上限。 */
    public static final int MAX_SENTRY_RELOAD_AMMO = 9000;
    /** XP 与 mB 的换算倍数。 */
    public static final int XP_TO_MB_MULTIPLIER = 20;
    /** 一次生成附魔的最大数量。 */
    public static final int MAX_GENERATED_ENCHANTMENTS = 5;
    /** 每单位燃料对应的 mB 数。 */
    public static final int MB_PER_FUEL_UNIT = 1000;
    /** FE 转燃料的默认除数（1000 FE = 1 燃料）。 */
    public static final int DEFAULT_FE_TO_FUEL_DIVISOR = 1000;
    /** 载具充电默认速率（FE）。 */
    public static final int DEFAULT_CHARGE_RATE_FE = 500000;
    /** 普通载具缓存存活时间（毫秒）。 */
    public static final long CACHE_TTL_VEHICLE = 120_000L;
    /** SW 载具缓存存活时间（毫秒）。 */
    public static final long CACHE_TTL_SUPERB_VEHICLE = 120_000L;
    /** 女仆相关缓存存活时间（毫秒）。 */
    public static final long CACHE_TTL_MAID = 5_000L;
    /** TACZ 弹药全量缓存存活时间（毫秒）。 */
    public static final long CACHE_TTL_TACZ_AMMO_FULL = 6_000L;
    /** Toast 提示显示时长（毫秒）。 */
    public static final long TOAST_DISPLAY_MS = 2_500L;
    /** 通知冷却时间（毫秒）。 */
    public static final long NOTIFICATION_COOLDOWN_MS = 300_000L;
    /** 载具同步间隔（毫秒）。 */
    public static final long SYNC_INTERVAL_VEHICLE = 2_000L;
    /** 强制同步间隔（毫秒）。 */
    public static final long SYNC_INTERVAL_FORCE = 5_000L;
    /** 每秒刻数。 */
    public static final int TICKS_PER_SECOND = 20;
    /** 短时阈值（秒），用于时间显示分级。 */
    public static final int SHORT_TIME_THRESHOLD_SECONDS = 30;
    /** 中等时长阈值（秒），用于时间显示分级。 */
    public static final int MEDIUM_TIME_THRESHOLD_SECONDS = 300;
    /** 充电间隔最大刻数。 */
    public static final int MAX_CHARGE_INTERVAL_TICKS = 1200;
    /** 需要 OP 权限的等级。 */
    public static final int OP_LEVEL = 2;
    /** 分页每页最大条目数。 */
    public static final int MAX_PAGE_SIZE = 100;
    /** 分页默认每页条目数。 */
    public static final int DEFAULT_PAGE_SIZE = 10;
    /** NBT 大小警告阈值（字节）。 */
    public static final int NBT_SIZE_WARNING_THRESHOLD = 10240;
    /** 槽位尺寸（像素）。 */
    public static final int SLOT_SIZE = 18;
    /** 槽位间距（像素）。 */
    public static final int SLOT_GAP = 2;
    /** 枪械 HUD 网络文本缩放。 */
    public static final float GUN_HUD_NET_TEXT_SCALE = 0.8f;
    /** 弹药数量文本缩放。 */
    public static final float AMMO_COUNT_TEXT_SCALE = 0.666f;
    /** 铁砧网络数量文本缩放。 */
    public static final float SMITH_NET_COUNT_SCALE = 0.5f;
    /** 最小射击所需弹药数。 */
    public static final int MIN_SHOOT_AMMO = 1;
}

