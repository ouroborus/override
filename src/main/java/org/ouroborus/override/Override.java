package org.ouroborus.override;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.UserListOps;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Mod(modid=Override.MODID, name = Override.NAME, version = Override.VERSION, acceptableRemoteVersions = "*")
public class Override {
    public static final String MODID = "override";
    public static final String VERSION = "0.0.0.0";
    public static final String NAME = "AE2 OP Override";

    public static final Logger logger = LogManager.getLogger(Override.MODID);

    private static final String CARD = "item.appliedenergistics2.biometric_card";
    private static final String TERMINAL = "tile.appliedenergistics2.security_station";

    @EventHandler
    public void init(FMLInitializationEvent event) {
        logger.debug("init");
    }

    @EventHandler
    public void serverLoad(FMLServerStartingEvent event) {
        logger.debug("serverLoad");

        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEvent(PlayerInteractEvent event) {
        // Each action triggers several PlayerInteractEvent events, each with different contexts

        World world = event.getWorld();

        // Process on logical server side
        if(world.isRemote) {
            return;
        }

        logger.debug("PlayerInteractEvent: " + world.isRemote);

        // The player side of the interaction has a null face; the block side has a non-null face
        if(event.getFace() == null) {
            return;
        }

        // Only want main hand interactions
        if(event.getHand() != EnumHand.MAIN_HAND) {
            return;
        }

        // Get the held item
        ItemStack itemStack = event.getItemStack();
        logger.debug("getItemStack.getUnlocalizedName: " + itemStack.getUnlocalizedName());

        // Check if player is holding a biometric card
        if(!itemStack.getUnlocalizedName().equals(CARD)) {
            return;
        }

        // Get the targeted block
        TileEntity tileEntity = world.getTileEntity(event.getPos());
        if(tileEntity == null) {
            return;
        }

        logger.debug("getTileEntity.getUnlocalizedName: " + tileEntity.getBlockType().getUnlocalizedName());

        // Check if target is security terminal
        if(!tileEntity.getBlockType().getUnlocalizedName().equals(TERMINAL)) {
            return;
        }

        EntityPlayer entityPlayer = event.getEntityPlayer();
        logger.debug("entityPlayer: " + entityPlayer.getDisplayNameString());

        MinecraftServer server = entityPlayer.getServer();
        boolean isSinglePlayer = server.isSinglePlayer();
        logger.debug("isSinglePlayer: " + isSinglePlayer);

        // Always allow in SP; only allow in MP if op level is high enough
        if(!isSinglePlayer) {
            UserListOps oppedPlayers = server.getPlayerList().getOppedPlayers();
            GameProfile gameProfile = entityPlayer.getGameProfile();
            int permissionLevel = oppedPlayers.getPermissionLevel(gameProfile);
            int opPermissionLevel = server.getOpPermissionLevel();

            logger.debug("permissionLevel: " + permissionLevel);
            logger.debug("opPermissionLevel: " + opPermissionLevel);

            if(permissionLevel < opPermissionLevel) {
                logger.debug("access denied");
                return;
            }
        }

        logger.debug("access granted");

        // At this point we know that AE2 is available,
        // the player has the permissions we want,
        // the player is holding a biometric card,
        // and the player has targeted a security console.

        // Grab all the classes, objects, private fields, and private methods we'll need
        IItemList<IAEItemStack> storedItems;
        Class<?> cAEItemStack;
        Method fromItemStack;
        Method inventoryChanged;
        Object oMEMonitorHandler;
        Field hasChanged;
        Method getStorageList;
        try {
            Class<?> cTileSecurityStation = tileEntity.getClass();

            Field inventory = cTileSecurityStation.getDeclaredField("inventory");
            inventory.setAccessible(true);

            Object oSecurityStationInventory = inventory.get(tileEntity); // oSecurityStationInventory = tileEntity.inventory;

            Method getStoredItems = oSecurityStationInventory.getClass().getMethod("getStoredItems");

            storedItems = (IItemList<IAEItemStack>) getStoredItems.invoke(oSecurityStationInventory); // storedItems = tileEntity.inventory.getStoredItems();

            cAEItemStack = Class.forName("appeng.util.item.AEItemStack");

            fromItemStack = cAEItemStack.getMethod("fromItemStack", ItemStack.class);

            inventoryChanged = cTileSecurityStation.getMethod("inventoryChanged");

            Field securityMonitor = cTileSecurityStation.getDeclaredField("securityMonitor");
            securityMonitor.setAccessible(true);

            oMEMonitorHandler = securityMonitor.get(tileEntity); // oMEMonitorHandler = tileEntity.securityMonitor;
            Class<?> cMEMonitorHandler = oMEMonitorHandler.getClass();

            hasChanged = cMEMonitorHandler.getDeclaredField("hasChanged");
            hasChanged.setAccessible(true);

            getStorageList = cMEMonitorHandler.getMethod("getStorageList");
        } catch ( NoSuchMethodException
                | NoSuchFieldException
                | IllegalAccessException
                | InvocationTargetException
                | ClassNotFoundException
                | ClassCastException e) {
            e.printStackTrace();
            return;
        }

        // If we got this far, we should be good for manipulation.

        // Find the biometric card (if any) whose player matches the one in the held card
        String id = itemStack.getTagCompound().getCompoundTag("profile").getString("Id");
        IAEItemStack target = null;
        for(IAEItemStack stack: storedItems) {
            if(stack.asItemStackRepresentation().getTagCompound().getCompoundTag("profile").getString("Id").equals(id)) {
                target = stack;
                break;
            }
        }

        // Swap the held biometric card with the one (if any) in the security terminal
        try {
            storedItems.add((IAEItemStack) fromItemStack.invoke(cAEItemStack, itemStack)); // tileEntity.storedItems.add(AEItemStack.fromItemStack(itemStack));
        } catch ( IllegalAccessException
                | InvocationTargetException e) {
            e.printStackTrace();
            return;
        }
        if (target == null) {
            entityPlayer.getHeldItemMainhand().setCount(0);
        } else {
            ItemStack pulled = target.createItemStack();
            target.setStackSize(0);
            entityPlayer.setHeldItem(EnumHand.MAIN_HAND, pulled);
        }

        try {
            // Not sure if this is necessary
            inventoryChanged.invoke(tileEntity); // tileEntity.inventoryChanged();

            // Trick cached inventory into updating so that client reflects changes
            hasChanged.setBoolean(oMEMonitorHandler, true); // tileEntity.securityMonitor.hasChanged = true;
            getStorageList.invoke(oMEMonitorHandler); // tileEntity.securityMonitor.getStorageList();
        } catch ( IllegalAccessException
                | InvocationTargetException e) {
            e.printStackTrace();
            return;
        }

    }

}
