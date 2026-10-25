package com.lauua.lauuacheats.ui;

import com.lauua.lauuacheats.core.Module;
import com.lauua.lauuacheats.core.ModuleManager;
import com.lauua.lauuacheats.hud.HudRegistry;
import com.lauua.lauuacheats.scan.ChunkScanner;
import com.lauua.lauuacheats.settings.Setting;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Original dashboard design: compact sidebar, live search, cards, split detail pane and interactive settings. */
public final class ClickGui extends Screen {
    private static final int BG=0xFF0A101B, SURFACE=0xFF121D2B, CARD=0xFF182738,
        MINT=0xFF71E3CF, TEXT=0xFFE5F3F7, MUTED=0xFF91A8BA, PURPLE=0xFFD197F3, BORDER=0xFF274253;
    private final ModuleManager manager;
    private final HudRegistry hud;
    private final ChunkScanner scanner;
    private final Runnable save;
    private EditBox search;
    private Module.Category category=Module.Category.WORLD;
    private Module selected;
    private boolean detailOpen;
    private String query="";
    private int x,y,w,h,sidebar,detail,listScroll,settingsScroll;
    private int dragX,dragY;
    private boolean moving,wasDown,editingHud;
    private String movingHud;
    private Setting<?> sliding;
    private Setting.KeybindSetting binding;
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
        sidebar=w<690?112:160; detail=w<690?0:244;
        search=new EditBox(font,x+sidebar+19,y+51,Math.max(90,w-sidebar-detail-44),19,Component.literal("Search modules"));
        search.setValue(query);
        search.setHint(Component.literal("Search modules…"));
        search.setResponder(value->{ query=value; listScroll=0; });
        addRenderableWidget(search);
        ScreenMouseEvents.afterMouseClick(this).register((screen,event,consumed)-> {
            if(consumed) return true;
            onClick((int)event.x(),(int)event.y(),event.button()); return true;
        });
        ScreenMouseEvents.afterMouseScroll(this).register((screen,mx,my,horizontal,vertical,consumed)-> {
            if((detail==0 && detailOpen) || (detail>0 && mx>x+w-detail)) settingsScroll=Math.max(0,settingsScroll-(int)Math.signum(vertical)*2);
            else listScroll=Math.max(0,listScroll-(int)Math.signum(vertical)*2);
            return true;
        });
    }
    @Override public void tick() {
        super.tick();
        int[] p=mouse(); boolean down=GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(),GLFW.GLFW_MOUSE_BUTTON_LEFT)==GLFW.GLFW_PRESS;
        if(!down) { moving=false; movingHud=null; sliding=null; }
        search.visible=detail>0 || !detailOpen;
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
        if(new Rect(x+w-112,y+7,42,18).has(mx,my)) { editingHud=!editingHud; return; }
        int navTop=y+72,navStep=20;
        if(mx>=x+10 && mx<x+sidebar-8 && my>=navTop-2 && my<navTop+Module.Category.values().length*navStep-2) {
            int idx=(my-navTop+2)/navStep; category=Module.Category.values()[idx]; listScroll=0; detailOpen=false; return;
        }
        if(editingHud && button==GLFW.GLFW_MOUSE_BUTTON_LEFT) for(HudRegistry.Element e:hud.all()) {
            if(new Rect(e.x(),e.y(),14,11).has(mx,my)) { movingHud=e.name(); dragX=mx-e.x(); dragY=my-e.y(); return; }
        }
        if(detail==0 && detailOpen && new Rect(x+sidebar+12,y+38,86,25).has(mx,my)) {
            detailOpen=false; return;
        }
        for(Card card:cards) if(card.bounds().has(mx,my)) {
            selected=card.module(); settingsScroll=0;
            if(button==GLFW.GLFW_MOUSE_BUTTON_LEFT && mx>=card.bounds().x()+card.bounds().w()-46) card.module().toggle();
            else if(detail==0) detailOpen=true;
            return;
        }
        for(var row:settingRows.entrySet()) if(row.getValue().has(mx,my)) {
            Setting<?> setting=row.getKey();
            if(button==GLFW.GLFW_MOUSE_BUTTON_RIGHT) { setting.reset(); binding=null; }
            else if(setting instanceof Setting.KeybindSetting key) binding=key;
            else if(setting instanceof Setting.BooleanSetting b) b.set(!b.get());
            else if(setting instanceof Setting.IntSlider || setting instanceof Setting.DoubleSlider) { sliding=setting; updateSlider(setting,mx); }
            else if(setting instanceof Setting.EnumSetting<?> e) cycle(e);
            else if(setting instanceof Setting.ColorSetting c) c.set(nextColor(c.get()));
            return;
        }
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if(binding!=null) {
            binding.set(event.isEscape() || event.key()==GLFW.GLFW_KEY_BACKSPACE?-1:event.key());
            binding=null;
            return true;
        }
        return super.keyPressed(event);
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
        rect(g,x-3,y-3,w+6,h+6,0xAA4C6E79);
        rect(g,x-2,y-2,w+4,h+4,0xFF09121F);
        rect(g,x,y,w,h,BG);
        rect(g,x,y,w,32,0xFF142435);
        rect(g,x,y,3,32,MINT);
        rect(g,x+10,y+8,16,16,0xFF315D62);
        text(g,"L",x+15,y+12,MINT);
        text(g,"LAUUA",x+33,y+11,TEXT);
        text(g,"/ EXPLORER",x+90,y+11,MUTED);
        if(w>680) text(g,"26.2  /  CLIENT",x+w-241,y+11,MUTED);
        rect(g,x+w-113,y+6,42,20,editingHud?0xFF337970:0xFF263C4D);
        text(g,"HUD",x+w-104,y+12,editingHud?TEXT:MUTED);
        rect(g,x+w-67,y+6,58,20,0xFF32736B);
        text(g,"SAVE",x+w-55,y+12,TEXT);
        rect(g,x,y+32,sidebar,h-32,0xFF111F2D);
        rect(g,x+sidebar-1,y+32,1,h-32,BORDER);
        text(g,"CATEGORIES",x+13,y+50,MUTED);
        int cy=y+72;
        for(Module.Category cat:Module.Category.values()) {
            boolean active=cat==category;
            if(active) { rect(g,x+6,cy-2,sidebar-13,18,0xFF25424C); rect(g,x+6,cy-2,2,18,MINT); }
            text(g,cat.name(),x+15,cy+3,active?MINT:MUTED);
            cy+=20;
        }
        rect(g,x+9,y+h-66,sidebar-18,53,0xFF1B3240);
        rect(g,x+9,y+h-66,sidebar-18,1,0xFF396477);
        text(g,"LIVE INDEX",x+16,y+h-57,MINT);
        text(g,scanner.visible().size()+" markers",x+16,y+h-41,TEXT);
        text(g,"RSHIFT to close",x+16,y+h-26,MUTED);
        int centerX=x+sidebar+10,centerW=w-sidebar-detail-17;
        text(g,category==Module.Category.WORLD?"WORLD / BASEFINDING":category.name()+" / MODULES",centerX+9,y+39,TEXT);
        rect(g,centerX+8,y+78,centerW-14,1,BORDER);
        var filtered=manager.all().stream().filter(m->m.category==category)
            .filter(m->m.name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))
                || m.description.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
        int cols=centerW>=380?2:1,cardW=(centerW-17-(cols-1)*8)/cols;
        int maxRows=Math.max(1,(h-126)/62);
        listScroll=Math.min(listScroll,Math.max(0,(filtered.size()+cols-1)/cols-maxRows));
        for(int i=listScroll*cols;i<filtered.size() && (detail>0 || !detailOpen);i++) {
            int row=(i/cols)-listScroll,column=i%cols,top=y+88+row*62;
            if(top+55>y+h-17) break;
            Module m=filtered.get(i); int left=centerX+8+column*(cardW+8);
            boolean focused=m==selected;
            rect(g,left,top,cardW,56,focused?0xFF25404E:CARD);
            rect(g,left,top,2,56,m.enabled()?MINT:0xFF395367);
            rect(g,left+5,top+55,cardW-10,1,BORDER);
            text(g,fit(m.name,cardW-61),left+10,top+8,TEXT);
            text(g,fit(m.description,cardW-18),left+10,top+24,MUTED);
            text(g,m.enabled()?"ACTIVE":"PAUSED",left+10,top+42,m.enabled()?MINT:MUTED);
            text(g,"/ " + m.tier.name(),left+69,top+42,m.tier==Module.Tier.D?0xFFFFA9B5:PURPLE);
            rect(g,left+cardW-44,top+8,34,17,m.enabled()?0xFF32796E:0xFF304353);
            rect(g,left+cardW-(m.enabled()?26:41),top+10,13,13,m.enabled()?MINT:MUTED);
            cards.add(new Card(m,new Rect(left,top,cardW,56)));
        }
        if(filtered.isEmpty() && !detailOpen) {
            text(g,"NO MATCHING MODULES",centerX+12,y+112,MUTED);
            text(g,"Try a different category or search.",centerX+12,y+129,MUTED);
        }
        if(!detailOpen && detail==0) text(g,"CLICK CARD FOR SETTINGS  /  SCROLL FOR MORE",centerX+8,y+h-15,MUTED);
        if(detail>0 || detailOpen) {
        int panelX=detail==0?x+sidebar+4:x+w-detail+2;
        int panelW=detail==0?w-sidebar-13:detail-11;
        if(detail==0) {
            rect(g,panelX-2,y+32,panelW+8,h-32,BG);
            rect(g,panelX+9,y+38,87,25,CARD);
            text(g,"← MODULES",panelX+17,y+46,MINT);
        } else rect(g,panelX-2,y+32,1,h-32,0xFF254558);
        text(g,"INSPECTOR",panelX+(detail==0?114:13),y+47,MUTED);
        if(selected!=null) {
            boolean compact=h<390;
            text(g,fit(selected.name,panelW-24),panelX+13,y+72,TEXT);
            text(g,(selected.enabled()?"ACTIVE  /  ":"PAUSED  /  ")+"TIER " + selected.tier.name(),panelX+13,y+89,selected.enabled()?MINT:PURPLE);
            text(g,fit(selected.note,panelW-25),panelX+13,y+105,MUTED);
            int labelY=compact?y+123:y+144,rowsY=compact?y+142:y+170,rowH=compact?36:52;
            rect(g,panelX+12,labelY-10,panelW-18,1,BORDER);
            text(g,"SETTINGS  /  RIGHT-CLICK TO RESET",panelX+13,labelY,MUTED);
            List<Setting<?>> settings=selected.settings();
            int max=Math.max(1,(h-(rowsY-y)-14)/rowH);
            settingsScroll=Math.min(settingsScroll,Math.max(0,settings.size()-max));
            for(int i=settingsScroll;i<settings.size();i++) {
                int ry=rowsY+(i-settingsScroll)*rowH;
                if(ry+rowH-3>y+h-11) break;
                Setting<?> s=settings.get(i);
                rect(g,panelX+10,ry,panelW-14,rowH-4,SURFACE);
                text(g,fit(s.name,panelW-31),panelX+17,ry+4,TEXT);
                String value=s==binding?"PRESS KEY / ESC TO CLEAR":s instanceof Setting.KeybindSetting k
                    ? k.get()==-1?"UNBOUND / CLICK TO BIND":"KEY " + k.get()
                    : s instanceof Setting.ColorSetting c?String.format("#%06X",c.get()&0xffffff):String.valueOf(s.get());
                text(g,fit(value,panelW-38),panelX+17,ry+(compact?17:23),
                    s instanceof Setting.BooleanSetting b && b.get()?MINT:MUTED);
                if(s instanceof Setting.ColorSetting c) rect(g,panelX+panelW-31,ry+18,11,10,c.get());
                if(s instanceof Setting.IntSlider slider) progress(g,panelX+17,ry+rowH-9,panelW-32,(slider.get()-slider.min)/(double)(slider.max-slider.min));
                if(s instanceof Setting.DoubleSlider slider) progress(g,panelX+17,ry+rowH-9,panelW-32,(slider.get()-slider.min)/(slider.max-slider.min));
                settingRows.put(s,new Rect(panelX+10,ry,panelW-14,rowH-4));
            }
            if(settings.size()>max) text(g,(settingsScroll+1)+"-"+Math.min(settings.size(),settingsScroll+max)+" / "+settings.size(),
                panelX+panelW-63,labelY,MINT);
        } else text(g,"Select a module",panelX+13,y+73,MUTED);
        }
        super.extractRenderState(g,mx,my,delta); // Search field is the only vanilla widget.
        if(editingHud) for(HudRegistry.Element e:hud.all()) {
            // HUD handles are intentionally hidden outside edit mode: on small GUI scales
            // they overlap navigation and may obscure actionable module controls.
            rect(g,e.x(),e.y(),Math.min(95,font.width(e.name())+15),12,0xEE30625F);
            text(g,"::"+e.name(),e.x()+2,e.y()+2,TEXT);
        }
    }
    /** Test hooks return real rendered hit targets, not inferred positions. */
    public int[] categoryPoint(Module.Category target) {
        return new int[]{x+26,y+78+target.ordinal()*20};
    }
    public int[] moduleInspectPoint(String name) {
        for(Card card:cards) if(card.module().name.equals(name)) return new int[]{card.bounds().x()+20,card.bounds().y()+13};
        throw new IllegalStateException("Card not rendered: " + name);
    }
    public int[] settingPoint(String name) {
        for(var row:settingRows.entrySet()) if(row.getKey().name.equals(name))
            return new int[]{row.getValue().x()+row.getValue().w()/2,row.getValue().y()+10};
        throw new IllegalStateException("Setting not rendered: " + name + " visible=" + settingRows.keySet().stream().map(s->s.name).toList());
    }
    /** Test hook for actual client input: coordinates come from the last rendered card layout. */
    public int[] moduleTogglePoint(String name) {
        for(Card card:cards) if(card.module().name.equals(name)) {
            Rect b=card.bounds(); return new int[]{b.x()+b.w()-18,b.y()+17};
        }
        throw new IllegalStateException("Module card not visible: " + name + " cards="+cards.stream().map(c->c.module().name).toList() + " size="+width+"x"+height+" panel="+w+"x"+h+" category="+category+" query="+query);
    }
    private static void progress(GuiGraphicsExtractor g,int x,int y,int w,double ratio) {
        rect(g,x,y,w,3,0xFF314558); rect(g,x,y,Math.max(2,(int)(w*Math.clamp(ratio,0,1))),3,MINT);
    }
    private static void rect(GuiGraphicsExtractor g,int x,int y,int w,int h,int color) { g.fill(x,y,x+Math.max(1,w),y+Math.max(1,h),color); }
    private void text(GuiGraphicsExtractor g,String value,int x,int y,int color) { g.text(font,value,x,y,color); }
    private String fit(String s,int maxWidth) {
        if(font.width(s)<=maxWidth) return s;
        while(!s.isEmpty() && font.width(s+"…")>maxWidth) s=s.substring(0,s.length()-1);
        return s+"…";
    }
    private static String shorten(String s,int limit) { return s.length()>limit?s.substring(0,Math.max(0,limit-1))+"…":s; }
    @Override public boolean isPauseScreen() { return false; }
}
