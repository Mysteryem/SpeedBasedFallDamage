package uk.co.mysterymayhem.speedbasedfalldamage;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.event.entity.living.LivingFallEvent;

/**
 * Created by Mysteryem on 11/06/2017.
 */
@SuppressWarnings("unused")
public class ProxyClient extends ProxyCommon {

    private final Minecraft minecraft;

    public ProxyClient() {
        this.minecraft = Minecraft.getMinecraft();
    }

    public void processLegBreakage(LivingFallEvent event, EntityLivingBase legsOwner) {
        if (legsOwner.world.isRemote) {
            float distanceFromMotionY = getDistanceFromMotionY(legsOwner.motionY);

//            FMLLog.info("Distance before: " + event.getDistance() + ", distance after: " + distanceFromMotionY);

            if (minecraft.player == legsOwner) {
                // Send packet to server
                SpeedBasedFallDamage.NETWORK_WRAPPER.sendToServer(new HiMrServerPleaseBreakMyLegsPacket(distanceFromMotionY, event.getDamageMultiplier()));
            }

            event.setDistance(distanceFromMotionY);
        } else {
            super.processLegBreakage(event, legsOwner);
        }
    }
}
