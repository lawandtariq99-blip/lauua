package com.lauua.lauuacheats.hud;

import com.lauua.lauuacheats.core.ModuleManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import java.util.*;

/** HUD positions are independent of world state; a GUI can move them with move(). */
public final class HudRegistry {
    public record Element(String name, int x, int y, java.util.function.Supplier<String> text) { }
    private final Map<String, Element> elements = new LinkedHashMap<>();
    public boolean background = true, shadow = true;
    public int opacity = 160;
    public void put(String name, int x, int y, java.util.function.Supplier<String> text) { elements.put(name,new Element(name,x,y,text)); }
    public void move(String name, int x, int y) { Element e=elements.get(name); if(e!=null) put(name,x,y,e.text()); }
    public Collection<Element> all() { return Collections.unmodifiableCollection(elements.values()); }
    public void register(ModuleManager modules) {
        Minecraft mc=Minecraft.getInstance();
        put("Coordinates", 5, 5, () -> mc.player == null ? "" : "XYZ " + mc.player.blockPosition().toShortString());
        put("Reach", 5, 19, () -> "Reach: " + (mc.hitResult == null || mc.player == null ? "—" : String.format(Locale.ROOT,"%.2f",mc.hitResult.getLocation().distanceTo(mc.player.getEyePosition()))));
        put("Modules", 5, 33, () -> String.join(" | ",modules.all().stream().filter(m->m.enabled()).map(m->m.name).limit(12).toList()));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("lauuacheats","hud"), (graphics, delta) -> draw(graphics));
    }
    private void draw(GuiGraphicsExtractor graphics) {
        Minecraft mc=Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        for (Element e : elements.values()) {
            String text=e.text().get(); if (text.isEmpty()) continue;
            if (background) graphics.fill(e.x()-2,e.y()-2,e.x()+mc.font.width(text)+3,e.y()+11,(Math.max(0,Math.min(255,opacity))<<24)|0x121212);
            if (shadow) graphics.text(mc.font,text,e.x()+1,e.y()+1,0xFF111111);
            graphics.text(mc.font,text,e.x(),e.y(),0xFFFFFFFF);
        }
    }
}
