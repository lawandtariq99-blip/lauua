package com.lauua.lauuacheats.render;

import com.lauua.lauuacheats.core.Module;
import com.lauua.lauuacheats.core.ModuleManager;
import com.lauua.lauuacheats.modules.WorldModules.EspStyle;
import com.lauua.lauuacheats.scan.ChunkScanner;
import com.lauua.lauuacheats.settings.Setting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

/** Snapshot-only Minecraft 26.2 ESP. No chunks, block states or entities are read during rendering. */
public final class WorldEsp {
    private final ChunkScanner scanner;
    private final ModuleManager modules;
    public WorldEsp(ChunkScanner scanner, ModuleManager modules) { this.scanner=scanner; this.modules=modules; }
    public void register() { LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(this::draw); }
    private Module module(String name) { return modules.find(name).filter(Module::enabled).orElse(null); }
    private static Object value(Module m,String name,Object fallback) {
        if(m==null) return fallback;
        return m.settings().stream().filter(s->s.name.equals(name)).map(Setting::get).findFirst().orElse(fallback);
    }
    private static boolean flag(Module m,String name) { return Boolean.TRUE.equals(value(m,name,true)); }
    /** Colors follow the indexed storage subtype. Trapped chests get a distinct red tint. */
    public static int storageRgb(ChunkScanner.StorageType type, int chest, int barrel, int other) {
        return switch(type) {
            case CHEST -> chest;
            case TRAPPED_CHEST -> 0xFFFF7C91;
            case BARREL -> barrel;
            case SHULKER, ENDER_CHEST, MACHINE, NONE -> other;
        };
    }
    private static boolean storageEnabled(Module m,ChunkScanner.StorageType type) {
        return switch(type) {
            case CHEST, TRAPPED_CHEST -> flag(m,"Chests");
            case BARREL -> flag(m,"Barrels");
            case SHULKER -> flag(m,"Shulkers");
            case ENDER_CHEST, MACHINE, NONE -> flag(m,"Hoppers & furnaces");
        };
    }
    private void draw(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context) {
        var spots=scanner.visible();
        if(spots.isEmpty()) return;
        Module storage=module("StorageESP"), spawner=module("SpawnerESP"), clusters=module("ClusterFinder");
        Module ore=module("OreESP"), portal=module("PortalESP"), utility=module("UtilityESP");
        if(storage==null && spawner==null && clusters==null && ore==null && portal==null && utility==null) return;
        Vec3 camera=context.levelState().cameraRenderState.pos;
        PoseStack pose=context.poseStack();
        pose.pushPose(); pose.translate(-camera.x,-camera.y,-camera.z);
        int count=0;
        for(var h:spots) {
            if(count>=192) break;
            int rgb=0,alpha=108,range=192;
            EspStyle style=EspStyle.FILL;
            switch(h.kind()) {
                case STORAGE -> {
                    if(storage==null || !storageEnabled(storage,h.storageType())) continue;
                    rgb=storageRgb(h.storageType(),(int)value(storage,"Chest color",0xFF71E3CF),
                        (int)value(storage,"Barrel color",0xFFFFBD73),(int)value(storage,"Other color",0xFFB698FA));
                    alpha=(int)value(storage,"Opacity",100);
                    range=(int)value(storage,"Range",96);
                    style=(EspStyle)value(storage,"Style",EspStyle.BOTH);
                }
                case SPAWNER -> { if(spawner==null) continue; rgb=0xFFDA73F5; alpha=135; }
                case UTILITY -> { if(utility==null && clusters==null) continue; rgb=0xFFFABD68; }
                case ARTIFICIAL -> { if(clusters==null) continue; rgb=0xFFF0AB61; alpha=72; }
                case ORE -> {
                    if(ore==null) continue;
                    rgb=(int)value(ore,"Color",0xFF76B7FF);
                    range=(int)value(ore,"Range",64);
                    alpha=110;
                }
                case PORTAL -> { if(portal==null) continue; rgb=0xFFAD85F6; alpha=115; }
            }
            if(camera.distanceToSqr(Vec3.atCenterOf(h.pos()))>(double)range*range) continue;
            count++;
            final int fill=(alpha<<24)|(rgb&0xffffff), edge=(0xEE<<24)|(rgb&0xffffff);
            final EspStyle selectedStyle=style;
            pose.pushPose(); pose.translate(h.pos().getX(),h.pos().getY(),h.pos().getZ());
            context.submitNodeCollector().submitCustomGeometry(pose,RenderTypes.debugFilledBox(),(matrix,vertices)-> {
                if(selectedStyle!=EspStyle.OUTLINE) box(matrix,vertices,fill,-0.006f,1.006f,-0.006f,1.006f,-0.006f,1.006f);
                if(selectedStyle!=EspStyle.FILL) outline(matrix,vertices,edge);
            });
            pose.popPose();
        }
        pose.popPose();
    }
    private static void outline(PoseStack.Pose matrix,VertexConsumer vertices,int color) {
        // A wire frame is twelve narrow solid cuboids. It works on 26.2's debugFilledBox
        // pipeline without an unsupported/depth-breaking line render type; cap is 192 markers.
        float lo=-0.012f,hi=1.012f,t=0.014f;
        for(int a=0;a<2;a++) for(int b=0;b<2;b++) {
            float x=a==0?lo:hi-t,y=b==0?lo:hi-t;
            box(matrix,vertices,color,x,x+t,y,y+t,lo,hi);
            box(matrix,vertices,color,x,x+t,lo,hi,y,y+t);
            box(matrix,vertices,color,lo,hi,x,x+t,y,y+t);
        }
    }
    private static final int[][] FACES={{0,3,2,1},{5,6,7,4},{4,7,3,0},{1,2,6,5},{3,7,6,2},{4,0,1,5}};
    private static void box(PoseStack.Pose matrix,VertexConsumer vertices,int color,
                            float x0,float x1,float y0,float y1,float z0,float z1) {
        for(int[] f:FACES) for(int i:f)
            vertices.addVertex(matrix,(i==1||i==2||i==5||i==6)?x1:x0,
                (i==2||i==3||i==6||i==7)?y1:y0,i>=4?z1:z0).setColor(color);
    }
}
