package com.lauua.lauuacheats.scan;

import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Explicit log action; never transmit coordinates externally. */
public final class CoordLogger {
    public record Point(String name,int x,int y,int z,String world,String time) { }
    private final Deque<Point> points=new ArrayDeque<>();
    private final Path dir=FabricLoader.getInstance().getConfigDir().resolve("lauuacheats/waypoints");
    public List<Point> all() { return List.copyOf(points); }
    public void log(Minecraft mc,String world,String name) throws IOException {
        if(mc.player==null) return;
        var pos=mc.player.blockPosition();
        Point p=new Point(name,pos.getX(),pos.getY(),pos.getZ(),world,Instant.now().toString());
        if(points.size()==256) points.removeFirst(); points.addLast(p);
        mc.player.displayClientMessage(Component.literal("[Lauua] " + name + " " + pos.toShortString()),false);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("coordinates.txt"),p.time()+" "+p.world()+" "+p.name()+" "+p.x()+" "+p.y()+" "+p.z()+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
    public void export() throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("waypoints.json"),new GsonBuilder().setPrettyPrinting().create().toJson(points),StandardCharsets.UTF_8);
        StringBuilder csv=new StringBuilder("name,x,y,z,world,time\n");
        StringBuilder xaero=new StringBuilder("# Xaero Waypoint Export\n# name:initials:x:y:z:color:disabled:type:set:rotate_on_tp:tp_yaw:visibility_type\n");
        for(Point p:points) {
            csv.append(String.join(",",quote(p.name()),""+p.x(),""+p.y(),""+p.z(),quote(p.world()),quote(p.time()))).append('\n');
            String safe=p.name().replace(':','_').replace('\n',' ');
            xaero.append("waypoint:").append(safe).append(":L:").append(p.x()).append(':').append(p.y()).append(':').append(p.z()).append(":6:false:0:gui.xaero_default:false:0:0\n");
        }
        Files.writeString(dir.resolve("waypoints.csv"),csv,StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("waypoints-xaero.txt"),xaero,StandardCharsets.UTF_8);
    }
    private static String quote(String s) { return "\""+s.replace("\"","\"\"")+"\""; }
}
