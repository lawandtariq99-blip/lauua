package com.lauua.lauuacheats.core;

import com.lauua.lauuacheats.rotation.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Main-thread timed sequence runner. No sleeps or work on the render thread. */
public final class MacroManager {
    public enum Op { PLACE, BREAK, USE, SWITCH_SLOT, ATTACK, LOOK_ANGLE, CHAT, WAIT_MS }
    public record Step(Op op, int value, float yaw, float pitch, String message, long delayMs) {
        public Step { if (delayMs < 0 || delayMs > 60_000) throw new IllegalArgumentException("delay out of range"); }
    }
    public record Macro(String name, int key, List<Step> steps, int loops, boolean abortOnRelease) {
        public Macro { steps=List.copyOf(steps); if (steps.size()>128 || loops<1 || loops>100) throw new IllegalArgumentException("macro limit exceeded"); }
    }
    private final Map<String, Macro> macros=new LinkedHashMap<>();
    private final RotationManager rotations;
    private Macro running;
    private int index, loopsLeft;
    private long nextAt;
    public MacroManager(RotationManager rotations) { this.rotations=rotations; }
    public void register(Macro macro) { if (macros.size()>=32) throw new IllegalStateException("Too many macros"); macros.put(macro.name(),macro); }
    public Collection<Macro> all() { return Collections.unmodifiableCollection(macros.values()); }
    public void start(String name) { Macro m=macros.get(name); if (m!=null && !m.steps().isEmpty()) { running=m; index=0; loopsLeft=m.loops(); nextAt=0; } }
    public void abort() { running=null; rotations.clear(); }
    public void tick(Minecraft mc) {
        if (mc.player==null || mc.level==null) { abort(); return; }
        if (running==null || System.currentTimeMillis()<nextAt) return;
        if (running.abortOnRelease() && running.key()>=0 && GLFW.glfwGetKey(mc.getWindow().getWindow(),running.key())!=GLFW.GLFW_PRESS) { abort(); return; }
        Step step=running.steps().get(index++);
        execute(mc,step);
        nextAt=System.currentTimeMillis()+step.delayMs()+ (step.op()==Op.WAIT_MS?Math.max(0,step.value()):0);
        if(index>=running.steps().size()) { index=0; if(--loopsLeft==0) running=null; }
    }
    private void execute(Minecraft mc, Step s) {
        switch (s.op()) {
            case SWITCH_SLOT -> { if (s.value()>=0 && s.value()<9) mc.player.getInventory().setSelectedSlot(s.value()); }
            case CHAT -> { if (s.message()!=null && s.message().length()<=256) mc.player.connection.sendChat(s.message()); }
            case LOOK_ANGLE -> rotations.enqueue(RotationManager.Type.LOOK,s.yaw(),s.pitch(),()->{});
            case USE -> rotations.enqueue(RotationManager.Type.USE,s.yaw(),s.pitch(),()->mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND));
            case PLACE -> {
                HitResult hit=mc.hitResult;
                if(hit instanceof BlockHitResult block) rotations.enqueue(RotationManager.Type.PLACE,s.yaw(),s.pitch(),()->mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,block));
            }
            case BREAK -> {
                HitResult hit=mc.hitResult;
                if(hit instanceof BlockHitResult block) rotations.enqueue(RotationManager.Type.BREAK,s.yaw(),s.pitch(),()->mc.gameMode.startDestroyBlock(block.getBlockPos(),block.getDirection()));
            }
            case ATTACK -> { if(mc.crosshairPickEntity!=null) rotations.enqueue(RotationManager.Type.ATTACK,s.yaw(),s.pitch(),()->mc.gameMode.attack(mc.player,mc.crosshairPickEntity)); }
            case WAIT_MS -> { }
        }
    }
}
