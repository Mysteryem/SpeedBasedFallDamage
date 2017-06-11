package uk.co.mysterymayhem.speedbasedfalldamage;

import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

@Mod(
        modid = SpeedBasedFallDamage.MODID,
        version = SpeedBasedFallDamage.VERSION,
        acceptedMinecraftVersions = "[1.10.2, 1.12)",
        name = "Speed Based Fall Damage",
        acceptableRemoteVersions = "[1.0.0,1.1)"
)
public class SpeedBasedFallDamage {

    static final String MODID = "speedbasedfalldamage";
    static final String VERSION = "1.0.0";

    static final SimpleNetworkWrapper NETWORK_WRAPPER = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);

    @SidedProxy(clientSide = "uk.co.mysterymayhem.speedbasedfalldamage.ProxyClient", serverSide = "uk.co.mysterymayhem.speedbasedfalldamage.ProxyCommon")
    static ProxyCommon proxy;

    // HIGHEST priority in attempt to update the event's distance before other mods. Other mods can then cancel or
    // modify the distance further at their own discretion
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingFall(LivingFallEvent event) {
        EntityLivingBase entityLiving = event.getEntityLiving();
        proxy.processLegBreakage(event, entityLiving);
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        NETWORK_WRAPPER.registerMessage(new HiMrServerPleaseBreakMyLegsHandler(), HiMrServerPleaseBreakMyLegsPacket.class, 0, Side.SERVER);
    }

    // Register the listening methods
    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(SpeedBasedFallDamage.class);
    }
}
