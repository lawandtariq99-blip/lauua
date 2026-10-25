package com.lauua.lauuacheats.render;

import com.lauua.lauuacheats.core.Module;
import com.lauua.lauuacheats.core.ModuleManager;
import com.lauua.lauuacheats.scan.ChunkScanner;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

/** Minecraft 26.2 submit-node ESP; only immutable pre-indexed positions are read in the render phase. */
public final class WorldEsp {
    private final ChunkScanner scanner;
    private final ModuleManager modules;
    public WorldEsp(ChunkScanner scanner, ModuleManager modules) { this.scanner=scanner; this.modules=modules; }
    public void register() { LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(this::draw); }
    private boolean enabled(String name) { return modules.find(name).map(Module::enabled).orElse(false); }
    private void draw(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context) {
        var spots=scanner.visible();
        if(spots.isEmpty()) return;
        boolean storage=enabled("StorageESP"), spawner=enabled("SpawnerESP"), clusters=enabled("ClusterFinder");
        if(!storage && !spawner && !clusters) return;
        Vec3 camera=context.levelState().cameraRenderState.pos;
        PoseStack pose=context.poseStack();
        pose.pushPose();
        pose.translate(-camera.x,-camera.y,-camera.z);
        int count=0;
        for(var h:spots) {
            if(count++>=192) break;
            int color=switch(h.kind()) {
                case STORAGE -> storage?0x7752DFCA:0;
                case SPAWNER -> spawner?0x99DA73F5:0;
                case UTILITY -> clusters?0x88FABD68:0;
                case ARTIFICIAL -> clusters?0x66F0AB61:0;
            };
            if(color==0 || camera.distanceToSqr(Vec3.atCenterOf(h.pos()))>192*192) continue;
            double x=h.pos().getX(),y=h.pos().getY(),z=h.pos().getZ();
            pose.pushPose();
            pose.translate(x,y,z);
            context.submitNodeCollector().submitCustomGeometry(pose,RenderTypes.debugFilledBox(),
                (matrix, vertices)->box(matrix,vertices,color));
            pose.popPose();
        }
        pose.popPose();
    }
    private static void box(PoseStack.Pose matrix, VertexConsumer vertices, int color) {
        // Six outward-facing quads. A small inset prevents z-fighting with the vanilla block.
        float a=-0.01f,b=1.01f;
        float[][] p={{a,a,a},{b,a,a},{b,b,a},{a,b,a},{a,a,b},{b,a,b},{b,b,b},{a,b,b}};
        int[][] faces={{0,3,2,1},{5,6,7,4},{4,7,3,0},{1,2,6,5},{3,7,6,2},{4,0,1,5}};
        for(int[] f:faces) for(int i:f) vertices.addVertex(matrix,p[i][0],p[i][1],p[i][2]).setColor(color);
    }
}
