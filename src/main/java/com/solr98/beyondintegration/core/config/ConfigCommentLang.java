package com.solr98.beyondintegration.core.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 配置文件注释多语言支持（可扩展）。
 * <p>
 * 注释文本统一放在语言文件 {@code assets/beyond_integration/lang/<语言代码>.json} 中，
 * key 格式为 {@code beyond_integration.config.comment.<配置路径>}（配置路径即
 * {@code CommandConfig} 中 builder 的分区/键路径，如 {@code vehicle.charge_mode}）。
 * <p>
 * 扩展方式：
 * <ul>
 *   <li><b>新增配置项注释</b>：在 {@code CommandConfig} 中调用
 *       {@code .comment(ConfigCommentLang.comment("分区.键"))}，并在
 *       en_us.json / zh_cn.json（以及其它语言文件）添加对应的注释 key；</li>
 *   <li><b>新增语言</b>：新增 {@code <语言代码>.json} 语言文件，并在
 *       {@code CommandConfig.Language} 枚举中添加对应项即可；</li>
 *   <li>语言文件缺失或 key 缺失时自动回退 {@code en_us}；仍缺失时返回路径本身（便于开发期发现遗漏）。</li>
 * </ul>
 * <p>
 * 语言选择：spec 构建时读取已存在配置文件中的 {@code command_language}；
 * 配置文件尚未生成时使用默认 {@code en_us}。配置加载完成后若文件注释语言与
 * 配置语言不一致，通过 {@link ModConfig#save()} 用当前语言的 spec 注释重写
 * （保留 Range/Allowed Values 等自动注释，值不变）。
 */
public final class ConfigCommentLang {

    private static final String CONFIG_FILE = "beyond_integration-common.toml";
    private static final String DEFAULT_LANGUAGE = "en_us";
    private static final String KEY_PREFIX = "beyond_integration.config.comment.";
    private static final String RESOURCE_ROOT = "assets/beyond_integration/lang/";

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 语言代码 -> (key -> 文本)。 */
    private static final Map<String, Map<String, String>> LANG_CACHE = new HashMap<>();

    /** COMMON 配置文件的语言判断样本路径（单行注释，覆盖多个分区）。 */
    private static final String[] COMMON_SAMPLES = {
            "language", "network_list", "vehicle", "blacklist", "craft", "anvil", "auto_totem", "revive"
    };

    /** CLIENT 配置文件的语言判断样本路径（单行注释）。 */
    private static final String[] CLIENT_SAMPLES = {
            "tacz_smith_use_network", "tacz_smith_output_to_network", "enchant_preview_on"
    };

    /** 启动时根据已存在配置决定的目标语言代码；文件不存在 = 默认 en_us。 */
    private static final String TARGET_LANGUAGE = detectLanguage();

    private ConfigCommentLang() {}

    /**
     * 获取指定配置路径的注释文本（按目标语言）。
     *
     * @param path 配置路径，如 {@code vehicle.charge_mode}
     * @return 对应的注释；语言文件/key 缺失时回退 en_us，仍缺失返回 path
     */
    public static String comment(String path) {
        String key = KEY_PREFIX + path;
        String value = getTranslation(TARGET_LANGUAGE, key);
        if (value == null) {
            value = getTranslation(DEFAULT_LANGUAGE, key);
        }
        return value != null ? value : path;
    }

    private static String getTranslation(String language, String key) {
        return LANG_CACHE.computeIfAbsent(language, ConfigCommentLang::loadLanguage).get(key);
    }

    /** 从 classpath 读取语言 json（新增语言只需放入语言文件，无需改代码）。 */
    private static Map<String, String> loadLanguage(String language) {
        String resourcePath = RESOURCE_ROOT + language + ".json";
        try (InputStream in = ConfigCommentLang.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                return Map.of();
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                Map<String, String> map = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        map.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
                return map;
            }
        } catch (Exception e) {
            LOGGER.warn("[Beyond Integration] Failed to load language file {}", resourcePath, e);
            return Map.of();
        }
    }

    /** 读取已存在配置文件中的 command_language（转小写语言代码）；不存在/失败时返回默认。 */
    private static String detectLanguage() {
        Path file = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE);
        if (!Files.exists(file)) {
            return DEFAULT_LANGUAGE;
        }
        CommentedFileConfig config = CommentedFileConfig.builder(file).preserveInsertionOrder().build();
        try {
            config.load();
            Object value = config.get("language.command_language");
            if (value == null) {
                return DEFAULT_LANGUAGE;
            }
            return String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            LOGGER.warn("[Beyond Integration] Failed to read config language", e);
            return DEFAULT_LANGUAGE;
        } finally {
            config.close();
        }
    }

    /**
     * 配置加载完成后调用：若文件中的注释语言与目标语言不一致，
     * 通过 {@link ModConfig#save()} 用当前语言的 spec 注释重写文件（值保持不变）。
     */
    public static void saveIfLanguageChanged(ModConfig config) {
        if (config == null) {
            return;
        }
        Path file = config.getFullPath();
        if (file == null || !Files.exists(file)) {
            return;
        }
        try {
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            String[] samples = config.getType() == ModConfig.Type.CLIENT ? CLIENT_SAMPLES : COMMON_SAMPLES;
            if (fileMatchesLanguage(text, samples)) {
                return;
            }
            config.save();
            LOGGER.info("[Beyond Integration] Config comments switched to {} ({})", TARGET_LANGUAGE, config.getType());
        } catch (IOException e) {
            LOGGER.warn("[Beyond Integration] Failed to switch config comments", e);
        }
    }

    /** 文件中是否已包含目标语言的注释样本（任一命中即视为一致）。 */
    private static boolean fileMatchesLanguage(String text, String[] samples) {
        for (String path : samples) {
            String sample = getTranslation(TARGET_LANGUAGE, KEY_PREFIX + path);
            if (sample != null && !sample.contains("\n") && text.contains(sample)) {
                return true;
            }
        }
        return false;
    }
}
