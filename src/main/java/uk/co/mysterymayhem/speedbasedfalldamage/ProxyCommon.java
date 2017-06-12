package uk.co.mysterymayhem.speedbasedfalldamage;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingFallEvent;

/**
 * Created by Mysteryem on 11/06/2017.
 */
public class ProxyCommon {
    // Pre-calculatable constants
    private static final double a = 25d / 98d; //As precise as a double can store the result: 0.25510204081632654D;
    private static final double b = -98d / 25d; //-3.92d;
    private static final double c = 49.498316452509154484714331401824; //1 / Math.log(50d / 49d), as precise as a double can store the result: 49.49831645250915D

    private boolean serverSideLegBreakageAllowed;

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
    static float getDistanceFromMotionY(double motionY) {
        // 25d / 98d * motionY + 1
        double part1 = a * motionY + 1;

        // part1 = 0 -> log(part1) = -infinity, part1 < 0 -> log(part1) = NaN, both aren't usable because minecraft
        // converts fall distance to an int as part of a ciel operation
        if (part1 <= 0) {
            // You have to be travelling at more than 3.92 blocks _per tick_ downwards to hit this (78.4 blocks per second)
            // part1 = Double.MIN_VALUE; // smallest double greater than 0
            // the below distance calculation then becomes 144250.23861878135d
            return 144250.23f;
        }

        // -(196 + (-98d / 25d) * (50 * part1 - Math.log(part1) / Math.log(50d / 49d)))
        // -(196 + (-98d / 25d) * (50 * (25d / 98d * motionY + 1) - Math.log(25d / 98d * motionY + 1) / Math.log(50d / 49d)))
        double distance = -(196 + b * (50 * part1 - Math.log(part1) * c));
        if (distance < 0) {
            // This happened _once_ and I have no idea how
            distance = 0;
        }
        return (float) distance;
    }

    void breakSomeLegs(float calculatedDistanceFromSpeed, float damageMultiplier, EntityPlayer legsOwner) {
        legsOwner.onGround = false;
        legsOwner.isAirBorne = true;
        serverSideLegBreakageAllowed = true;
        legsOwner.fall(calculatedDistanceFromSpeed, damageMultiplier);
        serverSideLegBreakageAllowed = false;
    }

    public void processLegBreakage(LivingFallEvent event, EntityLivingBase legsOwner) {
        if (legsOwner instanceof EntityPlayerMP) {
            if (!serverSideLegBreakageAllowed) {
                event.setCanceled(true);
            } else {
//                FMLLog.info("distance " + event.getDistance());
            }
        } else {
            event.setDistance(getDistanceFromMotionY(legsOwner.motionY));
        }
    }
}
