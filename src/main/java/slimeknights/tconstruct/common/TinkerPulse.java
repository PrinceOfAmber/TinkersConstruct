package slimeknights.tconstruct.common;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.OreDictionary;

import java.util.Locale;

import slimeknights.mantle.block.EnumBlock;
import slimeknights.mantle.item.ItemBlockMeta;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.Util;
import slimeknights.tconstruct.library.tools.ToolPart;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.world.TinkerWorld;

/**
 * Just a small helper class that provides some function for cleaner Pulses.
 *
 * Items should be registered during PreInit
 *
 * Models should be registered during Init
 */
// MANTLE
public abstract class TinkerPulse {

  protected static boolean isToolsLoaded() {
    return TConstruct.pulseManager.isPulseLoaded(TinkerTools.PulseId);
  }

  protected static boolean isSmelteryLoaded() {
    return TConstruct.pulseManager.isPulseLoaded(TinkerSmeltery.PulseId);
  }

  protected static boolean isWorldLoaded() {
    return TConstruct.pulseManager.isPulseLoaded(TinkerWorld.PulseId);
  }

  /**
   * Sets the correct unlocalized name and registers the item.
   */
  protected static <T extends Item> T registerItem(T item, String unlocName) {
    if(!unlocName.equals(unlocName.toLowerCase(Locale.US))) {
      throw new IllegalArgumentException(String.format("Unlocalized names need to be all lowercase! Item: %s", unlocName));
    }

    item.setUnlocalizedName(Util.prefix(unlocName));
    GameRegistry.registerItem(item, unlocName);
    return item;
  }

  protected static <T extends Block> T registerBlock(T block, String unlocName) {
    block.setUnlocalizedName(Util.prefix(unlocName));
    GameRegistry.registerBlock(block, unlocName);
    return block;
  }

  protected static <T extends EnumBlock<?>> T registerEnumBlock(T block, String unlocName) {
    registerBlock(block, ItemBlockMeta.class, unlocName);
    ItemBlockMeta.setMappingProperty(block, block.prop);
    return block;
  }

  protected static <T extends Block> T registerBlock(T block,
                                                     Class<? extends ItemBlock> itemBlockClazz,
                                                     String unlocName, Object... itemCtorArgs) {
    if(!unlocName.equals(unlocName.toLowerCase(Locale.US))) {
      throw new IllegalArgumentException(String.format("Unlocalized names need to be all lowercase! Block: %s", unlocName));
    }

    block.setUnlocalizedName(Util.prefix(unlocName));
    GameRegistry.registerBlock(block, itemBlockClazz, unlocName, itemCtorArgs);
    return block;
  }

  protected static void registerTE(Class<? extends TileEntity> teClazz, String name) {
    if(!name.equals(name.toLowerCase(Locale.US))) {
      throw new IllegalArgumentException(String.format("Unlocalized names need to be all lowercase! TE: %s", name));
    }

    GameRegistry.registerTileEntity(teClazz, Util.prefix(name));
  }
}
