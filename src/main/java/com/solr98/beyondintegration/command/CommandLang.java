package com.solr98.beyondintegration.command;

import net.minecraft.network.chat.Component;
import com.solr98.beyondintegration.CommandConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * 命令多语言文本中心：集中管理所有命令提示文本的中/英文双语翻译，
 * 按当前配置的语言（CommandConfig）返回对应文本或 Component。
 */
public class CommandLang
{
    /** 翻译表：键 -> (语言 -> 文本) */
    private static final Map<String, Map<CommandConfig.Language, String>> TRANSLATIONS = new HashMap<>();

    static
    {
        register("network.list.title",
                CommandConfig.Language.EN_US, "====== Dimension Networks ======",
                CommandConfig.Language.ZH_CN, "========= 维度网络列表 =========");

        register("network.list.all_title",
                CommandConfig.Language.EN_US, "=== All Dimension Networks Page: %s ===",
                CommandConfig.Language.ZH_CN, "=== 所有维度网络 第 %s 页 ===");

        register("network.list.owner",
                CommandConfig.Language.EN_US, "Owner",
                CommandConfig.Language.ZH_CN, "所有者");

        register("network.list.players",
                CommandConfig.Language.EN_US, "Players",
                CommandConfig.Language.ZH_CN, "玩家数");

        register("network.list.managers",
                CommandConfig.Language.EN_US, "Managers",
                CommandConfig.Language.ZH_CN, "管理员数");

        register("network.list.deleted_mark",
                CommandConfig.Language.EN_US, "[DELETED]",
                CommandConfig.Language.ZH_CN, "[已删除]");

        register("network.list.none",
                CommandConfig.Language.EN_US, "No networks found.",
                CommandConfig.Language.ZH_CN, "未找到网络。");

        register("network.list.none_all",
                CommandConfig.Language.EN_US, "No networks found.",
                CommandConfig.Language.ZH_CN, "未找到网络。");

        register("network.info.title",
                CommandConfig.Language.EN_US, "======= Network Info (ID: %s) =======",
                CommandConfig.Language.ZH_CN, "======= 网络信息 (ID: %s) =======");

        register("network.info.unknown",
                CommandConfig.Language.EN_US, "Unknown",
                CommandConfig.Language.ZH_CN, "未知");

        register("network.info.status.active",
                CommandConfig.Language.EN_US, "Active",
                CommandConfig.Language.ZH_CN, "活跃");

        register("network.info.status.deleted",
                CommandConfig.Language.EN_US, "Deleted",
                CommandConfig.Language.ZH_CN, "已删除");

        register("network.info.players",
                CommandConfig.Language.EN_US, "Players",
                CommandConfig.Language.ZH_CN, "玩家数");

        register("network.info.managers",
                CommandConfig.Language.EN_US, "Managers",
                CommandConfig.Language.ZH_CN, "管理员数");

        register("network.info.slot_capacity",
                CommandConfig.Language.EN_US, "Slot Capacity (per type)",
                CommandConfig.Language.ZH_CN, "槽位容量 (每种)");

        register("network.info.slot_max_size",
                CommandConfig.Language.EN_US, "Slot Max Size (types)",
                CommandConfig.Language.ZH_CN, "槽位数量 (种类)");



        register("network.info.not_exist",
                CommandConfig.Language.EN_US, "Network does not exist: netId=%s",
                CommandConfig.Language.ZH_CN, "网络不存在：netId=%s");

        register("network.generateItems.success",
                CommandConfig.Language.EN_US, "Generated %s types of items, total %s items inserted into network %s",
                CommandConfig.Language.ZH_CN, "已生成 %s 种物品，共 %s 个物品插入到网络 %s");



        register("network.batchCreate.success",
                CommandConfig.Language.EN_US, "Successfully created %s networks",
                CommandConfig.Language.ZH_CN, "成功创建 %s 个网络");

        register("network.restore.success",
                CommandConfig.Language.EN_US, "Restored network: netId=%s",
                CommandConfig.Language.ZH_CN, "已恢复网络：netId=%s");

        register("network.restore.not_deleted",
                CommandConfig.Language.EN_US, "Network is not deleted: netId=%s",
                CommandConfig.Language.ZH_CN, "网络未被删除：netId=%s");

        register("network.restore.not_exist",
                CommandConfig.Language.EN_US, "Network does not exist: netId=%s",
                CommandConfig.Language.ZH_CN, "网络不存在：netId=%s");

        register("network.insert.success",
                CommandConfig.Language.EN_US, "Inserted %s x %s into network %s",
                CommandConfig.Language.ZH_CN, "已插入 %s x %s 到网络 %s");

        register("network.giveTerminal.success",
                CommandConfig.Language.EN_US, "Given portable network terminal (bound to netId=%s, owner=%s) x%s",
                CommandConfig.Language.ZH_CN, "已给予便携网络终端 (绑定到 netId=%s, 所有者=%s) x%s");

        register("network.giveTerminal.item_name",
                CommandConfig.Language.EN_US, "Owner: %s's Network #%s",
                CommandConfig.Language.ZH_CN, "所有者: %s 的 %s号网络");

        register("network.giveTerminal.item_description",
                CommandConfig.Language.EN_US, "Owner: %s\nNetwork ID: %s",
                CommandConfig.Language.ZH_CN, "所有者: %s\n网络ID: %s");

        register("network.giveTerminal.not_exist",
                CommandConfig.Language.EN_US, "Network does not exist: netId=%s",
                CommandConfig.Language.ZH_CN, "网络不存在：netId=%s");

        register("error.server_not_available",
                CommandConfig.Language.EN_US, "Server not available.",
                CommandConfig.Language.ZH_CN, "服务器不可用。");

        register("error.player_required",
                CommandConfig.Language.EN_US, "This command must be run by a player.",
                CommandConfig.Language.ZH_CN, "此命令必须由玩家执行。");

        register("error.item_not_found",
                CommandConfig.Language.EN_US, "Portable network terminal item not found.",
                CommandConfig.Language.ZH_CN, "未找到便携网络终端物品。");



        register("network.list.page",
                CommandConfig.Language.EN_US, "Page %s",
                CommandConfig.Language.ZH_CN, "第 %s 页");

        register("network.list.previous",
                CommandConfig.Language.EN_US, "◀ Previous",
                CommandConfig.Language.ZH_CN, "◀ 上一页");

        register("network.list.next",
                CommandConfig.Language.EN_US, "Next ▶",
                CommandConfig.Language.ZH_CN, "下一页 ▶");

        register("network.list.page_with_total",
                CommandConfig.Language.EN_US, "Page %s/%s (Total: %s networks)",
                CommandConfig.Language.ZH_CN, "第 %s/%s 页 (总计: %s 个网络)");

        register("network.list.player_title",
                CommandConfig.Language.EN_US, "=== %s's Networks Page: %s ===",
                CommandConfig.Language.ZH_CN, "=== %s 的网络 第 %s 页 ===");

        register("network.list.permission",
                CommandConfig.Language.EN_US, "YOU Permission",
                CommandConfig.Language.ZH_CN, "你的权限");

        register("network.open.success",
                CommandConfig.Language.EN_US, "Opened %s GUI for network %s for player %s",
                CommandConfig.Language.ZH_CN, "已为玩家 %s 打开网络 %s 的 %s 界面");

        register("network.open.error.no_permission",
                CommandConfig.Language.EN_US, "Player %s does not have permission to access network %s",
                CommandConfig.Language.ZH_CN, "玩家 %s 没有权限访问网络 %s");

        register("network.open.error.not_exist",
                CommandConfig.Language.EN_US, "Network ID %s does not exist or has been deleted",
                CommandConfig.Language.ZH_CN, "网络ID %s 不存在或已被删除");

        register("network.open.error.player_required",
                CommandConfig.Language.EN_US, "This command must be executed by a player or specify a target player",
                CommandConfig.Language.ZH_CN, "此命令必须由玩家执行或指定目标玩家");

        register("network.open.error.general",
                CommandConfig.Language.EN_US, "Error opening GUI: %s",
                CommandConfig.Language.ZH_CN, "打开界面时出错: %s");

        register("network.open.error.failed",
                CommandConfig.Language.EN_US, "Failed to open interface",
                CommandConfig.Language.ZH_CN, "打开界面失败");

        register("error.not_in_network",
                CommandConfig.Language.EN_US, "You are not in any network.",
                CommandConfig.Language.ZH_CN, "您不在任何网络中。");

        register("error.network_not_found",
                CommandConfig.Language.EN_US, "ID does not correspond to any network (or it was deleted).",
                CommandConfig.Language.ZH_CN, "ID不对应任何网络（或已被删除）。");

        register("network.restore.note",
                CommandConfig.Language.EN_US, "Note: Network ID restoration may be limited in this version",
                CommandConfig.Language.ZH_CN, "注意：此版本中网络ID恢复可能受限");

        register("error.network_id_limit",
                CommandConfig.Language.EN_US, "Cannot find available network ID (may have reached the limit of 10000)",
                CommandConfig.Language.ZH_CN, "无法找到可用的网络ID（可能已达到上限10000）");

        register("network.batchCreate.error.no_available_id",
                CommandConfig.Language.EN_US, "Cannot find available network ID",
                CommandConfig.Language.ZH_CN, "无法找到可用网络ID");

        register("network.batchCreate.error.creation_failed",
                CommandConfig.Language.EN_US, "Network #%s: Creation failed (player may already have a network)",
                CommandConfig.Language.ZH_CN, "网络#%s: 创建失败（玩家可能已有网络）");

        register("network.batchCreate.error.general_failure",
                CommandConfig.Language.EN_US, "Network #%s: %s",
                CommandConfig.Language.ZH_CN, "网络#%s: %s");

        register("network.batchCreate.error.no_networks_created",
                CommandConfig.Language.EN_US, "No networks were created",
                CommandConfig.Language.ZH_CN, "未创建任何网络");

        register("network.open.menu.storage",
                CommandConfig.Language.EN_US, "Network Storage",
                CommandConfig.Language.ZH_CN, "网络存储");

        register("network.open.menu.crafting",
                CommandConfig.Language.EN_US, "Network Crafting",
                CommandConfig.Language.ZH_CN, "网络合成");

        register("network.open.menu.terminal",
                CommandConfig.Language.EN_US, "Network Terminal",
                CommandConfig.Language.ZH_CN, "网络终端");

        register("network.open.menu.permission",
                CommandConfig.Language.EN_US, "Network Permission Control",
                CommandConfig.Language.ZH_CN, "网络权限控制");

        register("network.open.error.no_permission_control",
                CommandConfig.Language.EN_US, "Player %s does not have permission to control network %s (requires owner or manager)",
                CommandConfig.Language.ZH_CN, "玩家 %s 没有权限控制网络 %s (需要所有者或管理员权限)");

        register("network.open.error.no_primary_network",
                CommandConfig.Language.EN_US, "Player %s does not have a primary network",
                CommandConfig.Language.ZH_CN, "玩家 %s 没有主要网络");

        register("network.open.error.cannot_get_network_id",
                CommandConfig.Language.EN_US, "Cannot get network ID",
                CommandConfig.Language.ZH_CN, "无法获取网络ID");

        register("network.open.error.not_primary_network",
                CommandConfig.Language.EN_US, "Network %s is not player %s's primary network (primary network is %s). Use /bdtools myNetworks to set primary network.",
                CommandConfig.Language.ZH_CN, "网络 %s 不是玩家 %s 的主要网络（主要网络是 %s）。使用 /bdtools myNetworks 设置主要网络。");

        register("network.batchAdd.success",
                CommandConfig.Language.EN_US, "Successfully added %s players as %s to network %s: %s",
                CommandConfig.Language.ZH_CN, "成功添加 %s 个玩家为网络 %s 的%s: %s");

        register("network.batchAdd.already_in_network",
                CommandConfig.Language.EN_US, "Players already in network: %s",
                CommandConfig.Language.ZH_CN, "以下玩家已在网络中: %s");

        register("network.batchAddPlayer.success",
                CommandConfig.Language.EN_US, "Successfully added %s/%s players as %s to network %s",
                CommandConfig.Language.ZH_CN, "成功添加 %s/%s 名玩家为%s到网络 %s");

        register("network.batchAddPlayer.already_in_networks",
                CommandConfig.Language.EN_US, "Player already in network(s): %s",
                CommandConfig.Language.ZH_CN, "玩家已在以下网络中: %s");

        register("network.batchAddPlayer.not_exist",
                CommandConfig.Language.EN_US, "Network(s) do not exist: %s",
                CommandConfig.Language.ZH_CN, "以下网络不存在: %s");

        register("network.batchAddPlayer.no_permission",
                CommandConfig.Language.EN_US, "No permission to add player to network(s): %s",
                CommandConfig.Language.ZH_CN, "无权限添加玩家到以下网络: %s");

        register("network.batchAddPlayer.failed",
                CommandConfig.Language.EN_US, "Failed to add player to network(s): %s",
                CommandConfig.Language.ZH_CN, "添加玩家到以下网络失败: %s");

        register("network.batchAddPlayer.error.invalid_networks",
                CommandConfig.Language.EN_US, "Invalid network IDs: %s",
                CommandConfig.Language.ZH_CN, "无效的网络ID: %s");

        register("network.batchAddPlayer.no_networks",
                CommandConfig.Language.EN_US, "No networks specified",
                CommandConfig.Language.ZH_CN, "未指定网络");

        register("network.batchAddToNetworks.success",
                CommandConfig.Language.EN_US, "Successfully added %s/%s players as %s",
                CommandConfig.Language.ZH_CN, "成功添加 %s/%s 名玩家为%s");

        register("network.batchAddToNetworks.already_in_network",
                CommandConfig.Language.EN_US, "Players already in network(s):",
                CommandConfig.Language.ZH_CN, "玩家已在以下网络中:");

        register("network.batchAddToNetworks.no_permission",
                CommandConfig.Language.EN_US, "No permission to add players to network(s):",
                CommandConfig.Language.ZH_CN, "无权限添加玩家到以下网络:");

        register("error.add_player_failed",
                CommandConfig.Language.EN_US, "Failed to add player: %s",
                CommandConfig.Language.ZH_CN, "添加玩家失败: %s");

        register("error.remove_player_failed",
                CommandConfig.Language.EN_US, "Failed to remove player: %s",
                CommandConfig.Language.ZH_CN, "移除玩家失败: %s");

        register("network.batchAddToNetworks.failed",
                CommandConfig.Language.EN_US, "Failed to add players to network(s):",
                CommandConfig.Language.ZH_CN, "添加玩家到以下网络失败:");

        register("network.batchAdd.failed",
                CommandConfig.Language.EN_US, "Failed to add players: %s",
                CommandConfig.Language.ZH_CN, "以下玩家添加失败: %s");

        register("network.batchAdd.no_players",
                CommandConfig.Language.EN_US, "No players were added (player list may be empty)",
                CommandConfig.Language.ZH_CN, "没有玩家被添加（玩家列表可能为空）");

        register("network.myNetworks.title.self",
                CommandConfig.Language.EN_US, "==== Your Network Page %s ===",
                CommandConfig.Language.ZH_CN, "==== 你的网络 第 %s 页 ===");

        register("network.myNetworks.title.other",
                CommandConfig.Language.EN_US, "=== Network Permissions for Player %s ===",
                CommandConfig.Language.ZH_CN, "======= 玩家 %s 拥有的网络权限 =======");

        register("network.myNetworks.none",
                CommandConfig.Language.EN_US, "This player has no network permissions",
                CommandConfig.Language.ZH_CN, "该玩家没有任何网络权限");

        register("network.myNetworks.permission.owner",
                CommandConfig.Language.EN_US, "Owner",
                CommandConfig.Language.ZH_CN, "所有者");

        register("network.myNetworks.permission.manager",
                CommandConfig.Language.EN_US, "Manager",
                CommandConfig.Language.ZH_CN, "管理员");

        register("network.myNetworks.permission.member",
                CommandConfig.Language.EN_US, "Member",
                CommandConfig.Language.ZH_CN, "成员");



        register("error.op_required_for_others",
                CommandConfig.Language.EN_US, "Only OP can view other players' network information",
                CommandConfig.Language.ZH_CN, "只有OP可以查看其他玩家的网络信息");

        register("network.batchAdd.error.owner_required",
                CommandConfig.Language.EN_US, "Only network owner can add managers to network %s",
                CommandConfig.Language.ZH_CN, "只有网络所有者可以向网络 %s 添加管理员");

        register("network.batchAdd.error.owner_or_manager_required",
                CommandConfig.Language.EN_US, "Only network owner or manager can add members to network %s",
                CommandConfig.Language.ZH_CN, "只有网络所有者或管理员可以向网络 %s 添加成员");

        register("error.invalid_resource_type",
                CommandConfig.Language.EN_US, "Invalid resource type: %s",
                CommandConfig.Language.ZH_CN, "无效的资源类型: %s");

        register("network.batchAddPlayer.error.no_valid_networks",
                CommandConfig.Language.EN_US, "No valid networks found to add player to",
                CommandConfig.Language.ZH_CN, "未找到有效的网络来添加玩家");

        register("network.batchAddPlayer.no_networks",
                CommandConfig.Language.EN_US, "No networks specified",
                CommandConfig.Language.ZH_CN, "未指定网络");

        register("network.batchRemove.failed",
                CommandConfig.Language.EN_US, "Failed to remove players: %s",
                CommandConfig.Language.ZH_CN, "以下玩家移除失败: %s");

        register("network.batchRemove.no_players",
                CommandConfig.Language.EN_US, "No players were removed (player list may be empty)",
                CommandConfig.Language.ZH_CN, "没有玩家被移除（玩家列表可能为空）");

        register("network.batchRemove.not_in_network",
                CommandConfig.Language.EN_US, "Players not in network: %s",
                CommandConfig.Language.ZH_CN, "以下玩家不在网络中: %s");

        register("network.batchRemove.success",
                CommandConfig.Language.EN_US, "Successfully removed %s players from network %s",
                CommandConfig.Language.ZH_CN, "成功从网络 %s 移除 %s 个玩家");

        register("network.generateResources.success",
                CommandConfig.Language.EN_US, "Generated %s resources of type %s, total %s inserted into network %s",
                CommandConfig.Language.ZH_CN, "已生成 %s 个%s资源，共 %s 插入到网络 %s");

        register("network.generateItems.resource_type.items",
                CommandConfig.Language.EN_US, "item",
                CommandConfig.Language.ZH_CN, "物品");

        register("network.generateItems.resource_type.fluids",
                CommandConfig.Language.EN_US, "fluid",
                CommandConfig.Language.ZH_CN, "流体");

        register("network.generateItems.resource_type.energy",
                CommandConfig.Language.EN_US, "energy",
                CommandConfig.Language.ZH_CN, "能量");

        register("network.generateItems.resource_type.mixed",
                CommandConfig.Language.EN_US, "mixed",
                CommandConfig.Language.ZH_CN, "混合");

        register("network.generateItems.resource_type.all",
                CommandConfig.Language.EN_US, "all",
                CommandConfig.Language.ZH_CN, "全部");

        register("network.generateResources.detailed_title",
                CommandConfig.Language.EN_US, "=== Generated %s Resources for Network %s ===",
                CommandConfig.Language.ZH_CN, "=== 为网络 %s 生成的%s资源 ===");

        register("network.generateResources.detailed_items",
                CommandConfig.Language.EN_US, "  Items: %s types, %s total",
                CommandConfig.Language.ZH_CN, "  物品: %s 种, %s 个");

        register("network.generateResources.detailed_fluids",
                CommandConfig.Language.EN_US, "  Fluids: %s types, %s mB total",
                CommandConfig.Language.ZH_CN, "  流体: %s 种, %s mB");

        register("network.generateResources.detailed_energy",
                CommandConfig.Language.EN_US, "  Energy: %s types, %s FE total",
                CommandConfig.Language.ZH_CN, "  能量: %s 种, %s FE");

        register("network.generateResources.detailed_total",
                CommandConfig.Language.EN_US, "  Total: %s resource types, %s resources inserted",
                CommandConfig.Language.ZH_CN, "  总计: %s 种资源类型, %s 个资源已插入");



        register("network.myNetworks.info.format",
                CommandConfig.Language.EN_US, "Network %s | Permission: %s | Owner: %s | Players: %s | Managers: %s",
                CommandConfig.Language.ZH_CN, "网络 %s | 权限: %s | 所有者: %s | 玩家: %s | 管理员: %s");

        register("network.info.title",
                CommandConfig.Language.EN_US, "=== Network Info (ID: %s) ===",
                CommandConfig.Language.ZH_CN, "=== 网络信息 (ID: %s) ===");

        register("network.info.owner_label",
                CommandConfig.Language.EN_US, "Owner: %s | Status: ",
                CommandConfig.Language.ZH_CN, "所有者: %s | 状态: ");

        register("network.info.your_permission_label",
                CommandConfig.Language.EN_US, " | Your Permission: ",
                CommandConfig.Language.ZH_CN, " | 你的权限: ");

        register("network.info.status.active",
                CommandConfig.Language.EN_US, "Active",
                CommandConfig.Language.ZH_CN, "活跃");

        register("network.info.status.deleted",
                CommandConfig.Language.EN_US, "Deleted",
                CommandConfig.Language.ZH_CN, "已删除");

        register("network.info.crystal_time",
                CommandConfig.Language.EN_US, "Crystal Remaining Time: ",
                CommandConfig.Language.ZH_CN, "结晶生成剩余时间: ");

        register("network.info.slot_capacity_label",
                CommandConfig.Language.EN_US, "Slot Capacity: ",
                CommandConfig.Language.ZH_CN, "槽位容量: ");

        register("network.info.slot_count_label",
                CommandConfig.Language.EN_US, " | Slot Count: ",
                CommandConfig.Language.ZH_CN, " | 槽位数量: ");

        register("network.info.storage_stats",
                CommandConfig.Language.EN_US, "Storage Statistics:",
                CommandConfig.Language.ZH_CN, "存储统计:");

        register("network.info.items_label",
                CommandConfig.Language.EN_US, "  Items: ",
                CommandConfig.Language.ZH_CN, "  物品: ");

        register("network.info.types_suffix",
                CommandConfig.Language.EN_US, " types, Total: ",
                CommandConfig.Language.ZH_CN, " 种, 总量: ");

        register("network.info.fluids_label",
                CommandConfig.Language.EN_US, "  Fluids: ",
                CommandConfig.Language.ZH_CN, "  流体: ");

        register("network.info.energy_label",
                CommandConfig.Language.EN_US, "  Energy: ",
                CommandConfig.Language.ZH_CN, "  能量: ");

        register("network.info.player_count_label",
                CommandConfig.Language.EN_US, "Player Count: ",
                CommandConfig.Language.ZH_CN, "玩家数: ");

        register("network.info.manager_count_label",
                CommandConfig.Language.EN_US, " | Managers: ",
                CommandConfig.Language.ZH_CN, " | 管理员: ");

        register("network.info.player_list_label",
                CommandConfig.Language.EN_US, "Player List: ",
                CommandConfig.Language.ZH_CN, "玩家列表: ");

        register("network.info.no_players",
                CommandConfig.Language.EN_US, "No players",
                CommandConfig.Language.ZH_CN, "无玩家");

        register("network.info.no_resources",
                CommandConfig.Language.EN_US, "  No resources",
                CommandConfig.Language.ZH_CN, "  无资源");
        
        // NBT大小信息（OP手动触发）
        register("network.info.nbt_section_title",
                CommandConfig.Language.EN_US, "=== NBT Data Analysis (Manually Triggered) ===",
                CommandConfig.Language.ZH_CN, "=== NBT数据分析（手动触发） ===");
        
        register("network.info.nbt_calculation_failed",
                CommandConfig.Language.EN_US, "  Failed to calculate NBT size",
                CommandConfig.Language.ZH_CN, "  计算NBT大小失败");
        
        register("network.info.nbt_size",
                CommandConfig.Language.EN_US, "  NBT Item Types: %s, Total Size: %s",
                CommandConfig.Language.ZH_CN, "  NBT物品种类: %s, 总大小: %s");
        
        register("network.info.no_nbt_data",
                CommandConfig.Language.EN_US, "  No NBT data found",
                CommandConfig.Language.ZH_CN, "  未找到NBT数据");
        
        register("network.info.nbt_large_warning",
                CommandConfig.Language.EN_US, "  Warning: Large NBT data detected (>1GB)!",
                CommandConfig.Language.ZH_CN, "  警告: 检测到大量NBT数据（>1GB）！");
        
        register("network.info.nbt_note",
                CommandConfig.Language.EN_US, "  Note: NBT analysis only includes items with NBT tags.",
                CommandConfig.Language.ZH_CN, "  注意: NBT分析只包括带有NBT标签的物品。");
        
        // NBT提示框文本
        register("network.info.nbt_hover_title",
                CommandConfig.Language.EN_US, "NBT Data Details:",
                CommandConfig.Language.ZH_CN, "NBT数据详情：");
        
        register("network.info.nbt_hover_unique_types",
                CommandConfig.Language.EN_US, "Unique item types with NBT: ",
                CommandConfig.Language.ZH_CN, "有NBT的物品种类：");
        
        register("network.info.nbt_hover_total_items",
                CommandConfig.Language.EN_US, "Total items with NBT: ",
                CommandConfig.Language.ZH_CN, "有NBT的物品总数：");
        
        register("network.info.nbt_hover_exact_size",
                CommandConfig.Language.EN_US, "Exact size: ",
                CommandConfig.Language.ZH_CN, "精确大小：");
        
        register("network.info.nbt_hover_formatted_size",
                CommandConfig.Language.EN_US, "Formatted size: ",
                CommandConfig.Language.ZH_CN, "格式化大小：");
        
        register("network.info.nbt_hover_note",
                CommandConfig.Language.EN_US, "\nNote: NBT calculation only includes items with NBT tags.",
                CommandConfig.Language.ZH_CN, "\n注意: NBT计算只包括带有NBT标签的物品。");

        register("display.empty_item",
                CommandConfig.Language.EN_US, "Empty",
                CommandConfig.Language.ZH_CN, "空");

        register("display.empty_fluid",
                CommandConfig.Language.EN_US, "Empty Fluid",
                CommandConfig.Language.ZH_CN, "空流体");

        register("network.info.no_permission",
                CommandConfig.Language.EN_US, "No Permission",
                CommandConfig.Language.ZH_CN, "无权限");

        // 自动合成命令相关文本
        register("network.craft.success",
                CommandConfig.Language.EN_US, "Successfully crafted %s x %s from network %s",
                CommandConfig.Language.ZH_CN, "成功从网络 %3$s 合成 %1$s x %2$s");
        
        register("network.craft.no_recipe",
                CommandConfig.Language.EN_US, "No crafting recipe found for: %s",
                CommandConfig.Language.ZH_CN, "找不到合成配方: %s");
        
        register("network.craft.insufficient_materials",
                CommandConfig.Language.EN_US, "Insufficient materials in network",
                CommandConfig.Language.ZH_CN, "网络材料不足");
        
        register("network.craft.insufficient_storage",
                CommandConfig.Language.EN_US, "Insufficient storage space for output",
                CommandConfig.Language.ZH_CN, "输出存储空间不足");
        
        register("network.craft.failed",
                CommandConfig.Language.EN_US, "Crafting failed: %s",
                CommandConfig.Language.ZH_CN, "合成失败: %s");
        
        register("network.craft.materials_required",
                CommandConfig.Language.EN_US, "Materials required for %s x %s:",
                CommandConfig.Language.ZH_CN, "合成 %1$s x %2$s 所需材料:");
        
        register("network.craft.material_entry",
                CommandConfig.Language.EN_US, "  %s x %s",
                CommandConfig.Language.ZH_CN, "  %2$s x %1$s");
        
        register("network.craft.material_available",
                CommandConfig.Language.EN_US, " (Available: %s)",
                CommandConfig.Language.ZH_CN, " (可用: %s)");
        
        register("network.craft.check.success",
                CommandConfig.Language.EN_US, "Can craft %s x %s: materials available",
                CommandConfig.Language.ZH_CN, "可以合成 %1$s x %2$s: 材料充足");
        
        register("network.craft.check.insufficient",
                CommandConfig.Language.EN_US, "Cannot craft %s x %s: insufficient materials",
                CommandConfig.Language.ZH_CN, "无法合成 %1$s x %2$s: 材料不足");
        
        // 合成树命令相关文本
        register("network.craft.tree.success",
                CommandConfig.Language.EN_US, "Crafting tree analysis for %s x %s:",
                CommandConfig.Language.ZH_CN, "合成树分析 %1$s x %2$s:");
        
        register("network.craft.tree.node",
                CommandConfig.Language.EN_US, "%s%s x %s - %s",
                CommandConfig.Language.ZH_CN, "%1$s%3$s x %2$s - %4$s");
        
        register("network.craft.tree.indent",
                CommandConfig.Language.EN_US, "  ",
                CommandConfig.Language.ZH_CN, "  ");
        
        register("network.craft.tree.total_materials",
                CommandConfig.Language.EN_US, "Total materials required:",
                CommandConfig.Language.ZH_CN, "总计所需材料:");
        
        register("network.craft.tree.execute.success",
                CommandConfig.Language.EN_US, "Successfully crafted tree for %s x %s from network %s",
                CommandConfig.Language.ZH_CN, "成功从网络 %3$s 执行合成树 %1$s x %2$s");
        
        register("network.craft.tree.execute.failed",
                CommandConfig.Language.EN_US, "Crafting tree execution failed: %s",
                CommandConfig.Language.ZH_CN, "合成树执行失败: %s");
        
        register("network.craft.tree.recursion_limit",
                CommandConfig.Language.EN_US, "Recursion depth limit reached (%s levels)",
                CommandConfig.Language.ZH_CN, "达到递归深度限制 (%s 层)");
        
        register("error.insufficient_storage",
                CommandConfig.Language.EN_US, "Insufficient storage space in network",
                CommandConfig.Language.ZH_CN, "网络存储空间不足");
        
        register("error.crafting_disabled",
                CommandConfig.Language.EN_US, "Crafting functionality is disabled in configuration",
                CommandConfig.Language.ZH_CN, "合成功能已在配置中禁用");

        register("error.craft_cooldown",
                CommandConfig.Language.EN_US, "Please wait before sending another craft request",
                CommandConfig.Language.ZH_CN, "请等待后再发送合成请求");
        
        register("error.amount_must_be_positive",
                CommandConfig.Language.EN_US, "Amount must be greater than 0",
                CommandConfig.Language.ZH_CN, "数量必须大于0");
        
        register("error.target_owner_not_found",
                CommandConfig.Language.EN_US, "Cannot find owner of target network",
                CommandConfig.Language.ZH_CN, "无法找到目标网络的所有者");
        
        register("error.extract_energy_failed",
                CommandConfig.Language.EN_US, "Failed to extract energy",
                CommandConfig.Language.ZH_CN, "提取能量失败");
        
        register("error.extract_fluid_failed",
                CommandConfig.Language.EN_US, "Failed to extract fluid",
                CommandConfig.Language.ZH_CN, "提取流体失败");
        
        register("error.extract_item_failed",
                CommandConfig.Language.EN_US, "Failed to extract item",
                CommandConfig.Language.ZH_CN, "提取物品失败");
        
        register("error.accept_request_failed",
                CommandConfig.Language.EN_US, "Failed to accept request",
                CommandConfig.Language.ZH_CN, "接受请求失败");
        
        register("error.deny_request_failed",
                CommandConfig.Language.EN_US, "Failed to deny request",
                CommandConfig.Language.ZH_CN, "拒绝请求失败");
        
        register("error.no_pending_request",
                CommandConfig.Language.EN_US, "No pending transfer request",
                CommandConfig.Language.ZH_CN, "没有待处理的传输请求");
        
        register("error.request_cancelled",
                CommandConfig.Language.EN_US, "Your transfer request has been cancelled",
                CommandConfig.Language.ZH_CN, "你的传输请求已被取消");
        
        register("error.request_denied",
                CommandConfig.Language.EN_US, "Your transfer request has been denied",
                CommandConfig.Language.ZH_CN, "你的传输请求已被拒绝");
        
        register("error.transfer_failed_network",
                CommandConfig.Language.EN_US, "Transfer failed: Network does not exist or has been deleted",
                CommandConfig.Language.ZH_CN, "传输失败：网络不存在或已删除");
        
        // 传输请求消息
        register("network.transfer.request.wants_to_transfer",
                CommandConfig.Language.EN_US, " wants to transfer ",
                CommandConfig.Language.ZH_CN, " 想要从网络 ");
        
        register("network.transfer.request.from_network",
                CommandConfig.Language.EN_US, " from network ",
                CommandConfig.Language.ZH_CN, " 传输 ");
        
        
        
        
        

        // 其他错误消息
        register("error.network_id_required",
                CommandConfig.Language.EN_US, "Network ID parameter cannot be empty",
                CommandConfig.Language.ZH_CN, "网络ID参数不能为空");
        
        register("error.player_required_for_command",
                CommandConfig.Language.EN_US, "This command must be executed by a player",
                CommandConfig.Language.ZH_CN, "此命令必须由玩家执行");

        register("error.unsupported_player_selector",
                CommandConfig.Language.EN_US, "Unsupported player selector: %s",
                CommandConfig.Language.ZH_CN, "不支持的玩家选择器: %s");

        register("error.cannot_parse_player_selector",
                CommandConfig.Language.EN_US, "Cannot parse player selector: %s",
                CommandConfig.Language.ZH_CN, "无法解析玩家选择器: %s");

        register("error.invalid_network_id_or_player",
                CommandConfig.Language.EN_US, "Invalid network ID or player name: %s",
                CommandConfig.Language.ZH_CN, "无效的网络ID或玩家名: %s");

        register("error.player_not_in_any_network",
                CommandConfig.Language.EN_US, "Player %s is not in any network",
                CommandConfig.Language.ZH_CN, "玩家 %s 不在任何网络中");
        
        
        
        // 资源显示
        register("display.fluid_total",
                CommandConfig.Language.EN_US, "Fluid total: %s mB",
                CommandConfig.Language.ZH_CN, "流体总量: %s mB");
        
        register("display.energy_total",
                CommandConfig.Language.EN_US, "Energy total: %s FE",
                CommandConfig.Language.ZH_CN, "能量总量: %s FE");
        
        register("display.crystal_generation_disabled",
                CommandConfig.Language.EN_US, "Crystal generation is disabled",
                CommandConfig.Language.ZH_CN, "结晶生成功能已禁用");
        
        register("display.disabled",
                CommandConfig.Language.EN_US, "Disabled",
                CommandConfig.Language.ZH_CN, "已禁用");
        
        // 功能未实现
        register("error.enchantment_book_not_implemented",
                CommandConfig.Language.EN_US, "Enchantment book giving functionality is not yet implemented",
                CommandConfig.Language.ZH_CN, "给予附魔书功能暂未实现");

        register("network.tools.giveEnchantedBooks.success",
                CommandConfig.Language.EN_US, "Gave %s %s enchanted books",
                CommandConfig.Language.ZH_CN, "已给予 %s %s 本附魔书");
        
        register("network.tools.giveEnchantedBooks.all_info",
                CommandConfig.Language.EN_US, "Creating enchanted book with all %s enchantments for %s...",
                CommandConfig.Language.ZH_CN, "正在为 %s 创建包含所有 %s 个附魔的附魔书...");
        
        // 插入命令错误
        register("error.item_required",
                CommandConfig.Language.EN_US, "Item argument is required",
                CommandConfig.Language.ZH_CN, "需要指定物品参数");

        register("error.tag_required",
                CommandConfig.Language.EN_US, "Item tag argument is required (use #namespace:path)",
                CommandConfig.Language.ZH_CN, "需要指定物品标签参数（使用 #命名空间:路径）");

        register("error.tag_empty",
                CommandConfig.Language.EN_US, "Tag %s contains no items",
                CommandConfig.Language.ZH_CN, "标签 %s 内没有任何物品");
        
        register("error.fluid_required",
                CommandConfig.Language.EN_US, "Fluid argument is required",
                CommandConfig.Language.ZH_CN, "需要指定流体参数");
        
        // 资源生成结果
        register("network.generate.result.title",
                CommandConfig.Language.EN_US, "Network %s resource generation result (%s):",
                CommandConfig.Language.ZH_CN, "网络 %s 资源生成结果 (%s):");
        
        register("network.generate.result.items",
                CommandConfig.Language.EN_US, "  Items: %s types, %s total",
                CommandConfig.Language.ZH_CN, "  物品: %s 种, 总量: %s");
        
        register("network.generate.result.fluids",
                CommandConfig.Language.EN_US, "  Fluids: %s types, %s mB total",
                CommandConfig.Language.ZH_CN, "  流体: %s 种, 总量: %s mB");
        
        register("network.generate.result.energy",
                CommandConfig.Language.EN_US, "  Energy: %s types, %s FE total",
                CommandConfig.Language.ZH_CN, "  能量: %s 种, 总量: %s FE");

        register("network.generate.result.enchantments",
                CommandConfig.Language.EN_US, "  Enchantments: %s items with %s enchantments total",
                CommandConfig.Language.ZH_CN, "  附魔: %s 个物品带有 %s 个附魔");

        register("network.generate.result.nbt_size",
                CommandConfig.Language.EN_US, "  NBT Data: %s items with NBT, estimated size: %s",
                CommandConfig.Language.ZH_CN, "  NBT数据: %s 个物品带有NBT, 估算大小: %s");

        register("network.generate.result.nbt_warning",
                CommandConfig.Language.EN_US, "  Warning: Complex NBT may affect performance and storage",
                CommandConfig.Language.ZH_CN, "  警告: 复杂NBT可能影响性能和存储");
        
        // 分页导航
        register("pagination.click_to_page",
                CommandConfig.Language.EN_US, "Click to go to page %s",
                CommandConfig.Language.ZH_CN, "点击前往第 %s 页");
        
        // 物品和资源类型显示
        register("display.total_items",
                CommandConfig.Language.EN_US, "Total items: %s",
                CommandConfig.Language.ZH_CN, "物品总数: %s");
        
        register("display.resource_type_count",
                CommandConfig.Language.EN_US, "%s type count: %s",
                CommandConfig.Language.ZH_CN, "%s类型数量: %s");
        
        // 结晶时间显示
        register("display.crystal_remaining_time",
                CommandConfig.Language.EN_US, "Crystal Remaining Time: %s",
                CommandConfig.Language.ZH_CN, "结晶剩余时间: %s");
        
        register("display.crystal_time_tooltip",
                CommandConfig.Language.EN_US, "Time until next crystal generation",
                CommandConfig.Language.ZH_CN, "距离下一次结晶生成的时间");
        
        // 槽位信息
        register("display.slot_capacity",
                CommandConfig.Language.EN_US, "Slot Capacity: %s",
                CommandConfig.Language.ZH_CN, "槽位容量: %s");
        
        register("display.slot_count",
                CommandConfig.Language.EN_US, "Slot Count: %s",
                CommandConfig.Language.ZH_CN, "槽位数量: %s");
        
        // 时间格式化
        register("time.format.days_hours_minutes_seconds",
                CommandConfig.Language.EN_US, "%sd %sh %sm %ss",
                CommandConfig.Language.ZH_CN, "%s天%s小时%s分钟%s秒");
        
        register("time.format.hours_minutes_seconds",
                CommandConfig.Language.EN_US, "%sh %sm %ss",
                CommandConfig.Language.ZH_CN, "%s小时%s分钟%s秒");
        
        register("time.format.minutes_seconds",
                CommandConfig.Language.EN_US, "%sm %ss",
                CommandConfig.Language.ZH_CN, "%s分钟%s秒");
        
        register("time.format.seconds",
                CommandConfig.Language.EN_US, "%ss",
                CommandConfig.Language.ZH_CN, "%s秒");
        
        register("time.format.less_than_second",
                CommandConfig.Language.EN_US, "<1s",
                CommandConfig.Language.ZH_CN, "<1秒");
        
        // 插入操作成功消息
        register("network.insert.item.success",
                CommandConfig.Language.EN_US, "Inserted %s of %s into network %s",
                CommandConfig.Language.ZH_CN, "已向网络 %3$s 插入 %1$s 个 %2$s");

        register("network.workstation.invalid_id",
                CommandConfig.Language.EN_US, "Invalid workstation id: %s (valid: anvil, cut, grind, smith, enchant)",
                CommandConfig.Language.ZH_CN, "无效的工作台 ID：%s（可用：anvil、cut、grind、smith、enchant）");

        register("network.workstation.reset.one",
                CommandConfig.Language.EN_US, "Reset workstation activation for %2$s on network %1$s",
                CommandConfig.Language.ZH_CN, "已重置网络 %1$s 的 %2$s 工作台激活状态");

        register("network.workstation.reset.all",
                CommandConfig.Language.EN_US, "Reset all workstation activations on network %1$s (%2$s: %3$s)",
                CommandConfig.Language.ZH_CN, "已重置网络 %1$s 的全部工作台激活状态（%2$s 个：%3$s）");

        register("network.workstation.reset.none",
                CommandConfig.Language.EN_US, "Workstation %2$s is not activated on network %1$s",
                CommandConfig.Language.ZH_CN, "网络 %1$s 的 %2$s 工作台当前未激活");

        register("network.workstation.reset.all_none",
                CommandConfig.Language.EN_US, "No workstations are activated on network %s",
                CommandConfig.Language.ZH_CN, "网络 %s 没有已激活的工作台");

        register("network.info.workstation",
                CommandConfig.Language.EN_US, "Activated workstations: %s",
                CommandConfig.Language.ZH_CN, "已激活工作台：%s");

        register("network.info.workstation_none",
                CommandConfig.Language.EN_US, "Activated workstations: none",
                CommandConfig.Language.ZH_CN, "已激活工作台：无");

        register("network.insert.tag.title",
                CommandConfig.Language.EN_US, "======== Insert Tag: network %1$s, tag %2$s ========",
                CommandConfig.Language.ZH_CN, "========= 标签插入：网络 %1$s，标签 %2$s =========");

        register("network.insert.tag.line.skipped",
                CommandConfig.Language.EN_US, "Skipped %s (insufficient storage)",
                CommandConfig.Language.ZH_CN, "跳过 %s（空间不足）");

        register("network.insert.tag.summary",
                CommandConfig.Language.EN_US, "Inserted %1$s/%2$s types x %3$s each, total %4$s items (%5$s types skipped)",
                CommandConfig.Language.ZH_CN, "成功插入 %1$s/%2$s 种物品，每种 %3$s 个，共 %4$s 个（跳过 %5$s 种）");
        
        register("network.insert.fluid.success",
                CommandConfig.Language.EN_US, "Inserted %s mB of %s into network %s",
                CommandConfig.Language.ZH_CN, "已向网络 %3$s 插入 %1$s mB 的 %2$s");
        
        register("network.insert.energy.success",
                CommandConfig.Language.EN_US, "Inserted %s FE of energy into network %s",
                CommandConfig.Language.ZH_CN, "已向网络 %2$s 插入 %1$s FE 能量");
        
        // 网络创建消息
        register("network.create.success",
                CommandConfig.Language.EN_US, "Created network #%s (ID: %s) for player %s",
                CommandConfig.Language.ZH_CN, "为玩家 %3$s 创建网络 #%1$s (ID: %2$s)");
        
        register("network.create.warning.failed",
                CommandConfig.Language.EN_US, "Warning: Failed to create network #%s for player %s",
                CommandConfig.Language.ZH_CN, "警告: 无法为玩家 %2$s 创建网络 #%1$s");
        
        register("network.create.warning.failed_with_error",
                CommandConfig.Language.EN_US, "Warning: Failed to create network #%s for player %s: %s",
                CommandConfig.Language.ZH_CN, "警告: 无法为玩家 %2$s 创建网络 #%1$s: %3$s");
        
        register("error.op_required",
                CommandConfig.Language.EN_US, "You must be an operator to use this command",
                CommandConfig.Language.ZH_CN, "你需要OP权限才能使用此命令");
        
        register("error.players_required",
                CommandConfig.Language.EN_US, "At least one player must be specified",
                CommandConfig.Language.ZH_CN, "至少需要指定一个玩家");
        
        register("error.invalid_network_id",
                CommandConfig.Language.EN_US, "Invalid network ID: %s",
                CommandConfig.Language.ZH_CN, "无效的网络ID: %s");
        
        register("error.insert_failed",
                CommandConfig.Language.EN_US, "Insertion failed, %s remaining",
                CommandConfig.Language.ZH_CN, "插入失败，剩余 %s 个");

        // 传输按钮文本
        register("button.accept",
                CommandConfig.Language.EN_US, "Accept",
                CommandConfig.Language.ZH_CN, "接受");

        register("button.deny",
                CommandConfig.Language.EN_US, "Deny",
                CommandConfig.Language.ZH_CN, "拒绝");

        register("button.cancel",
                CommandConfig.Language.EN_US, "Cancel",
                CommandConfig.Language.ZH_CN, "取消");

        register("button.hover.accept",
                CommandConfig.Language.EN_US, "Click to accept",
                CommandConfig.Language.ZH_CN, "点击接受");

        register("button.hover.deny",
                CommandConfig.Language.EN_US, "Click to deny",
                CommandConfig.Language.ZH_CN, "点击拒绝");

        register("button.hover.cancel",
                CommandConfig.Language.EN_US, "Click to cancel",
                CommandConfig.Language.ZH_CN, "点击取消");

        register("error.feature_removed",
                CommandConfig.Language.EN_US, "Feature has been removed: %s",
                CommandConfig.Language.ZH_CN, "功能已移除: %s");

        // 网络名称显示
        register("network.info.name_label",
                CommandConfig.Language.EN_US, "Network Name: ",
                CommandConfig.Language.ZH_CN, "网络名称: ");

        register("network.list.name",
                CommandConfig.Language.EN_US, "Name",
                CommandConfig.Language.ZH_CN, "名称");

        register("network.list.name.none",
                CommandConfig.Language.EN_US, "(Unnamed)",
                CommandConfig.Language.ZH_CN, "(未命名)");

        register("network.transfer.insufficient_storage",
                CommandConfig.Language.EN_US, "Insufficient storage space in network",
                CommandConfig.Language.ZH_CN, "网络存储空间不足");

        // 命令错误消息（跟随 command_language 配置）
        register("error.no_primary_network",
                CommandConfig.Language.EN_US, "You don't have a primary network",
                CommandConfig.Language.ZH_CN, "你没有主网络");

        register("error.execute_failed",
                CommandConfig.Language.EN_US, "Execution failed: %s",
                CommandConfig.Language.ZH_CN, "执行失败: %s");

        register("error.cannot_remove_owner",
                CommandConfig.Language.EN_US, "Cannot remove network owner",
                CommandConfig.Language.ZH_CN, "不能移除网络所有者");

    }

    /** 注册一条键对应的中英文翻译文本 */
    private static void register(String key, CommandConfig.Language lang1, String text1, CommandConfig.Language lang2, String text2)
    {
        TRANSLATIONS.computeIfAbsent(key, k -> new HashMap<>()).put(lang1, text1);
        TRANSLATIONS.computeIfAbsent(key, k -> new HashMap<>()).put(lang2, text2);
    }

    /** 按当前语言获取文本，未找到时返回键本身，语言缺失时回退英文 */
    public static String get(String key)
    {
        CommandConfig.Language lang = CommandConfig.getCommandLanguage();
        Map<CommandConfig.Language, String> langMap = TRANSLATIONS.get(key);
        if (langMap == null)
        {
            return key;
        }
        String text = langMap.get(lang);
        return text != null ? text : langMap.get(CommandConfig.Language.EN_US);
    }

    /** 获取格式化后的文本（占位符以可变参数填充） */
    public static String get(String key, Object... args)
    {
        return String.format(get(key), args);
    }

    /** 获取无参文本组件 */
    public static Component component(String key)
    {
        return Component.literal(get(key));
    }

    /** 获取格式化后的文本组件 */
    public static Component component(String key, Object... args)
    {
        return Component.literal(get(key, args));
    }
}