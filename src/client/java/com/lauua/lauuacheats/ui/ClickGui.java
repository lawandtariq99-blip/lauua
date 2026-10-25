package com.lauua.lauuacheats.ui;

import com.lauua.lauuacheats.core.Module;
import com.lauua.lauuacheats.core.ModuleManager;
import com.lauua.lauuacheats.hud.HudRegistry;
import com.lauua.lauuacheats.scan.ChunkScanner;
import com.lauua.lauuacheats.settings.Setting;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Original dashboard design: compact sidebar, live search, cards, split detail pane and interactive settings. */
public final class ClickGui extends Screen {
    private static final int BG=0xFF0A101B, SURFACE=0xFF121D2B, CARD=0xFF182738,
        MINT=0xFF71E3CF, TEXT=0xFFE5F3F7, MUTED=0xFF91A8BA, PURPLE=0xFFD197F3;
    private final ModuleManager manager;
    private final HudRegistry hud;
    private final ChunkScanner scanner;
    private final Runnable save;
    private EditBox search;
    private Module.Category category=Module.Category.WORLD;
    private Module selected;
    private String query="";
    private int x,y,w,h,sidebar,detail,listScroll,settingsScroll;
    private int dragX,dragY;
    private boolean moving,wasDown;
    private String movingHud;
    private Setting<?> sliding;
    private final List<Card> cards=new ArrayList<>();
    private final Map<Setting<?>,Rect> settingRows=new LinkedHashMap<>();
    private record Rect(int x,int y,int w,int h) {
        boolean has(int px,int py) { return px>=x && px<x+w && py>=y && py<y+h; }
    }
    private record Card(Module module, Rect bounds) { }

    public ClickGui(ModuleManager manager, HudRegistry hud, ChunkScanner scanner, Runnable save) {
        super(Component.literal("Lauua | Explorer"));
        this.manager=manager; this.hud=hud; this.scanner=scanner; this.save=save;
        this.selected=manager.find("ClusterFinder").orElse(null);
    }
    @Override protected void init() {
        w=Math.min(940,width-22); h=Math.min(555,height-24);
        x=Math.max(11,(width-w)/2); y=Math.max(12,(height-h)/2);
        sidebar=w<650?126:160; detail=w<650?180:244;
        search=new EditBox(font,x+sidebar+19,y+51,Math.max(90,w-sidebar-detail-44),19,Component.literal("Search modules"));
        search.setValue(query);
        search.setHint(Component.literal("Search modules…"));
        search.setResponder(value->{ query=value; listScroll=0; });
        addRenderableWidget(search);
        ScreenMouseEvents.afterMouseClick(this).register((screen,event,consumed)-> {
            if(consumed) return true;
            int[] p=mouse(); onClick(p[0],p[1],event.button()); return true;
        });
        ScreenMouseEvents.afterMouseScroll(this).register((screen,mx,my,horizontal,vertical,consumed)-> {
            if(mx<x+sidebar || mx>x+w-detail) settingsScroll=Math.max(0,settingsScroll-(int)Math.signum(vertical)*2);
            else listScroll=Math.max(0,listScroll-(int)Math.signum(vertical)*2);
            return true;
        });
    }
    @Override public void tick() {
        super.tick();
        int[] p=mouse(); boolean down=GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(),GLFW.GLFW_MOUSE_BUTTON_LEFT)==GLFW.GLFW_PRESS;
        if(!down) { moving=false; movingHud=null; sliding=null; }
        if(down && !wasDown && new Rect(x,y,w,32).has(p[0],p[1]) && p[0]<x+w-72) {
            moving=true; dragX=p[0]-x; dragY=p[1]-y;
        }
        if(moving) {
            int nx=Math.clamp(p[0]-dragX,2,Math.max(2,width-w-2));
            int ny=Math.clamp(p[1]-dragY,2,Math.max(2,height-h-2));
            if(nx!=x || ny!=y) { x=nx; y=ny; search.setX(x+sidebar+19); search.setY(y+51); }
        }
        if(sliding!=null) updateSlider(sliding,p[0]);
        if(movingHud!=null) hud.move(movingHud,Math.max(0,p[0]-dragX),Math.max(0,p[1]-dragY));
        wasDown=down;
    }
    private int[] mouse() {
        Minecraft mc=Minecraft.getInstance(); double[] px={0},py={0};
        GLFW.glfwGetCursorPos(mc.getWindow().handle(),px,py);
        return new int[]{(int)(px[0]*width/mc.getWindow().getScreenWidth()),(int)(py[0]*height/mc.getWindow().getScreenHeight())};
    }
    private void onClick(int mx,int my,int button) {
        if(new Rect(x+w-66,y+7,56,18).has(mx,my)) { save.run(); return; }
        if(mx>=x+11 && mx<x+sidebar-10 && my>=y+101 && my<y+101+Module.Category.values().length*31) {
            int idx=(my-y-101)/31; category=Module.Category.values()[idx]; listScroll=0; return;
        }
        if(button==GLFW.GLFW_MOUSE_BUTTON_LEFT) for(HudRegistry.Element e:hud.all()) {
            if(new Rect(e.x(),e.y(),14,11).has(mx,my)) { movingHud=e.name(); dragX=mx-e.x(); dragY=my-e.y(); return; }
        }
        for(Card card:cards) if(card.bounds().has(mx,my)) {
            selected=card.module(); settingsScroll=0;
            if(button==GLFW.GLFW_MOUSE_BUTTON_LEFT && mx>=card.bounds().x()+card.bounds().w()-46) card.module().toggle();
            return;
        }
        for(var row:settingRows.entrySet()) if(row.getValue().has(mx,my)) {
            Setting<?> setting=row.getKey();
            if(button==GLFW.GLFW_MOUSE_BUTTON_RIGHT) setting.reset();
            else if(setting instanceof Setting.BooleanSetting b) b.set(!b.get());
            else if(setting instanceof Setting.IntSlider || setting instanceof Setting.DoubleSlider) { sliding=setting; updateSlider(setting,mx); }
            else if(setting instanceof Setting.EnumSetting<?> e) cycle(e);
            else if(setting instanceof Setting.ColorSetting c) c.set(nextColor(c.get()));
            return;
        }
    }
    private int nextColor(int value) {
        int[] palette={0xFF71E3CF,0xFFD197F3,0xFFF7B96E,0xFF76A9FF,0xFFFF7C9B};
        for(int i=0;i<palette.length;i++) if(value==palette[i]) return palette[(i+1)%palette.length];
        return palette[0];
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private void cycle(Setting.EnumSetting<?> e) {
        Setting.EnumSetting raw=e;
        Enum value=(Enum)e.get(); Enum[] options=(Enum[])value.getDeclaringClass().getEnumConstants();
        raw.set(options[(value.ordinal()+1)%options.length]);
    }
    private void updateSlider(Setting<?> setting,int px) {
        Rect r=settingRows.get(setting); if(r==null) return;
        double fraction=Math.clamp((px-(r.x()+9))/(double)(r.w()-18),0,1);
        if(setting instanceof Setting.IntSlider s) s.set(s.min+(int)Math.round((s.max-s.min)*fraction));
        else if(setting instanceof Setting.DoubleSlider s) s.set(Math.round((s.min+(s.max-s.min)*fraction)*10.0)/10.0);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        cards.clear(); settingRows.clear();
        g.fill(0,0,width,height,0xBB070D16);
        rect(g,x-2,y-2,w+4,h+4,0x664A6D78);
        rect(g,x,y,w,h,BG);
        rect(g,x,y,w,32,0xFF142435);
        rect(g,x,y,3,32,MINT);
        text(g,"◆ LAUUA",x+14,y+11,MINT);
        text(g,"/  EXPLORER",x+83,y+11,TEXT);
        text(g,"26.2 • CLIENT",x+w-183,y+11,MUTED);
        rect(g,x+w-67,y+6,58,20,0xFF275C60);
        text(g,"SAVE",x+w-55,y+12,TEXT);
        rect(g,x,y+32,sidebar,h-32,0xFF101C29);
        text(g,"NAVIGATION",x+16,y+71,MUTED);
        int cy=y+101;
        for(Module.Category cat:Module.Category.values()) {
            boolean active=cat==category;
            if(active) { rect(g,x+7,cy-3,sidebar-15,26,0xFF1B3943); rect(g,x+7,cy-3,3,26,MINT); }
            text(g,(cat==Module.Category.WORLD?"◈ ":"• ")+cat.name(),x+17,cy+4,active?MINT:MUTED);
            cy+=31;
        }
        rect(g,x+13,y+h-89,sidebar-27,66,0xFF162B37);
        text(g,"BASEFINDER",x+20,y+h-81,MINT);
        text(g,scanner.visible().size()+" live markers",x+20,y+h-62,TEXT);
        text(g,"RSHIFT / CLOSE",x+20,y+h-46,MUTED);
        int centerX=x+sidebar+10,centerW=w-sidebar-detail-17;
        text(g,category==Module.Category.WORLD?"Basefinding & ESP":category.name()+" modules",centerX+9,y+39,TEXT);
        rect(g,centerX+8,y+78,centerW-14,1,0xFF2B4455);
        var filtered=manager.all().stream().filter(m->m.category==category)
            .filter(m->m.name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))
                || m.description.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
        int cols=centerW>=380?2:1,cardW=(centerW-17-(cols-1)*8)/cols;
        int maxRows=Math.max(1,(h-115)/68);
        listScroll=Math.min(listScroll,Math.max(0,(filtered.size()+cols-1)/cols-maxRows));
        for(int i=listScroll*cols;i<filtered.size();i++) {
            int row=(i/cols)-listScroll,column=i%cols,top=y+91+row*68;
            if(top+61>y+h-10) break;
            Module m=filtered.get(i); int left=centerX+8+column*(cardW+8);
            boolean focused=m==selected;
            rect(g,left,top,cardW,60,focused?0xFF213847:CARD);
            rect(g,left,top,3,60,m.enabled()?MINT:0xFF395367);
            text(g,shorten(m.name,cardW<180?18:24),left+11,top+10,TEXT);
            text(g,shorten(m.description,cardW<180?22:30),left+11,top+28,MUTED);
            text(g,"TIER " + m.tier.name(),left+11,top+45,m.tier==Module.Tier.D?0xFFFFA9B5:PURPLE);
            int toggle=m.enabled()?0xFF2B786F:0xFF304353;
            rect(g,left+cardW-42,top+10,34,16,toggle);
            rect(g,left+cardW-(m.enabled()?25:39),top+12,12,12,m.enabled()?MINT:MUTED);
            cards.add(new Card(m,new Rect(left,top,cardW,60)));
        }
        int panelX=x+w-detail+2,panelW=detail-11;
        rect(g,panelX-2,y+32,1,h-32,0xFF254558);
        text(g,"INSPECTOR",panelX+13,y+47,MUTED);
        if(selected!=null) {
            text(g,shorten(selected.name,26),panelX+13,y+72,TEXT);
            text(g,"TIER " + selected.tier.name() + (selected.enabled()?"    ACTIVE":"    PAUSED"),panelX+13,y+91,selected.enabled()?MINT:PURPLE);
            text(g,shorten(selected.note,29),panelX+13,y+111,MUTED);
            rect(g,panelX+12,y+133,panelW-18,1,0xFF304657);
            text(g,"SETTINGS   •   RIGHT CLICK TO RESET",panelX+13,y+144,MUTED);
            List<Setting<?>> settings=selected.settings();
            int max=Math.max(1,(h-196)/52);
            settingsScroll=Math.min(settingsScroll,Math.max(0,settings.size()-max));
            for(int i=settingsScroll;i<settings.size();i++) {
                int ry=y+170+(i-settingsScroll)*52;
                if(ry+45>y+h-12) break;
                Setting<?> s=settings.get(i);
                rect(g,panelX+10,ry,panelW-14,46,SURFACE);
                text(g,shorten(s.name,25),panelX+18,ry+6,TEXT);
                text(g,shorten(String.valueOf(s.get()),20),panelX+18,ry+22,s instanceof Setting.BooleanSetting b && b.get()?MINT:MUTED);
                if(s instanceof Setting.IntSlider slider) progress(g,panelX+18,ry+39,panelW-32,(slider.get()-slider.min)/(double)(slider.max-slider.min));
                if(s instanceof Setting.DoubleSlider slider) progress(g,panelX+18,ry+39,panelW-32,(slider.get()-slider.min)/(slider.max-slider.min));
                settingRows.put(s,new Rect(panelX+10,ry,panelW-14,46));
            }
        } else text(g,"Select a module",panelX+13,y+73,MUTED);
        super.extractRenderState(g,mx,my,delta); // Search field is the only vanilla widget.
        for(HudRegistry.Element e:hud.all()) { rect(g,e.x(),e.y(),14,11,0xBB407E86); text(g,"::",e.x()+2,e.y()+1,TEXT); }
    }
    private static void progress(GuiGraphicsExtractor g,int x,int y,int w,double ratio) {
        rect(g,x,y,w,3,0xFF314558); rect(g,x,y,Math.max(2,(int)(w*Math.clamp(ratio,0,1))),3,MINT);
    }
    private static void rect(GuiGraphicsExtractor g,int x,int y,int w,int h,int color) { g.fill(x,y,x+Math.max(1,w),y+Math.max(1,h),color); }
    private void text(GuiGraphicsExtractor g,String value,int x,int y,int color) { g.text(font,value,x,y,color); }
    private static String shorten(String s,int limit) { return s.length()>limit?s.substring(0,Math.max(0,limit-1))+"…":s; }
    @Override public boolean isPauseScreen() { return false; }
}
