package com.lauua.lauuacheats.modules;

import com.lauua.lauuacheats.core.Module;
import com.lauua.lauuacheats.core.ModuleManager;
import com.lauua.lauuacheats.hud.HudRegistry;
import com.lauua.lauuacheats.rotation.RotationManager;
import com.lauua.lauuacheats.text.mixin.GuiScreenAccessor;
import net.minecraft.world.phys.HitResult;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.BlockHitResult;
import org.lwjgl.glfw.GLFW;

/** Low-cost optional stage-8 helpers; no world scan or network I/O during HUD extraction. */
public final class Extras {
    private Extras() { }
    public static void register(ModuleManager modules,HudRegistry hud,RotationManager rotation) {
        modules.register(new Module("SessionTimer","Track time since world join",Module.Category.HUD,-1,false,Module.Tier.A,"Resets on world change") {});
        hud.put("Session",5,61,()-> modules.find("SessionTimer").filter(Module::enabled).map(m -> {
            // The time is measured from entrypoint registration, and reset in worldChanged().
            return "Session " + String.format("%02d:%02d:%02d",(System.currentTimeMillis()-sessionStarted)/3600000,(System.currentTimeMillis()-sessionStarted)/60000%60,(System.currentTimeMillis()-sessionStarted)/1000%60);
        }).orElse(""));
        modules.register(new Module("FPS HUD","Display frame rate",Module.Category.HUD,-1,false,Module.Tier.A,"FPS only; ping/TPS not measured") {});
        hud.put("FPS",5,75,()-> modules.find("FPS HUD").filter(Module::enabled).map(m->"FPS " + Minecraft.getInstance().getFps()).orElse(""));
        modules.register(new Module("TargetHUD","Show hovered mob health",Module.Category.HUD,-1,false,Module.Tier.B,"Hovered mobs only") {});
        hud.put("Target",5,89,()-> {
            var mc=Minecraft.getInstance();
            if(!modules.find("TargetHUD").map(Module::enabled).orElse(false) || !(mc.crosshairPickEntity instanceof Mob mob)) return "";
            return mob.getName().getString() + " " + Math.ceil(mob.getHealth()) + " HP";
        });
        modules.register(new Module("TriggerBot","Attack hovered mob at vanilla cooldown",Module.Category.COMBAT,-1,false,Module.Tier.D,"No players; 3-block clamp when Vanilla Mode enabled") {
            @Override public void tick() {
                var mc=Minecraft.getInstance();
                if(mc.player==null || mc.gameMode==null || ((GuiScreenAccessor)mc.gui).lauuacheats$screen()!=null || !(mc.crosshairPickEntity instanceof Mob target)
                    || !target.isAlive() || mc.player.getAttackStrengthScale(0)<0.99
                    || target.distanceTo(mc.player)>rotation.clampReach(3.0)) return;
                rotation.enqueue(RotationManager.Type.ATTACK,mc.player.getYRot(),mc.player.getXRot(),()-> {
                    if(target.isAlive() && mc.player!=null && mc.player.getAttackStrengthScale(0)>=0.99) mc.gameMode.attack(mc.player,target);
                });
            }
        });
        modules.register(new Module("AutoTool","Select fastest hotbar tool when breaking",Module.Category.MISC,-1,false,Module.Tier.B,"Hotbar only; does not restore previous slot") {
            @Override public void tick() {
                var mc=Minecraft.getInstance();
                if(mc.player==null || mc.level==null || ((GuiScreenAccessor)mc.gui).lauuacheats$screen()!=null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType()!=HitResult.Type.BLOCK
                    || GLFW.glfwGetMouseButton(mc.getWindow().handle(),GLFW.GLFW_MOUSE_BUTTON_LEFT)!=GLFW.GLFW_PRESS) return;
                var state=mc.level.getBlockState(hit.getBlockPos());
                int best=mc.player.getInventory().getSelectedSlot();
                float speed=mc.player.getInventory().getItem(best).getDestroySpeed(state);
                for(int i=0;i<9;i++) {
                    float candidate=mc.player.getInventory().getItem(i).getDestroySpeed(state);
                    if(candidate>speed) { speed=candidate; best=i; }
                }
                mc.player.getInventory().setSelectedSlot(best);
            }
        });
    }
    private static long sessionStarted=System.currentTimeMillis();
    public static void resetSession() { sessionStarted=System.currentTimeMillis(); }
}
