package com.solr98.beyondintegration.handler;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * {@code beyond_integration_data.dat} 的读取/保存入口（BD 网络关联数据的唯一持久化文件）。
 *
 * <p>文件位置：{@code <世界目录>/data/beyond_integration_data.dat}，GZIP 压缩的 NBT，
 * 根结构为 {@code {"data": {...}}}，与 Minecraft {@code DimensionDataStorage}
 * 的存储格式完全一致；本类为 BI 全部 BD 网络关联数据的读写唯一入口：
 * 弹药计数（SW 虚拟弹药/无限弹药）、附魔分离开关、TACZ 创造模式弹药类型与全类型标记。
 *
 * <p>NBT 内容格式（由 {@link NetworkAmmoData} 负责序列化/反序列化）：
 * <pre>
 * root
 * └── "data" (CompoundTag)
 *     ├── "networkAmmo" (CompoundTag)         网络 ID → 弹药映射
 *     │   └── "&lt;netId&gt;" (CompoundTag)        如 "0"
 *     │       └── "ammo" (CompoundTag)
 *     │           ├── "&lt;弹药类型id&gt;" (Long)   数量，如 "__infinite__"
 *     │           └── ...
 *     ├── "enchantSeparation" (CompoundTag)   网络 ID → 附魔分离开关
 *     │   └── "&lt;netId&gt;" (Boolean)
 *     ├── "creativeTypes" (CompoundTag)       网络 ID → TACZ 创造弹药类型集合
 *     │   └── "&lt;netId&gt;" (CompoundTag)
 *     │       ├── "size" (Int)
 *     │       └── "0".."size-1" (String)
 *     └── "creativeAllType" (CompoundTag)     网络 ID → 全类型创造标记（仅 true 写入）
 *         └── "&lt;netId&gt;" (Boolean)
 * </pre>
 *
 * <p>读写时机约定：
 * <ul>
 *   <li>读取：懒加载。任何 {@link NetworkAmmoData#get()} 首次访问时经
 *       {@link #load()} 从主世界 DataStorage 读出并缓存为单实例；</li>
 *   <li>保存：数据变更走 {@code SavedData.setDirty()}，由 MC 定时自动写盘；
 *       服务器停止时 {@link #saveNow()} 兜底强制落盘（已挂 ServerStoppingEvent）。</li>
 * </ul>
 *
 * <p>与 MC 原生规则一致：每次写盘前把旧文件轮转为 {@code .dat_old} 备份，
 * 主文件损坏时可回滚；写盘失败仅 WARN，不影响服务器运行。
 */
public final class NetworkDataStore {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 存档键名（对应文件 {@code <世界目录>/data/beyond_integration_data.dat}） */
    public static final String DATA_NAME = "beyond_integration_data";
    /** 旧版键名（1.20.1 时代 {@code beyond_integration_attachments}，Networks 结构），读取时自动迁移 */
    public static final String LEGACY_DATA_NAME = "beyond_integration_attachments";

    private NetworkDataStore() {}

    /**
     * 读取（懒加载）：
     * <ul>
     *   <li>读取前先执行旧存档兼容迁移（{@link #migrateLegacy}）：若新键文件不存在而
     *       旧键文件存在，自动转化并落盘为新键文件，旧文件删除；</li>
     *   <li>服务端：从主世界 {@code DimensionDataStorage} 按 {@link #DATA_NAME} 读取，
     *       已存在则直接复用缓存实例（保证全服单实例、内存与磁盘一致）；</li>
     *   <li>无服务器（客户端/开发环境）：返回临时空实例，仅保证内存读写安全，不落盘。</li>
     * </ul>
     *
     * @return 网络关联数据的唯一实例
     */
    public static NetworkAmmoData load() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return new NetworkAmmoData();
        migrateLegacy(server);
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(NetworkAmmoData::new, NetworkAmmoData::load), DATA_NAME);
    }

    /**
     * 旧存档兼容迁移：读取时发现旧键文件（{@code beyond_integration_attachments.dat}，
     * 1.20.1 的 Networks 结构）而新键文件不存在时，解析旧结构并转化为统一的
     * {@link #DATA_NAME} 新格式落盘，随后删除旧文件（迁移失败仅 WARN，不影响存档加载）。
     * 供服务端读取前调用；幂等：新文件已存在或旧文件不存在时直接返回。
     */
    private static void migrateLegacy(MinecraftServer server) {
        try {
            // 1.21.1 起 LevelResource 无 DATA_DIR 字段，data 目录为世界根目录下的 "data" 子目录
            java.nio.file.Path dir = server.getWorldPath(LevelResource.ROOT).resolve("data");
            java.nio.file.Path newPath = dir.resolve(DATA_NAME + ".dat");
            java.nio.file.Path oldPath = dir.resolve(LEGACY_DATA_NAME + ".dat");
            if (Files.exists(newPath) || !Files.exists(oldPath)) return;

            CompoundTag root = NbtIo.readCompressed(oldPath, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            CompoundTag dataTag = root.contains("data") ? root.getCompound("data") : new CompoundTag();
            NetworkAmmoData migrated = NetworkAmmoData.loadLegacy(dataTag, server.registryAccess());
            CompoundTag out = new CompoundTag();
            out.put("data", migrated.save(new CompoundTag(), server.registryAccess()));
            NbtIo.writeCompressed(out, newPath);
            Files.delete(oldPath);
            LOGGER.info("NetworkDataStore: 已将旧存档 {} 迁移为 {}", LEGACY_DATA_NAME, DATA_NAME);
        } catch (IOException e) {
            LOGGER.warn("NetworkDataStore: 迁移旧存档 {} 失败 ({})", LEGACY_DATA_NAME, e.getMessage());
        }
    }

    /**
     * 立即保存（兜底落盘）：服务器停止等时机调用，绕过 MC 定时保存直接写入磁盘。
     * <ul>
     *   <li>写入格式：根 {@code {"data": <NetworkAmmoData.save() 结果>}}，GZIP 压缩；</li>
     *   <li>备份机制：旧 {@code .dat} 先轮转为 {@code .dat_old}，再写新文件，
     *       与 MC 原生 {@code DimensionDataStorage} 行为一致；</li>
     *   <li>异常处理：任何 IO 失败仅记录 WARN，不向上抛，不影响服务器停止流程。</li>
     * </ul>
     */
    public static void saveNow() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        try {
            // 1.21.1 起 LevelResource 无 DATA_DIR 字段，data 目录为世界根目录下的 "data" 子目录
            Path dir = server.getWorldPath(LevelResource.ROOT).resolve("data");
            Files.createDirectories(dir);
            Path newFile = dir.resolve(DATA_NAME + ".dat");
            Path oldFile = dir.resolve(DATA_NAME + ".dat_old");

            // 把上次的 .dat 轮转为 .dat_old 备份（与 MC 原生保存规则一致）
            if (Files.exists(newFile)) {
                Files.deleteIfExists(oldFile);
                Files.move(newFile, oldFile, StandardCopyOption.REPLACE_EXISTING);
            }

            // 组装根结构 {"data": {...}} 并 GZIP 压缩写盘
            CompoundTag root = new CompoundTag();
            root.put("data", load().save(new CompoundTag(), server.registryAccess()));
            NbtIo.writeCompressed(root, newFile);
        } catch (IOException e) {
            LOGGER.warn("NetworkDataStore: 保存 {} 失败 ({})", DATA_NAME, e.getMessage());
        }
    }
}
