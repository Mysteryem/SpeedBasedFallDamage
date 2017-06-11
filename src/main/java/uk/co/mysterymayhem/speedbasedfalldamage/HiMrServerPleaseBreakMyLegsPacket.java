package uk.co.mysterymayhem.speedbasedfalldamage;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * Created by Mysteryem on 11/06/2017.
 */
public class HiMrServerPleaseBreakMyLegsPacket implements IMessage {
    float calculatedDistanceFromVelocity;
    float damageMultiplier;

    @SuppressWarnings("unused")
    public HiMrServerPleaseBreakMyLegsPacket() {
    }

    HiMrServerPleaseBreakMyLegsPacket(float distance, float damageMultiplier) {
        this.calculatedDistanceFromVelocity = distance;
        this.damageMultiplier = damageMultiplier;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        calculatedDistanceFromVelocity = buf.readFloat();
        damageMultiplier = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeFloat(calculatedDistanceFromVelocity);
        buf.writeFloat(damageMultiplier);
    }
}
