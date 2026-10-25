package com.lauua.lauuacheats.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Only main-thread callers may queue packet actions. No action is sent ahead of its rotation. */
public final class RotationManager {
    public enum Mode { INSTANT, LINEAR, SMOOTHED }
    public boolean vanillaMode = true;
    public Mode mode = Mode.INSTANT;
    public int placeDelayMs = 100, breakDelayMs = 100, perTickCap = 2, jitterMs = 0;
    private final Deque<Action> pending = new ArrayDeque<>();
    private long lastPlace, lastBreak;
    public enum Type { PLACE, BREAK, USE, ATTACK, LOOK }
    private record Action(Type type, float yaw, float pitch, Runnable packet) { }
    public void enqueue(Type type, float yaw, float pitch, Runnable packet) {
        if (pending.size() < 64) pending.addLast(new Action(type,yaw,pitch,packet));
    }
    public void clear() { pending.clear(); }
    public double clampReach(double requested) { return vanillaMode ? Math.min(requested, 3.0) : requested; }
    public void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { clear(); return; }
        int count=0;
        while (count < Math.max(1,Math.min(20,perTickCap)) && !pending.isEmpty()) {
            Action a=pending.peek(); long now=System.currentTimeMillis();
            int delay=a.type==Type.PLACE?placeDelayMs:a.type==Type.BREAK?breakDelayMs:0;
            long last=a.type==Type.PLACE?lastPlace:a.type==Type.BREAK?lastBreak:0;
            if (now-last < delay) break;
            pending.remove();
            // Server processes packets in send order: send a rotation packet FIRST, then the
            // queued action packet in the SAME tick. Never perform the action on an earlier tick.
            float yaw=a.yaw, pitch=a.pitch;
            if (mode != Mode.INSTANT && !vanillaMode) {
                float factor=mode==Mode.LINEAR?0.5f:0.25f;
                yaw=mc.player.getYRot()+Math.clamp(wrap(yaw-mc.player.getYRot()),-45f,45f)*factor;
                pitch=mc.player.getXRot()+Math.clamp(pitch-mc.player.getXRot(),-30f,30f)*factor;
            }
            pitch=Math.clamp(pitch,-90f,90f);
            mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(yaw,pitch,mc.player.onGround(),mc.player.horizontalCollision));
            mc.player.setYRot(yaw); mc.player.setXRot(pitch);
            a.packet.run();
            if(a.type==Type.PLACE) lastPlace=now+randomJitter();
            if(a.type==Type.BREAK) lastBreak=now+randomJitter();
            count++;
        }
    }
    private int randomJitter() { int j=Math.clamp(jitterMs,0,1000); return j==0?0:ThreadLocalRandom.current().nextInt(-j,j+1); }
    private static float wrap(float value) { return (value+180f)%360f<0?(value+180f)%360f+180f:(value+180f)%360f-180f; }
}
