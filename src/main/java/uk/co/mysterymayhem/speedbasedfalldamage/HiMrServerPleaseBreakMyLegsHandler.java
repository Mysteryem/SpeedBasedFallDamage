package uk.co.mysterymayhem.speedbasedfalldamage;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Created by Mysteryem on 11/06/2017.
 */
public class HiMrServerPleaseBreakMyLegsHandler implements IMessageHandler<HiMrServerPleaseBreakMyLegsPacket, IMessage> {
    @Override
    public IMessage onMessage(HiMrServerPleaseBreakMyLegsPacket message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(
                () -> SpeedBasedFallDamage.proxy.breakSomeLegs(message.calculatedDistanceFromVelocity, message.damageMultiplier, player)
        );
        return null;
    }
}
