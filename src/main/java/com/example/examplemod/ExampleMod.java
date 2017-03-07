package com.example.examplemod;

import com.google.common.base.Throwables;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockWall;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.WeakHashMap;

@Mod(modid = ExampleMod.MODID, version = ExampleMod.VERSION)
public class ExampleMod {
    public static final String MODID = "examplemod";
    public static final String VERSION = "1.0";
    private static final double a = 25d / 98d;
    private static final double b = -98d / 25d;
    private static final double c = Math.log(50d / 49d);
    private static WeakHashMap<Entity, Double> entityLastTickDownMotion = new WeakHashMap<>();
    private double motionYSave;
    private boolean wasntOnGroundAtStart = false;

    private static final MethodHandle updateFallState_EntityPlayerMP_Super;

    static {
        try {
            Field IMPL_LOOKUP = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            IMPL_LOOKUP.setAccessible(true);
            MethodHandles.Lookup TRUSTED_LOOKUP = (MethodHandles.Lookup)IMPL_LOOKUP.get(null);
            MethodHandle superUpdateFallState;
            try {
                superUpdateFallState = TRUSTED_LOOKUP.findSpecial(EntityPlayerMP.class.getSuperclass(), "func_184231_a", MethodType.methodType(void.class, double.class, boolean.class, IBlockState.class, BlockPos.class), EntityPlayerMP.class);
            } catch (Throwable e) {
                superUpdateFallState = TRUSTED_LOOKUP.findSpecial(EntityPlayerMP.class.getSuperclass(), "updateFallState", MethodType.methodType(void.class, double.class, boolean.class, IBlockState.class, BlockPos.class), EntityPlayerMP.class);
            }
            updateFallState_EntityPlayerMP_Super = superUpdateFallState;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static double getDistanceFromMotionY(double motionY) {
        double part1 = a * motionY + 1;
        return -(196 + b * (50 * part1 - Math.log(part1) / c));
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingFall(LivingFallEvent event) {
        Entity entity = event.getEntity();
        if (!internalEventFiring && event.getEntity() instanceof EntityPlayerMP) {
            event.setCanceled(true);
            return;
        }
        if (!entity.world.isRemote) {
            Double yMotionFromMap = entityLastTickDownMotion.get(entity);
            if (yMotionFromMap == null) {
                FMLLog.warning("Missing stored y motion! Setting fall distance to zero.");
                event.setDistance(0);
                return;
            }
            double storedYMotion = yMotionFromMap;
            FMLLog.info("Distance: %f, Multiplier: %f, Speed: %f", event.getDistance(), event.getDamageMultiplier(), storedYMotion);

            double distanceFromMotionY = getDistanceFromMotionY(storedYMotion);
            FMLLog.info("Distance from stored motionY: %f -> %f", storedYMotion, distanceFromMotionY);
            if (distanceFromMotionY < 0) {
                // This happened _once_. I've not seen it happen again.
                distanceFromMotionY = 0;
            }
            event.setDistance((float) distanceFromMotionY);
            entityLastTickDownMotion.remove(entity);
        }
    }

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        Entity entity = event.getEntity();
        // Players are a special case as their falling is processed through networkticks
        if (!entity.world.isRemote && !(entity instanceof EntityPlayer)) {
            entityLastTickDownMotion.put(entity, entity.motionY);
        }
    }

    private static boolean internalEventFiring = false;
    private static final WeakHashMap<EntityPlayer, Boolean> LAST_TICK_END_GROUND_STATE = new WeakHashMap<>();



    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        EntityPlayer player = event.player;
        if (!(player instanceof EntityPlayerMP)) {
            return;
        }
        switch (event.phase) {
            case START:
                Boolean aBoolean = LAST_TICK_END_GROUND_STATE.get(player);
                if (aBoolean != null) {
                    if (!aBoolean && player.onGround) {
                        LAST_TICK_END_GROUND_STATE.remove(player);
                        entityLastTickDownMotion.put(player, motionYSave);
                        internalEventFiring = true;
                        manualUpdateFallState((EntityPlayerMP)player);
                        internalEventFiring = false;
                    }
                }

                if (!player.onGround) {
                    motionYSave = player.motionY;
                    // As a fallback I guess in-case something unexpected happens
//                    entityLastTickDownMotion.put(player, motionYSave);
                    wasntOnGroundAtStart = true;
                }
                break;
            case END:
                if (wasntOnGroundAtStart) {
                    if (player.onGround) {
                        // We hit the ground!
                        entityLastTickDownMotion.put(player, motionYSave);
                        internalEventFiring = true;
                        manualUpdateFallState((EntityPlayerMP)player);
                        internalEventFiring = false;
                        LAST_TICK_END_GROUND_STATE.remove(player);
                    } else {
                        LAST_TICK_END_GROUND_STATE.put(player, false);
//                        entityLastTickDownMotion.put(player, motionYSave);
                        // Sometimes the client tells the server it hit the ground before the server thinks so
                        // Just got to guess
//                        entityLastTickDownMotion.put(player, (player.motionY - 0.08) * 0.98);
                    }
                    wasntOnGroundAtStart = false;
                }
                break;
        }
    }

    public void onServerTickEnd(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            //iterate through player entities here

        }
    }

    private static void manualUpdateFallState(EntityPlayerMP playerMP) {
        int j6 = MathHelper.floor(playerMP.posX);
        int i1 = MathHelper.floor(playerMP.posY - 0.20000000298023224D);
        int k6 = MathHelper.floor(playerMP.posZ);
        BlockPos blockpos = new BlockPos(j6, i1, k6);
        IBlockState iblockstate = playerMP.world.getBlockState(blockpos);

        if (iblockstate.getMaterial() == Material.AIR)
        {
            BlockPos blockpos1 = blockpos.down();
            IBlockState iblockstate1 = playerMP.world.getBlockState(blockpos1);
            Block block1 = iblockstate1.getBlock();

            if (block1 instanceof BlockFence || block1 instanceof BlockWall || block1 instanceof BlockFenceGate)
            {
                iblockstate = iblockstate1;
                blockpos = blockpos1;
            }
        }

        try {
            updateFallState_EntityPlayerMP_Super.invokeExact(playerMP, 0d, playerMP.onGround, iblockstate, blockpos);
        } catch (Throwable throwable) {
            throw Throwables.propagate(throwable);
        }
//        playerMP.updateFallState(0, playerMP.onGround, iblockstate, blockpos);
    }
}
