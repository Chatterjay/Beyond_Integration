package com.solr98.beyondintegration.client.gui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;

// 实体在 GUI 内渲染（照抄原版 InventoryScreen.renderEntityInInventory 逻辑）
/**
 * 工作站在界面内渲染实体/模型的工具类。
 * 封装原版物品栏实体渲染逻辑与附魔台书本 3D 模型渲染逻辑，
 * 支持固定视角与跟随鼠标视角两种实体渲染方式。
 */
public final class WorkstationRenderHelper {
    /** 在 GUI 指定位置渲染实体（固定旋转） */
    public static void renderEntityInInventory(GuiGraphics g, int x, int y, int scale, Quaternionf quat, Quaternionf camQuat, LivingEntity entity) {
        g.pose().pushPose();
        g.pose().translate(x, y, 50.0D);
        g.pose().scale(scale, scale, -scale);
        g.pose().mulPose(quat);
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        if (camQuat != null) {
            camQuat.conjugate();
            dispatcher.overrideCameraOrientation(camQuat);
        }
        dispatcher.setRenderShadow(false);
        RenderSystem.runAsFancy(() -> {
            dispatcher.render(entity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, g.pose(), g.bufferSource(), LightTexture.FULL_BRIGHT);
        });
        g.flush();
        dispatcher.setRenderShadow(true);
        g.pose().popPose();
    }

    // 鼠标跟随：偏移 → 角度 → 渲染（照抄原版 renderEntityInInventoryFollowsMouse / FollowsAngle）
    /** 根据鼠标偏移计算旋转角并渲染实体（跟随鼠标视角） */
    public static void renderEntityInInventoryFollowsMouse(GuiGraphics g, int x, int y, int scale, float mouseX, float mouseY, LivingEntity entity) {
        float f = (float) Math.atan(mouseX / 40.0F);
        float f1 = (float) Math.atan(mouseY / 40.0F);
        renderEntityInInventoryFollowsAngle(g, x, y, scale, f, f1, entity);
    }

    /** 按给定角度旋转实体并渲染（临时修改实体姿态，渲染后恢复） */
    public static void renderEntityInInventoryFollowsAngle(GuiGraphics g, int x, int y, int scale, float angleX, float angleY, LivingEntity entity) {
        Quaternionf quat = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf quat1 = new Quaternionf().rotateX(angleY * 20.0F * ((float) Math.PI / 180F));
        quat.mul(quat1);
        float bodyYaw = entity.yBodyRot;
        float headYaw = entity.getYRot();
        float pitch = entity.getXRot();
        float headRotO = entity.yHeadRotO;
        float headRot = entity.yHeadRot;
        entity.yBodyRot = 180.0F + angleX * 20.0F;
        entity.setYRot(180.0F + angleX * 40.0F);
        entity.setXRot(-angleY * 20.0F);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();
        renderEntityInInventory(g, x, y, scale, quat, quat1, entity);
        entity.yBodyRot = bodyYaw;
        entity.setYRot(headYaw);
        entity.setXRot(pitch);
        entity.yHeadRotO = headRotO;
        entity.yHeadRot = headRot;
    }

    private WorkstationRenderHelper() {}

    // ── 附魔台书本 3D 模型渲染（copy 原版 EnchantmentScreen.renderBook 逻辑封装）──
    /**
     * 在 GUI 中渲染附魔台 3D 书本模型（开合/翻页动画，姿态由调用方维护的动画字段驱动）。
     * @param g            渲染上下文
     * @param bookModel    已烘焙的 BookModel（ModelLayers.BOOK）
     * @param texture      书本贴图（默认 minecraft:textures/entity/enchanting_table_book.png）
     * @param anchorX      书模型锚点 X（屏幕坐标，对齐贴图上的书台位置）
     * @param anchorY      书模型锚点 Y（屏幕坐标）
     * @param partialTick  渲染帧部分时间
     * @param oOpen/open   开合动画：上一帧/当前开合度（0..1）
     * @param oFlip/flip   翻页动画：上一帧/当前翻页值
     */
    public static void renderEnchantingBook(GuiGraphics g, BookModel bookModel, ResourceLocation texture,
                                            int anchorX, int anchorY, float partialTick,
                                            float oOpen, float open, float oFlip, float flip) {
        float f = Mth.lerp(partialTick, oOpen, open);   // 开合插值
        float f1 = Mth.lerp(partialTick, oFlip, flip);  // 翻页插值
        Lighting.setupForEntityInInventory();
        g.pose().pushPose();
        g.pose().translate((float) anchorX, (float) anchorY, 100.0F);
        g.pose().scale(-40.0F, 40.0F, 40.0F);
        g.pose().mulPose(Axis.XP.rotationDegrees(25.0F));
        g.pose().translate((1.0F - f) * 0.2F, (1.0F - f) * 0.1F, (1.0F - f) * 0.25F);
        float f3 = -(1.0F - f) * 90.0F - 90.0F;
        g.pose().mulPose(Axis.YP.rotationDegrees(f3));
        g.pose().mulPose(Axis.XP.rotationDegrees(180.0F));
        float f4 = Mth.clamp(Mth.frac(f1 + 0.25F) * 1.6F - 0.3F, 0.0F, 1.0F);
        float f5 = Mth.clamp(Mth.frac(f1 + 0.75F) * 1.6F - 0.3F, 0.0F, 1.0F);
        bookModel.setupAnim(0.0F, f4, f5, f);
        VertexConsumer vertexconsumer = g.bufferSource().getBuffer(bookModel.renderType(texture));
        bookModel.renderToBuffer(g.pose(), vertexconsumer, 15728880, OverlayTexture.NO_OVERLAY);
        g.flush();
        g.pose().popPose();
        Lighting.setupFor3DItems();
    }
}
