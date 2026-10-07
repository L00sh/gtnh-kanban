package com.gtnhkanban.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * The Kanban window: a large beveled panel with folder tabs along its top edge. Pieces come from
 * {@code textures/gui/frame.png}, generated from {@code art/frame-mockup.png} by {@code art/build_frame_atlas.py}, and
 * are stretched so the window fits any screen while staying pixel-exact.
 */
final class KanbanFrame {

    static final ResourceLocation TEXTURE = new ResourceLocation("gtnhkanban", "textures/gui/frame.png");
    static final int TAB_HEIGHT = 21;
    static final int MARGIN = 8;
    static final int TABS_TOP = 4;
    static final int PANEL_TOP = TABS_TOP + TAB_HEIGHT;
    /** Distance from the panel edge to where content can start. */
    static final int INSET = 7;
    private static final int TAB_GAP = 8;
    private static final int FIRST_TAB_OFFSET = 24;
    private static final int RIGHT_TAB_OFFSET = 4;
    private static final int CORNER = 8;
    private static final RenderItem RENDER = new RenderItem();

    /** Unselected tab colors. Selected tabs are always teal. */
    enum Color {
        ORANGE,
        /** The Projects tab, so "back to the project list" stands apart from the board's own tabs. */
        PURPLE
    }

    /** Tab look; hover and pressed are placeholder art until the final colors are drawn. */
    enum State {

        SELECTED(0, 0x47D6C8, 0x022C27),
        NORMAL(16, 0xE1AA56, 0x2C1A02),
        HOVER(32, 0xFFC46A, 0x2C1A02),
        PRESSED(48, 0xE1AA56, 0x2C1A02),
        PURPLE_NORMAL(80, 0xA756E1, 0x1A012C),
        PURPLE_HOVER(96, 0xC06AFF, 0x1A012C),
        PURPLE_PRESSED(112, 0xA756E1, 0x1A012C);

        final int u;
        final int text;
        final int shadow;

        State(int u, int text, int shadow) {
            this.u = u;
            this.text = text;
            this.shadow = shadow;
        }
    }

    /** A clickable folder tab. A tab with {@code cog} shows the settings glyph instead of its label. */
    static final class Tab {

        final int id;
        final String label;
        final String tooltip;
        final ItemStack icon;
        final boolean cog;
        final boolean selected;
        Color color = Color.ORANGE;
        int x, width;

        Tab(int id, String label, String tooltip, ItemStack icon, boolean cog, boolean selected) {
            this.id = id;
            this.label = label;
            this.tooltip = tooltip;
            this.icon = icon;
            this.cog = cog;
            this.selected = selected;
        }

        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= TABS_TOP && mouseY < PANEL_TOP;
        }
    }

    /** Tabs on the left, in order, and on the right, right to left. */
    static final class Tabs {

        final List<Tab> left = new ArrayList<Tab>();
        final List<Tab> right = new ArrayList<Tab>();

        Tabs add(int id, String label, ItemStack icon, boolean selected) {
            left.add(new Tab(id, label, null, icon, false, selected));
            return this;
        }

        /** Adds a tab in {@code color} when not selected. */
        Tabs add(int id, String label, ItemStack icon, boolean selected, Color color) {
            add(id, label, icon, selected);
            left.get(left.size() - 1).color = color;
            return this;
        }

        Tabs addRight(int id, String tooltip, ItemStack icon, boolean cog) {
            right.add(new Tab(id, "", tooltip, icon, cog, false));
            return this;
        }

        void layout(FontRenderer font, int screenWidth) {
            int x = MARGIN + FIRST_TAB_OFFSET;
            for (Tab tab : left) {
                tab.width = Math.max(48, font.getStringWidth(tab.label) + 22 + (tab.icon == null ? 0 : 18));
                tab.x = x;
                x += tab.width + TAB_GAP;
            }
            int right = screenWidth - MARGIN - RIGHT_TAB_OFFSET;
            for (Tab tab : this.right) {
                tab.width = tab.cog ? 33 : 27;
                tab.x = right - tab.width;
                right = tab.x - TAB_GAP / 2;
            }
        }

        /** @return the tab under the mouse, or null */
        Tab at(int mouseX, int mouseY) {
            for (Tab tab : left) if (tab.contains(mouseX, mouseY)) return tab;
            for (Tab tab : right) if (tab.contains(mouseX, mouseY)) return tab;
            return null;
        }

        void draw(Minecraft mc, int mouseX, int mouseY) {
            for (Tab tab : left) drawTab(mc, tab, mouseX, mouseY);
            for (Tab tab : right) drawTab(mc, tab, mouseX, mouseY);
        }

        /** Tooltip for the tab under the mouse, or null. */
        String tooltip(int mouseX, int mouseY) {
            Tab tab = at(mouseX, mouseY);
            return tab == null ? null : tab.tooltip;
        }
    }

    private KanbanFrame() {}

    static int panelBottom(int screenHeight) {
        return screenHeight - MARGIN;
    }

    static int contentLeft() {
        return MARGIN + INSET;
    }

    static int contentRight(int screenWidth) {
        return screenWidth - MARGIN - INSET;
    }

    static int contentTop() {
        return PANEL_TOP + INSET;
    }

    static int contentBottom(int screenHeight) {
        return panelBottom(screenHeight) - INSET + 2;
    }

    /** Darkens the world behind, then draws the panel. Tabs are drawn separately, over its top edge. */
    static void drawWindow(Minecraft mc, int screenWidth, int screenHeight) {
        panel(mc, MARGIN, PANEL_TOP, screenWidth - 2 * MARGIN, panelBottom(screenHeight) - PANEL_TOP);
    }

    static void panel(Minecraft mc, int x, int y, int width, int height) {
        bind(mc);
        int innerW = Math.max(0, width - 2 * CORNER), innerH = Math.max(0, height - 2 * CORNER);
        quad(x, y, CORNER, CORNER, 0, 0, CORNER, CORNER);
        quad(x + width - CORNER, y, CORNER, CORNER, 9, 0, CORNER, CORNER);
        quad(x, y + height - CORNER, CORNER, CORNER, 0, 9, CORNER, CORNER);
        quad(x + width - CORNER, y + height - CORNER, CORNER, CORNER, 9, 9, CORNER, CORNER);
        quad(x + CORNER, y, innerW, CORNER, 8, 0, 1, CORNER);
        quad(x + CORNER, y + height - CORNER, innerW, CORNER, 8, 9, 1, CORNER);
        quad(x, y + CORNER, CORNER, innerH, 0, 8, CORNER, 1);
        quad(x + width - CORNER, y + CORNER, CORNER, innerH, 9, 8, CORNER, 1);
        quad(x + CORNER, y + CORNER, innerW, innerH, 8, 8, 1, 1);
    }

    static void drawTab(Minecraft mc, Tab tab, int mouseX, int mouseY) {
        boolean over = tab.contains(mouseX, mouseY);
        boolean purple = tab.color == Color.PURPLE;
        State state = tab.selected ? State.SELECTED
            : over && Mouse.isButtonDown(0) ? (purple ? State.PURPLE_PRESSED : State.PRESSED)
                : over ? (purple ? State.PURPLE_HOVER : State.HOVER) : purple ? State.PURPLE_NORMAL : State.NORMAL;
        bind(mc);
        int y = TABS_TOP;
        quad(tab.x, y, 6, TAB_HEIGHT, state.u, 32, 6, TAB_HEIGHT);
        quad(tab.x + 6, y, tab.width - 12, TAB_HEIGHT, state.u + 6, 32, 1, TAB_HEIGHT);
        quad(tab.x + tab.width - 6, y, 6, TAB_HEIGHT, state.u + 7, 32, 6, TAB_HEIGHT);
        if (tab.cog) {
            // 11px gear (plus shadow) centred in the tab's 16px-tall inner area.
            quad(tab.x + (tab.width - 11) / 2, y + 7, 12, 12, 64, 32, 12, 12);
            return;
        }
        int textX = tab.x + 11;
        if (tab.icon != null) {
            // The tab's border is 5px, leaving exactly 16px of inner height for the item.
            item(mc, tab.icon, tab.label.isEmpty() ? tab.x + (tab.width - 16) / 2 : tab.x + 7, y + 5);
            textX += 16;
        } else if (tab.label.isEmpty()) {
            label(mc.fontRenderer, "?", tab.x + (tab.width - mc.fontRenderer.getStringWidth("?")) / 2, y + 9, state);
        }
        if (!tab.label.isEmpty()) label(mc.fontRenderer, tab.label, textX, y + 9, state);
    }

    /** Text with the frame's colored drop shadow. */
    static void label(FontRenderer font, String text, int x, int y, State state) {
        font.drawString(text, x + 1, y + 1, state.shadow);
        font.drawString(text, x, y, state.text);
    }

    static void item(Minecraft mc, ItemStack stack, int x, int y) {
        RENDER.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x, y);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1, 1, 1, 1);
    }

    /** Restricts drawing to a screen rectangle (GUI coordinates) until {@link #endClip()}. */
    static void clip(Minecraft mc, int x, int y, int width, int height) {
        int scale = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
            x * scale,
            mc.displayHeight - (y + height) * scale,
            Math.max(0, width * scale),
            Math.max(0, height * scale));
    }

    static void endClip() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private static void bind(Minecraft mc) {
        mc.getTextureManager()
            .bindTexture(TEXTURE);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_LIGHTING);
    }

    /** Draws atlas region (u, v, uw, vh) stretched over (x, y, w, h). The atlas is 256x256. */
    private static void quad(int x, int y, int w, int h, int u, int v, int uw, int vh) {
        if (w <= 0 || h <= 0) return;
        float s = 1 / 256f;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + h, 0, u * s, (v + vh) * s);
        tessellator.addVertexWithUV(x + w, y + h, 0, (u + uw) * s, (v + vh) * s);
        tessellator.addVertexWithUV(x + w, y, 0, (u + uw) * s, v * s);
        tessellator.addVertexWithUV(x, y, 0, u * s, v * s);
        tessellator.draw();
    }
}
