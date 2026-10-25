package com.lauua.lauuacheats.modules;

import com.lauua.lauuacheats.core.*;
import com.lauua.lauuacheats.core.Module;
import com.lauua.lauuacheats.rotation.RotationManager;
import com.lauua.lauuacheats.settings.Setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.util.*;

/** Combat actions are intentionally disabled by default; all packet actions go through RotationManager. */
public final class CombatModules {
    private CombatModules() { }
    public static void register(ModuleManager manager, RotationManager rotation, MacroManager macros) {
        manager.register(new Reach(rotation)); manager.register(new KillAura(rotation));
        manager.register(new AutoTotem()); manager.register(new AutoOffhand()); manager.register(new AutoEat(rotation));
        manager.register(new CrystalAura(rotation)); manager.register(new AnchorAura(rotation));
        manager.register(new AnchorMacro(macros));
    }
    private static final class Reach extends Module {
        private final RotationManager rotations;
        final DoubleSlider distance=add(new DoubleSlider("Distance",3.0,2.0,6.0));
        Reach(RotationManager rotations) { super("Reach","Client action distance limit",Category.COMBAT,-1,false,Tier.C,"Vanilla Mode clamps to 3 blocks"); this.rotations=rotations; }
        double range() { return rotations.clampReach(distance.get()); }
    }
    private static final class KillAura extends Module {
        private final RotationManager rotations;
        final DoubleSlider range=add(new DoubleSlider("Range",3.0,1,6));
        final IntSlider interval=add(new IntSlider("Interval ticks",12,1,40));
        int ticks;
        KillAura(RotationManager r) { super("KillAura","Attack closest non-player hostile mob",Category.COMBAT,-1,false,Tier.D,"Players are excluded"); rotations=r; }
        @Override public void tick() {
            Minecraft mc=Minecraft.getInstance(); if(mc.player==null || mc.level==null || ++ticks%interval.get()!=0) return;
            double radius=rotations.clampReach(range.get());
            mc.level.getEntitiesOfClass(Mob.class,mc.player.getBoundingBox().inflate(radius),Mob::isAlive).stream()
                .min(Comparator.comparingDouble(e->e.distanceToSqr(mc.player))).ifPresent(target -> {
                    float[] angles=angles(mc.player.getEyePosition(),target.getEyePosition());
                    rotations.enqueue(RotationManager.Type.ATTACK,angles[0],angles[1],()-> {
                        if(target.isAlive() && mc.player!=null && target.distanceTo(mc.player)<=radius) mc.gameMode.attack(mc.player,target);
                    });
                });
        }
    }
    private static class AutoTotem extends Module {
        AutoTotem() { super("AutoTotem","Equip a totem in the offhand",Category.COMBAT,-1,false,Tier.B,"Only moves an existing totem"); }
        @Override public void tick() {
            Minecraft mc=Minecraft.getInstance(); if(mc.player==null || mc.gameMode==null || mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;
            equip(mc,Items.TOTEM_OF_UNDYING);
        }
    }
    private static class AutoOffhand extends Module {
        AutoOffhand() { super("AutoOffhand","Equip golden apples in offhand",Category.COMBAT,-1,false,Tier.B,"Does not override an equipped totem"); }
        @Override public void tick() {
            Minecraft mc=Minecraft.getInstance(); if(mc.player==null || mc.gameMode==null || !mc.player.getOffhandItem().isEmpty()) return;
            equip(mc,Items.GOLDEN_APPLE);
        }
    }
    private static void equip(Minecraft mc, Item item) {
        for(int i=0;i<36;i++) if(mc.player.getInventory().getItem(i).is(item)) {
            // Inventory menu: 0..8 hotbar -> 36..44; 9..35 main inventory -> 9..35.
            int slot=i<9?i+36:i;
            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,slot,40,ContainerInput.SWAP,mc.player);
            break;
        }
    }
    private static class AutoEat extends Module {
        final IntSlider threshold=add(new IntSlider("Hunger",12,1,19));
        private final RotationManager rotations;
        AutoEat(RotationManager r) { super("AutoEat","Use held food when hungry",Category.COMBAT,-1,false,Tier.B,"Food must be in main hand"); rotations=r; }
        @Override public void tick() {
            Minecraft mc=Minecraft.getInstance(); if(mc.player==null || mc.gameMode==null) return;
            if(mc.player.getFoodData().getFoodLevel()<=threshold.get() && mc.player.getMainHandItem().has(DataComponents.FOOD))
                rotations.enqueue(RotationManager.Type.USE,mc.player.getYRot(),mc.player.getXRot(),()->mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND));
        }
    }
    private static final class CrystalAura extends Module {
        final DoubleSlider minDamage=add(new DoubleSlider("Minimum target damage",6,1,20));
        final DoubleSlider maxSelf=add(new DoubleSlider("Maximum self damage",4,0,20));
        final BooleanSetting obsidian=add(new BooleanSetting("Obsidian autoplace",false));
        private final RotationManager rotations;
        CrystalAura(RotationManager r) { super("CrystalAura","Target crystals with self-damage failsafe",Category.COMBAT,-1,false,Tier.D,"Conservative self damage; no blind placements"); rotations=r; }
        @Override public void tick() {
            Minecraft mc=Minecraft.getInstance(); if(mc.player==null || mc.level==null || mc.gameMode==null) return;
            var targets=mc.level.getEntitiesOfClass(Mob.class,mc.player.getBoundingBox().inflate(8),Entity::isAlive);
            if(targets.isEmpty()) return;
            LivingEntity target=targets.stream().min(Comparator.comparingDouble(e->e.distanceToSqr(mc.player))).orElse(null);
            if(target==null) return;
            var crystals=mc.level.getEntitiesOfClass(EndCrystal.class,mc.player.getBoundingBox().inflate(5),Entity::isAlive);
            for(EndCrystal c:crystals) if(safe(mc,c.position(),target)) {
                float[] angle=angles(mc.player.getEyePosition(),c.position());
                rotations.enqueue(RotationManager.Type.BREAK,angle[0],angle[1],()-> { if(c.isAlive() && mc.player!=null) mc.gameMode.attack(mc.player,c); });
                return;
            }
            if(!(mc.hitResult instanceof BlockHitResult hit)) return;
            BlockPos pos=hit.getBlockPos(); var block=mc.level.getBlockState(pos).getBlock();
            if(block!=Blocks.OBSIDIAN && block!=Blocks.BEDROCK) {
                if(obsidian.get() && mc.player.getMainHandItem().is(Items.OBSIDIAN)) {
                    rotations.enqueue(RotationManager.Type.PLACE,mc.player.getYRot(),mc.player.getXRot(),()->mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit));
                }
                return;
            }
            Vec3 center=Vec3.atCenterOf(pos.above());
            if(mc.player.getMainHandItem().is(Items.END_CRYSTAL) && safe(mc,center,target))
                rotations.enqueue(RotationManager.Type.PLACE,mc.player.getYRot(),mc.player.getXRot(),()->mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit));
        }
        private boolean safe(Minecraft mc,Vec3 explosion,LivingEntity target) {
            // Vanilla-style distance impact (radius 12 for a crystal). Self uses full exposure
            // (worst case, ignores armor/resistance); target requires clear line of sight and
            // is estimated before mitigation. Avoid lethal actions without an offhand totem.
            double self=rawDamage(explosion,mc.player.position());
            double targetDamage=mc.level.clip(new ClipContext(explosion,target.getEyePosition(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player)).getType()==HitResult.Type.MISS
                ?rawDamage(explosion,target.position()):0;
            return self<=maxSelf.get() && (self<mc.player.getHealth() || mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) && targetDamage>=minDamage.get();
        }
        private static double rawDamage(Vec3 center,Vec3 position) {
            double impact=Math.max(0,1-center.distanceTo(position)/12);
            return (impact*impact+impact)*42+1;
        }
    }
    private static final class AnchorAura extends Module {
        private final RotationManager rotations;
        AnchorAura(RotationManager r) { super("AnchorAura","Charge/detonate targeted anchor outside the Nether",Category.COMBAT,-1,false,Tier.D,"Only works when holding glowstone to charge"); rotations=r; }
        @Override public void tick() {
            Minecraft mc=Minecraft.getInstance(); if(mc.player==null || mc.level==null || mc.level.dimension()==Level.NETHER) return;
            if(!(mc.hitResult instanceof BlockHitResult hit) || !mc.level.getBlockState(hit.getBlockPos()).is(Blocks.RESPAWN_ANCHOR)) return;
            if(mc.player.distanceToSqr(Vec3.atCenterOf(hit.getBlockPos()))>9) return;
            if(mc.player.getMainHandItem().is(Items.GLOWSTONE))
                rotations.enqueue(RotationManager.Type.USE,mc.player.getYRot(),mc.player.getXRot(),()->mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit));
            else if(mc.player.getHealth()>16 || mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING))
                rotations.enqueue(RotationManager.Type.USE,mc.player.getYRot(),mc.player.getXRot(),()->mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit));
        }
    }
    private static final class AnchorMacro extends Module {
        AnchorMacro(MacroManager macros) {
            super("AnchorMacro","Place, charge, detonate, replace (hotbar 0/1/2)",Category.COMBAT,-1,false,Tier.D,"Hold the macro key; aim at the placement face");
            macros.register(new MacroManager.Macro("anchor",-1,List.of(
                new MacroManager.Step(MacroManager.Op.SWITCH_SLOT,0,0,0,"",50),
                new MacroManager.Step(MacroManager.Op.PLACE,0,0,0,"",150),
                new MacroManager.Step(MacroManager.Op.SWITCH_SLOT,1,0,0,"",50),
                new MacroManager.Step(MacroManager.Op.PLACE,0,0,0,"",150),
                new MacroManager.Step(MacroManager.Op.SWITCH_SLOT,2,0,0,"",50),
                new MacroManager.Step(MacroManager.Op.USE,0,0,0,"",200),
                new MacroManager.Step(MacroManager.Op.SWITCH_SLOT,0,0,0,"",50),
                new MacroManager.Step(MacroManager.Op.PLACE,0,0,0,"",200)
            ),1,false));
        }
    }
    private static float[] angles(Vec3 from,Vec3 to) {
        Vec3 v=to.subtract(from); return new float[]{(float)(Math.toDegrees(Math.atan2(v.z,v.x))-90), (float)-Math.toDegrees(Math.atan2(v.y,Math.hypot(v.x,v.z)))};
    }
}
