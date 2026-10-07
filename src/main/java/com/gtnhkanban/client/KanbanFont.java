package com.gtnhkanban.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

/**
 * Minecraft's font at half size, for the Kanban screens. String widths are reported at half size, so layouts measure
 * what is drawn. {@code getCharWidth} stays full size: the font's own measuring and trimming add it up per character,
 * and halving it there as well would quarter every width.
 *
 * <p>
 * Text is drawn 2px lower than requested, so it sits centred where the 8px font's text sat and existing layouts stay
 * aligned. At GUI scales 2 and 4 each font pixel lands on whole screen pixels. It reads the same {@code ascii.png}
 * (resource packs apply) and follows the vanilla font's Unicode and bidi settings.
 */
final class KanbanFont extends FontRenderer {

    static final float SCALE = 0.5f;
    /** (8px glyphs - 4px glyphs) / 2: keeps text centred where full-size text was laid out. */
    private static final int CENTRE_OFFSET = 2;

    private static KanbanFont instance;

    private KanbanFont(Minecraft mc) {
        super(mc.gameSettings, new ResourceLocation("textures/font/ascii.png"), mc.getTextureManager(), false);
        FONT_HEIGHT = 5;
    }

    /** The shared instance, created once resources are available and kept in step with resource reloads. */
    static KanbanFont get(Minecraft mc) {
        if (instance == null) {
            instance = new KanbanFont(mc);
            instance.onResourceManagerReload(mc.getResourceManager());
            if (mc.getResourceManager() instanceof IReloadableResourceManager)
                ((IReloadableResourceManager) mc.getResourceManager()).registerReloadListener(instance);
        }
        instance.setUnicodeFlag(mc.fontRenderer.getUnicodeFlag());
        instance.setBidiFlag(mc.fontRenderer.getBidiFlag());
        return instance;
    }

    @Override
    public int drawString(String text, int x, int y, int color, boolean dropShadow) {
        if (text == null) return x;
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y + CENTRE_OFFSET, 0);
        GL11.glScalef(SCALE, SCALE, 1);
        int end = super.drawString(text, 0, 0, color, dropShadow);
        GL11.glPopMatrix();
        return x + (int) Math.ceil(end * SCALE);
    }

    @Override
    public int getStringWidth(String text) {
        return (int) Math.ceil(super.getStringWidth(text) * SCALE);
    }

    /** The two-argument form delegates here, so overriding it as well would double the width twice. */
    @Override
    public String trimStringToWidth(String text, int width, boolean reverse) {
        return super.trimStringToWidth(text, (int) (width / SCALE), reverse);
    }

    @Override
    public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
        return super.listFormattedStringToWidth(text, (int) (wrapWidth / SCALE));
    }
}
