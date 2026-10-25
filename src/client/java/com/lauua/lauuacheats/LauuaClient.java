package com.lauua.lauuacheats;

import com.lauua.lauuacheats.core.*;
import com.lauua.lauuacheats.core.Module;
import com.lauua.lauuacheats.hud.HudRegistry;
import com.lauua.lauuacheats.modules.CombatModules;
import com.lauua.lauuacheats.modules.WorldModules;
import com.lauua.lauuacheats.rotation.RotationManager;
import com.lauua.lauuacheats.scan.*;
import com.lauua.lauuacheats.settings.Setting.*;
import com.lauua.lauuacheats.text.TextPolicy;
import com.lauua.lauuacheats.ui.ClickGui;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.Locale;

public final class LauuaClient implements ClientModInitializer {
    public static final String ID="lauuacheats", PREFIX=".lauua";
    private final ModuleManager modules=new ModuleManager();
    private final EventBus bus=new EventBus();
    private final KeybindManager keys=new KeybindManager();
    private final RotationManager rotations=new RotationManager();
    private final MacroManager macros=new MacroManager(rotations);
    private final HudRegistry hud=new HudRegistry();
    private final ChunkScanner scanner=new ChunkScanner();
    private final CoordLogger logger=new CoordLogger();
    private final ProfileManager profiles=new ProfileManager(modules);
    private ClientLevel lastLevel;
    private String world="";

    @Override public void onInitializeClient() {
        modules.register(new Module("Vanilla Mode","Clamp reach and enforce packet ordering",Module.Category.MISC,-1,true,Module.Tier.A,"Enabled by default") {
            final BooleanSetting brand=add(new BooleanSetting("Vanilla brand",true));
            final IntSlider place=add(new IntSlider("Place delay ms",100,0,1000));
            final IntSlider breaking=add(new IntSlider("Break delay ms",100,0,1000));
            final IntSlider cap=add(new IntSlider("Per tick cap",2,1,20));
            final IntSlider jitter=add(new IntSlider("Jitter ms",0,0,200));
            @Override public void tick() { rotations.vanillaMode=enabled(); rotations.placeDelayMs=place.get(); rotations.breakDelayMs=breaking.get(); rotations.perTickCap=cap.get(); rotations.jitterMs=jitter.get(); TextPolicy.vanillaBrand=brand.get(); }
            @Override protected void onDisable() { rotations.vanillaMode=false; }
        });
        CombatModules.register(modules,rotations,macros);
        WorldModules.register(modules,scanner,hud,()->world);
        try { profiles.load("default"); } catch (Exception e) { System.err.println("[Lauua] profile load failed: " + e); }
        hud.register(modules);
        keys.bind(GLFW.GLFW_KEY_RIGHT_SHIFT,()-> Minecraft.getInstance().setScreen(new ClickGui(modules,hud)));
        keys.bind(GLFW.GLFW_KEY_END,()-> { for(Module m:modules.all()) m.setEnabled(false); rotations.clear(); macros.abort(); });
        keys.bind(GLFW.GLFW_KEY_INSERT,()->command("waypoint Quick"));
        keys.bind(GLFW.GLFW_KEY_F9,()->macros.start("anchor"));
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if(!message.startsWith(PREFIX)) return true;
            command(message.substring(PREFIX.length()).trim()); return false;
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private void tick(Minecraft mc) {
        if(lastLevel!=mc.level) {
            lastLevel=mc.level; rotations.clear(); macros.abort(); scanner.reset(); modules.worldChanged(); bus.post(new EventBus.WorldChanged());
        }
        if(mc.level!=null) {
            String server=mc.getCurrentServer()==null?"singleplayer":mc.getCurrentServer().ip;
            world=server+"/"+mc.level.dimension().identifier();
        } else world="";
        keys.tick(mc,modules); modules.tick(); macros.tick(mc); rotations.tick(mc);
        if(mc.level!=null && mc.player!=null && WorldModules.scanning(modules)) scanner.tick(mc,world);
        bus.post(new EventBus.Tick());
    }
    private void command(String input) {
        Minecraft mc=Minecraft.getInstance();
        String[] args=input.split("\\s+",3);
        try {
            switch(args[0].toLowerCase(Locale.ROOT)) {
                case "gui" -> mc.setScreen(new ClickGui(modules,hud));
                case "panic" -> { for(Module m:modules.all()) m.setEnabled(false); rotations.clear(); macros.abort(); reply("Modules disabled"); }
                case "toggle" -> { if(args.length<2) throw new IllegalArgumentException("toggle <module>"); Module m=modules.find(args[1]).orElseThrow(); m.toggle(); reply(m.name+" " + (m.enabled()?"ON":"OFF")); }
                case "save" -> { profiles.save(args.length>1?args[1]:"default"); reply("Profile saved"); }
                case "load" -> { profiles.load(args.length>1?args[1]:"default"); reply("Profile loaded"); }
                case "macro" -> { if(args.length<2) throw new IllegalArgumentException("macro <name>"); macros.start(args[1]); reply("Macro started"); }
                case "waypoint" -> { logger.log(mc,world,args.length>1?input.substring(9).trim():"Waypoint"); }
                case "export" -> { logger.export(); reply("Exported waypoints"); }
                case "scan" -> reply(scanner.results(world).stream().limit(5).map(r->r.x()+","+r.z()+" "+r.classification()+" score="+Math.round(r.clusterScore())).toList().toString());
                case "weight" -> { if(args.length<3) throw new IllegalArgumentException("weight <block id> <score>"); scanner.weight(args[1],Double.parseDouble(args[2])); reply("Weight updated"); }
                case "hud" -> { if(args.length<3) throw new IllegalArgumentException("hud <element> <x>,<y>"); String[] xy=args[2].split(","); hud.move(args[1],Integer.parseInt(xy[0]),Integer.parseInt(xy[1])); reply("HUD moved"); }
                default -> reply("Commands: gui, panic, toggle, save, load, macro, waypoint, export, scan, weight, hud");
            }
        } catch (Exception e) { reply("Error: "+e.getMessage()); }
    }
    private void reply(String message) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player!=null) mc.player.sendSystemMessage(Component.literal("[Lauua] " + message));
    }
}
