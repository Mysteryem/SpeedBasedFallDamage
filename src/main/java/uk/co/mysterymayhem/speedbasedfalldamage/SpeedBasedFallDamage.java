package uk.co.mysterymayhem.speedbasedfalldamage;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.WeakHashMap;

@Mod(modid = SpeedBasedFallDamage.MODID, version = SpeedBasedFallDamage.VERSION)
public class SpeedBasedFallDamage {
    private static final boolean DEBUG = true;

    public static final String MODID = "speedbasedfalldamage";
    public static final String VERSION = "1.0.0";
    private static final double a = 25d / 98d;
    private static final double b = -98d / 25d;
    private static final double c = Math.log(50d / 49d);
    // Used to restore
    private static final WeakHashMap<EntityPlayerMP, DataStore> PLAYER_DATA_MAP = new WeakHashMap<>();

    /**
     * Here be maths. I don't really remember how it was all calculated and I'm pretty sure some of these comments are
     * wrong, seeing as they don't come to the same answers as what the code is actually using...
     * <p>
     * This is based on two recurrence relations.<br>
     * &emsp;motionY at ticks 0:<br>
     * &emsp;&emsp;motY(0) = 0<br>
     * &emsp;motionY at ticks t+1:<br>
     * &emsp;&emsp;motY(t+1) = 0.98 * (motY(t) - 0.08)<br>
     * &emsp;motY(t) = (((49 / 50) ^ t) - 1) * 98 / 25<br>
     * &emsp;&emsp;&emsp;just put "f(t+1) = 0.98 * (f(t) - 0.08), f(0) = 0" into wolfram alpha<br>
     * <p>
     * &emsp;distance at ticks 0:<br>
     * &emsp;&emsp;d(0) = 0<br>
     * &emsp;distance at ticks 1:<br>
     * &emsp;&emsp;d(1) = motY(0)<br>
     * &emsp;&emsp;&emsp;during each tick, player first moves by motionY, motionY is then changed near the end of the tick<br>
     * &emsp;distance at ticks 2:<br>
     * &emsp;&emsp;d(2) = d(1) + motY(1)<br>
     * &emsp;distance at ticks n+1:<br>
     * &emsp;&emsp;d(t+1) = d(t) + motY(t)<br>
     * &emsp;&emsp;d(t+1) = d(t) + (((49 / 50) ^ t) - 1) * 98 / 25<br>
     * &emsp;&emsp;d(t) = -98 / 25 * (50 * ((49 / 50) ^ t - 1) + t)<br>
     * However, I wrote it down as d(t) = -98 / 25 * (50 * ((49 / 50) ^ (t + 1) - 1) + t + 1).<br>
     * &emsp;Maybe something to do with the fact that network ticks occur after update ticks<br>
     * So we now have distance as a function of ticks, but what we need is distance as a function of motionY<br>
     * <p>
     * Ticks as a function of motionY is t(y) = ln(1 + 25 * y / 98) / ln(49 / 50) based on our earlier formula<br>
     * &emsp;&emsp;d(t(y)) = -98 / 25 * (50 * ((49 / 50) ^ t(y) - 1) + t(y))<br>
     * &emsp;&emsp;d(y) = -98 / 25 * (50 * ((49 / 50) ^ (ln(1 + 25 * y / 98) / ln(49 / 50)) - 1) + (ln(1 + 25 * y / 98) / ln(49 / 50)))<br>
     * &emsp;&emsp;d(y) = 196 − 98 * (50 * (25 * y / 98 + 1) − ((ln(25 * y / 98 + 1)) / (ln(50 / 49)))) / 25<br>
     * &emsp;Rearranging:<br>
     * &emsp;&emsp;d(y) = 196 − 98 * (50 * (25 / 98 * y + 1) − ((ln(25 / 98 * y + 1)) / (ln(50 / 49)))) / 25<br>
     * &emsp;&emsp;part1 = 25 / 98 * y + 1<br>
     * &emsp;&emsp;d(y) = 196 − 98 * (50 * part1 − ((ln(part1)) / (ln(50 / 49)))) / 25<br>
     * &emsp;Rearranging:<br>
     * &emsp;&emsp;d(y) = 196 − 98 / 25 * (50 * part1 − ((ln(part1)) / (ln(50 / 49))))<br>
     * &emsp;Cached calculations:<br>
     * &emsp;&emsp;b = 98 / 25<br>
     * &emsp;&emsp;c = ln(50 / 49)<br>
     * &emsp;&emsp;d(y) = 196 − b * (50 * part1 − ((ln(part1)) / c))<br>
     * &emsp;I don't know how I got to the final result from here, I think there was some trial and error because it<br>
     * &emsp;&emsp;didn't work properly. Either way, you should now have the gist of how it was calculated<br>
     *
     * @param motionY current entity motionY
     * @return The distance the entity has fallen assuming they started at motionY = 0 and fell without any external
     * forces taking affect other than gravity.
     */
    private static double getDistanceFromMotionY(double motionY) {
        // 25d / 98d * motionY + 1
        double part1 = a * motionY + 1;
        // -(196 + (-98d / 25d) * (50 * part1 - Math.log(part1) / Math.log(50d / 49d)))
        // -(196 + (-98d / 25d) * (50 * (25d / 98d * motionY + 1) - Math.log(25d / 98d * motionY + 1) / Math.log(50d / 49d)))
        return -(196 + b * (50 * part1 - Math.log(part1) / c)); //
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            EntityPlayerMP playerMP = (EntityPlayerMP) event.player;
            DataStore dataStore = PLAYER_DATA_MAP.get(playerMP);
            switch (event.phase) {
                case START:
                    if (dataStore == null) {
                        dataStore = new DataStore();
                        PLAYER_DATA_MAP.put(playerMP, dataStore);
                    }
                    dataStore.onGroundAtStartOfPlayerTick = playerMP.onGround;
                    dataStore.motionYAtStartOfPlayerTick = playerMP.motionY;
                    break;
                case END:
                    if (!dataStore.onGroundAtStartOfPlayerTick && playerMP.onGround) {
                        // Player hit the ground during their update tick!
                        playerMP.onGround = false;
//                        FMLLog.info("Restoring motionY to " + dataStore.motionYAtStartOfPlayerTick + " from " + playerMP.motionY);
                        playerMP.motionY = dataStore.motionYAtStartOfPlayerTick;
                    }
                    dataStore.onGroundAtEndOfPlayerTick = playerMP.onGround;
                    break;
            }
        }
    }

    // HIGHEST priority in attempt to update the event's distance before other mods. Other mods can then cancel or
    // modify the distance further at their own discretion
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingFall(LivingFallEvent event) {
        Entity entity = event.getEntity();

        final double storedYMotion;

        if (entity instanceof EntityPlayerMP) {
            storedYMotion = PLAYER_DATA_MAP.get(entity).motionYAtStartOfPlayerTick;
        } else {
            storedYMotion = entity.motionY;
        }

        if (DEBUG) {
            FMLLog.info("Distance: %f, Multiplier: %f, Speed: %f", event.getDistance(), event.getDamageMultiplier(), entity.motionY);
        }

        double distanceFromMotionY = getDistanceFromMotionY(storedYMotion);

        if (DEBUG) {
            FMLLog.info("Distance from stored motionY: %f -> %f\n", storedYMotion, distanceFromMotionY);
        }


        if (distanceFromMotionY < 0) {
            // This happened _once_. I've not seen it happen again.
            distanceFromMotionY = 0;
        }
        event.setDistance((float) distanceFromMotionY);
    }

    // Register the listening methods
    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(SpeedBasedFallDamage.class);
    }

    // Could convert this to a capability
    private static class DataStore {
        // Used to determine if the player hit the ground during the player tick by comparing it to the post-tick value
        boolean onGroundAtStartOfPlayerTick;
        // Used to restore value when received player packet modifies the onGround field
        boolean onGroundAtEndOfPlayerTick;
        // Used as the speed that the player hit the ground
        double motionYAtStartOfPlayerTick;
    }

//    // For testing
//    @SubscribeEvent
//    public static void onRightClickStick(PlayerInteractEvent.RightClickItem event) {
//        ItemStack itemStack = event.getItemStack();
//        if (itemStack != null && itemStack.getItem() == Items.STICK) {
//            EntityPlayer player = event.getEntityPlayer();
//            if (player.isSneaking()) {
//                FMLLog.info("Cutting player motion from " + player.motionY + " to " + 0);
//                player.motionY = 0;
//            } else {
//                FMLLog.info("Increasing player motion from " + player.motionY + " to " + (player.motionY + 1));
//                player.motionY += 1;
//                // Required to stop the server from thinking the player is trying to jump
//                player.onGround = false;
//            }
//        }
//    }
}
