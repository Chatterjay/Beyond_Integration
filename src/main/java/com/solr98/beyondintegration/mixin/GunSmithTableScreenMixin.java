package com.solr98.beyondintegration.mixin;
import com.solr98.beyondintegration.client.GunSmithNetMode;
import com.solr98.beyondintegration.client.NetworkItemCache;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.RequestNetworkItemsPacket;
import com.solr98.beyondintegration.network.TaczCraftPacket;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.client.gui.components.smith.ImageButton;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 注入目标：TacZ 的 {@code GunSmithTableScreen}（枪械工作台界面，客户端）。
 * 目的：增加"使用网络材料/产物输出到网络"两个开关按钮及其偏好持久化；
 * 将网络中的材料数量合并到配方材料计数显示，并让合成按钮改走服务端网络合成逻辑。
 * 模式状态与 taczaddon 兼容 Mixin 共用 {@link GunSmithNetMode}。
 */
@Mixin(targets = "com.tacz.guns.client.gui.GunSmithTableScreen", remap = false)
public abstract class GunSmithTableScreenMixin extends AbstractContainerScreen<GunSmithTableMenu> {

    protected GunSmithTableScreenMixin(GunSmithTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    // 网络材料计数缓存（按版本号失效）
    @Unique private int beyond$netVersion = -1;
    @Unique private int[] beyond$cachedNetworkCounts;
    @Unique private String beyond$lastRecipeKey;
    @Unique private Button beyond$modeBtn;
    @Unique private Button beyond$outputBtn;

    @Shadow(remap = false) private @Nullable Int2IntArrayMap playerIngredientCount;
    @Shadow(remap = false) private @Nullable RecipeHolder<GunSmithTableRecipe> selectedRecipe;
    @Shadow(remap = false) private void getPlayerIngredientCount(RecipeHolder<GunSmithTableRecipe> holder) {}

    @Unique private static final ResourceLocation beyond$tex = ResourceLocation.parse("tacz:textures/gui/gun_smith_table.png");
    @Unique private static ItemStack beyond$netIcon = ItemStack.EMPTY;
    @Unique private static boolean beyond$loaded = false;

    // 从客户端配置加载两个开关的偏好设置（CLIENT 类型，config 目录持久化）
    @Unique
    private void beyond$loadPrefs() {
        try {
            GunSmithNetMode.setNetworkMode(com.solr98.beyondintegration.ClientConfig.taczSmithUseNetwork());
            GunSmithNetMode.setOutputToNetwork(com.solr98.beyondintegration.ClientConfig.taczSmithOutputToNetwork());
        } catch (Exception ignored) {}
    }

    // 保存两个开关的偏好设置到客户端配置
    @Unique
    private void beyond$savePrefs() {
        try {
            com.solr98.beyondintegration.ClientConfig.setTaczSmithUseNetwork(GunSmithNetMode.isNetworkMode());
            com.solr98.beyondintegration.ClientConfig.setTaczSmithOutputToNetwork(GunSmithNetMode.isOutputToNetwork());
        } catch (Exception ignored) {}
    }

    // 初始化网络图标（维度网络创造器物品），仅一次
    @Unique
    private static void beyond$initIcons() {
        if (!beyond$netIcon.isEmpty()) return;
        try {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("beyonddimensions:net_creater"));
            if (item != null) beyond$netIcon = new ItemStack(item);
        } catch (Exception ignored) {}
    }

    // 绘制 18x18 的开关按钮（普通/悬停两种贴图 + 图标物品）
    @Unique
    private void beyond$drawButton(GuiGraphics g, int x, int y, int mx, int my, ItemStack icon) {
        boolean hover = mx >= x && mx < x + 18 && my >= y && my < y + 18;
        int v = hover ? 182 : 164;
        g.blit(beyond$tex, x, y, 18, 18, 138, v, 48, 18, 256, 256);
        if (!icon.isEmpty()) g.renderFakeItem(icon, x + 1, y + 1);
    }

    @Unique
    private void beyond$refreshCounts() {
        if (selectedRecipe != null) getPlayerIngredientCount(selectedRecipe);
    }

    // taczaddon 存在时清空其"附近容器"虚拟来源（网络模式与外部容器模式互斥）
    @Unique
    private void beyond$clearAddonExternalSources() {
        if (!ModList.get().isLoaded("taczaddon")) return;
        try {
            Object self = this;
            if (self instanceof com.mafuyu404.taczaddon.init.VirtualContainerLoader loader) {
                loader.taczaddon$setVirtualContainer(new ArrayList<>());
            }
        } catch (Throwable ignored) {}
    }

    // 在界面渲染末尾追加绘制网络名称与两个开关按钮
    @Inject(method = "render", at = @At("TAIL"), remap = true)
    private void beyond$onRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!beyond$loaded) {
            beyond$loaded = true;
            beyond$loadPrefs();
            beyond$syncTooltips();
        }
        beyond$initIcons();

        var self = (GunSmithTableScreen) (Object) this;
        var font = Minecraft.getInstance().font;
        int left = self.getGuiLeft(), top = self.getGuiTop();

        Component netText = NetworkItemCache.getDisplayName();
        graphics.drawString(font, netText, left + 254, top + 33,
                NetworkItemCache.hasNetwork() ? 0x55FFFF : 0x888888, false);

        if (NetworkItemCache.hasNetwork()) {
            var modeIcon = GunSmithNetMode.isNetworkMode()
                    ? new ItemStack(net.minecraft.world.item.Items.ENDER_EYE)
                    : new ItemStack(net.minecraft.world.item.Items.CRAFTING_TABLE);
            beyond$drawButton(graphics, left + 322, top + 50, mouseX, mouseY, modeIcon);
        }
        if (NetworkItemCache.hasNetwork() && GunSmithNetMode.isNetworkMode()) {
            var outIcon = GunSmithNetMode.isOutputToNetwork() ? beyond$netIcon : new ItemStack(net.minecraft.world.item.Items.CHEST);
            beyond$drawButton(graphics, left + 267, top + 162, mouseX, mouseY, outIcon);
        }
    }

    // 在配方材料槽旁追加显示各材料对应的网络数量
    @Inject(method = "renderIngredient", at = @At("RETURN"))
    private void beyond$onRenderIngredient(GuiGraphics graphics, CallbackInfo ci) {
        if (!GunSmithNetMode.isNetworkMode()) return;
        if (!NetworkItemCache.hasNetwork()) return;
        if (selectedRecipe == null) return;
        var inputs = selectedRecipe.value().getInputs();
        if (inputs == null || inputs.isEmpty()) return;

        int netVer = NetworkItemCache.getVersion();
        if (beyond$cachedNetworkCounts == null || beyond$netVersion != netVer) {
            if (NetworkItemCache.isEmpty()) return;
            beyond$netVersion = netVer;
            beyond$cachedNetworkCounts = GunSmithNetMode.calcNetworkCounts(selectedRecipe.id(), selectedRecipe.value());
            beyond$lastRecipeKey = selectedRecipe.id().toString();
        }

        var font = Minecraft.getInstance().font;
        var screen = (GunSmithTableScreen) (Object) this;
        int idx = 0;
        for (int i = 0; i < 6 && idx < inputs.size(); i++) {
            for (int j = 0; j < 2 && idx < inputs.size(); j++) {
                if (idx >= beyond$cachedNetworkCounts.length) break;
                int netCount = beyond$cachedNetworkCounts[idx];
                if (netCount > 0) {
                    int offsetX = screen.getGuiLeft() + 254 + 45 * j;
                    int offsetY = screen.getGuiTop() + 62 + 17 * i;
                    var pose = graphics.pose();
                    pose.pushPose();
                    pose.translate(0, 0, 250);
                    pose.scale(0.5f, 0.5f, 1);
                    graphics.drawString(font, "+" + netCount, (offsetX + 17) * 2, (offsetY + 5) * 2, 0x55FFFF, false);
                    pose.popPose();
                }
                idx++;
            }
        }
    }

    // 在原材料计数中叠加网络材料数量，使"可合成次数"判定包含网络库存
    @Inject(method = "getPlayerIngredientCount", at = @At("RETURN"))
    private void beyond$addNetworkCounts(RecipeHolder<GunSmithTableRecipe> holder, CallbackInfo ci) {
        if (!GunSmithNetMode.isNetworkMode()) return;
        if (holder == null || playerIngredientCount == null) return;

        PacketHandler.sendToServer(new RequestNetworkItemsPacket());

        // taczaddon 可能用"背包+外部容器"的整体替换结果，网络模式下以真实背包为基线重算
        if (ModList.get().isLoaded("taczaddon")) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                var realInv = player.getInventory();
                var inputs = holder.value().getInputs();
                if (inputs != null) {
                    int size = Math.min(inputs.size(), playerIngredientCount.size());
                    for (int i = 0; i < size; i++) {
                        var ing = inputs.get(i).getIngredient();
                        int realCount = 0;
                        for (var stack : realInv.items) {
                            if (!stack.isEmpty() && ing.test(stack)) realCount += stack.getCount();
                        }
                        playerIngredientCount.put(i, realCount);
                    }
                }
            }
        }

        if (!NetworkItemCache.hasNetwork() || NetworkItemCache.isEmpty()) return;

        var recipe = holder.value();
        int netVer = NetworkItemCache.getVersion();
        String recipeKey = holder.id().toString();
        boolean cacheStale = !recipeKey.equals(beyond$lastRecipeKey) || beyond$netVersion != netVer;

        if (cacheStale) {
            beyond$netVersion = netVer;
            beyond$lastRecipeKey = recipeKey;
            beyond$cachedNetworkCounts = GunSmithNetMode.calcNetworkCounts(holder.id(), recipe);
        }

        int max = Math.min(beyond$cachedNetworkCounts.length, playerIngredientCount.size());
        for (int i = 0; i < max; i++) {
            int net = beyond$cachedNetworkCounts[i];
            if (net > 0) {
                long before = playerIngredientCount.get(i);
                long after = Math.min(before + net, Integer.MAX_VALUE);
                playerIngredientCount.put(i, (int) after);
            }
        }
    }

    // 包装合成按钮的点击回调：开启网络模式时改发网络合成数据包，否则走原逻辑
    @ModifyArg(method = "addCraftButton",
               at = @At(value = "INVOKE",
                        target = "Lcom/tacz/guns/client/gui/components/smith/ImageButton;<init>(IIIIIIILnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/gui/components/Button$OnPress;)V"),
               index = 8,
               remap = false)
    private Button.OnPress beyond$wrapOnPress(Button.OnPress original) {
        return b -> {
            if (!GunSmithNetMode.isNetworkMode()) { original.onPress(b); return; }
            if (selectedRecipe == null || playerIngredientCount == null) { original.onPress(b); return; }
            if (NetworkItemCache.hasNetwork()) {
                int count = Screen.hasShiftDown() ? 64 : 1;
                PacketHandler.sendToServer(new TaczCraftPacket(selectedRecipe.id(), count, GunSmithNetMode.isOutputToNetwork()));
            } else {
                original.onPress(b);
            }
        };
    }

    // 创建"使用网络材料"与"输出到网络"两个开关按钮
    @Inject(method = "addCraftButton", at = @At("TAIL"))
    private void beyond$onAddCraftButton(CallbackInfo ci) {
        var self = (GunSmithTableScreen) (Object) this;
        int left = self.getGuiLeft(), top = self.getGuiTop();

        var modeBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            GunSmithNetMode.setNetworkMode(!GunSmithNetMode.isNetworkMode());
            if (GunSmithNetMode.isNetworkMode()) {
                // 切入网络模式：清空 addon 外部容器来源（模式互斥）
                beyond$clearAddonExternalSources();
            }
            if (beyond$outputBtn != null) beyond$outputBtn.visible = GunSmithNetMode.isNetworkMode() && NetworkItemCache.hasNetwork();
            if (selectedRecipe != null) getPlayerIngredientCount(selectedRecipe);
            b.setTooltip(Tooltip.create(Component.translatable(GunSmithNetMode.isNetworkMode()
                    ? "gui.beyond_integration.mode.network"
                    : "gui.beyond_integration.mode.vanilla")));
        }).bounds(left + 322, top + 50, 18, 18).build());
        beyond$modeBtn = modeBtn;
        beyond$modeBtn.setTooltip(Tooltip.create(Component.translatable(GunSmithNetMode.isNetworkMode()
                ? "gui.beyond_integration.mode.network"
                : "gui.beyond_integration.mode.vanilla")));

        beyond$outputBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            GunSmithNetMode.setOutputToNetwork(!GunSmithNetMode.isOutputToNetwork());
            b.setTooltip(Tooltip.create(Component.translatable(GunSmithNetMode.isOutputToNetwork()
                    ? "gui.beyond_integration.output.network"
                    : "gui.beyond_integration.output.inventory",
                    NetworkItemCache.getDisplayName())));
        }).bounds(left + 267, top + 162, 18, 18).build());
        beyond$outputBtn.visible = NetworkItemCache.hasNetwork() && GunSmithNetMode.isNetworkMode();
    }

    // 按当前模式同步两个开关按钮的提示框文本（首次加载偏好后调用，修正初始硬编码）
    @Unique
    private void beyond$syncTooltips() {
        if (beyond$modeBtn != null) {
            beyond$modeBtn.setTooltip(Tooltip.create(Component.translatable(GunSmithNetMode.isNetworkMode()
                    ? "gui.beyond_integration.mode.network"
                    : "gui.beyond_integration.mode.vanilla")));
        }
        if (beyond$outputBtn != null) {
            beyond$outputBtn.setTooltip(Tooltip.create(Component.translatable(GunSmithNetMode.isOutputToNetwork()
                    ? "gui.beyond_integration.output.network"
                    : "gui.beyond_integration.output.inventory",
                    NetworkItemCache.getDisplayName())));
        }
    }

    // 关闭界面时保存开关偏好设置
    @Inject(method = "onClose", at = @At("HEAD"), remap = true)
    private void beyond$onClose(CallbackInfo ci) {
        beyond$savePrefs();
    }
}
