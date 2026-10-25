package com.lauua.lauuacheats.ui;

import com.lauua.lauuacheats.core.*;
import com.lauua.lauuacheats.core.Module;
import com.lauua.lauuacheats.hud.HudRegistry;
import com.lauua.lauuacheats.settings.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Widget-based GUI: category panels, live filtering, selected module settings and header dragging. */
public final class ClickGui extends Screen {
    private final ModuleManager manager;
    private final HudRegistry hud;
    private String movingHud;
    private final Set<Module.Category> closed = EnumSet.noneOf(Module.Category.class);
    private String query = "";
    private Module selected;
    private int originX = 20, originY = 28, offsetX, offsetY;
    private boolean dragging, previousLeft, previousRight;
    private final Map<Setting<?>, int[]> rows = new HashMap<>();
    public ClickGui(ModuleManager manager, HudRegistry hud) { super(Component.literal("Lauua Cheats")); this.manager=manager; this.hud=hud; }
    @Override protected void init() { rebuild(); }
    private void rebuild() {
        this.clearWidgets(); rows.clear();
        EditBox search = new EditBox(font, originX, originY, 155, 18, Component.literal("Search"));
        search.setValue(query); search.setResponder(s -> { query=s; rebuild(); });
        addRenderableWidget(search);
        int y = originY + 24;
        for (Module.Category category : Module.Category.values()) {
            addRenderableWidget(Button.builder(Component.literal((closed.contains(category) ? "> " : "v ") + category.name()), b -> {
                if (!closed.add(category)) closed.remove(category); rebuild();
            }).bounds(originX, y, 155, 18).build()); y+=19;
            if (closed.contains(category)) continue;
            for (Module module : manager.all()) {
                if (module.category != category || !module.name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) continue;
                addRenderableWidget(Button.builder(Component.literal("[" + module.tier + "] " + module.name + (module.enabled() ? " ON" : " OFF")), b -> {
                    module.toggle(); selected=module; rebuild();
                }).bounds(originX + 8, y, 147, 18).build()); y+=19;
                if (y > height - 40) break;
            }
        }
        if (selected == null) return;
        int sy=originY+24, sx=originX+168;
        addRenderableWidget(Button.builder(Component.literal(selected.name + " [" + selected.tier + "]"), b -> { selected=null; rebuild(); }).bounds(sx, sy, 170, 18).build()); sy+=22;
        for (Setting<?> setting : selected.settings()) {
            if (sy > height-38) break;
            final Setting<?> target=setting;
            addRenderableWidget(Button.builder(Component.literal(label(setting)), b -> { next(target); rebuild(); }).bounds(sx, sy, 170, 18).build());
            rows.put(setting, new int[]{sx,sy,sx+170,sy+18}); sy+=20;
        }
    }
    private String label(Setting<?> setting) { return setting.name + ": " + String.valueOf(setting.get()); }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void next(Setting<?> s) {
        if (s instanceof Setting.BooleanSetting b) b.set(!b.get());
        else if (s instanceof Setting.IntSlider i) i.set(i.get()+1 > i.max ? i.min : i.get()+1);
        else if (s instanceof Setting.DoubleSlider d) d.set(d.get()+0.1 > d.max ? d.min : Math.round((d.get()+0.1)*10.0)/10.0);
        else if (s instanceof Setting.EnumSetting e) { Enum[] all=(Enum[])((Enum)e.get()).getDeclaringClass().getEnumConstants(); e.set(all[(((Enum)e.get()).ordinal()+1)%all.length]); }
        // Strings, lists, colors and keybinds are editable in the JSON profile; clicking does not destroy them.
    }
    @Override public void tick() {
        super.tick();
        Minecraft mc=Minecraft.getInstance(); long window=mc.getWindow().handle();
        double[] x={0}, y={0}; GLFW.glfwGetCursorPos(window,x,y);
        int mx=(int)(x[0]*width/mc.getWindow().getScreenWidth()), my=(int)(y[0]*height/mc.getWindow().getScreenHeight());
        boolean left=GLFW.glfwGetMouseButton(window,GLFW.GLFW_MOUSE_BUTTON_LEFT)==GLFW.GLFW_PRESS;
        boolean right=GLFW.glfwGetMouseButton(window,GLFW.GLFW_MOUSE_BUTTON_RIGHT)==GLFW.GLFW_PRESS;
        if (left && !previousLeft) for (HudRegistry.Element element : hud.all()) {
            if (mx>=element.x() && mx<element.x()+14 && my>=element.y() && my<element.y()+11) {
                movingHud=element.name(); offsetX=mx-element.x(); offsetY=my-element.y(); break;
            }
        }
        if (left && !previousLeft && movingHud==null && mx>=originX && mx<originX+340 && my>=originY-22 && my<originY) {
            dragging=true; offsetX=mx-originX; offsetY=my-originY;
        }
        if (!left) { dragging=false; movingHud=null; }
        if (movingHud!=null) hud.move(movingHud, Math.max(0,mx-offsetX),Math.max(0,my-offsetY));
        if (dragging) { originX=mx-offsetX; originY=my-offsetY; rebuild(); }
        if (right && !previousRight) for (var entry : rows.entrySet()) {
            int[] r=entry.getValue();
            if (mx>=r[0] && mx<r[2] && my>=r[1] && my<r[3]) { entry.getKey().reset(); rebuild(); break; }
        }
        previousLeft=left; previousRight=right;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
        graphics.fill(originX, originY-22, originX+340, originY-2, 0xDD161820);
        graphics.text(font, "Lauua Cheats  •  drag here", originX+5, originY-17, 0xFFFFFFFF);
        if (selected != null) graphics.text(font, selected.note, originX+168, originY-18, 0xFFAAAAAA);
        super.extractRenderState(graphics,mx,my,delta);
        for (HudRegistry.Element e : hud.all()) {
            graphics.fill(e.x(),e.y(),e.x()+14,e.y()+11,0x99628BFF);
            graphics.text(font,"::",e.x()+2,e.y()+1,0xFFFFFFFF);
        }
    }
    @Override public boolean isPauseScreen() { return false; }
}
