package tconstruct.library.utils;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;

import org.apache.logging.log4j.Logger;

import java.util.LinkedList;
import java.util.List;

import tconstruct.library.TinkerRegistry;
import tconstruct.library.Util;
import tconstruct.library.tinkering.Material;
import tconstruct.library.tinkering.TinkersItem;
import tconstruct.library.tinkering.materials.ToolMaterialStats;
import tconstruct.library.tinkering.modifiers.IModifier;
import tconstruct.library.tinkering.modifiers.ModifierNBT;
import tconstruct.library.tinkering.modifiers.RecipeMatch;
import tconstruct.library.tinkering.traits.ITrait;

public final class ToolBuilder {

  private static Logger log = Util.getLogger("ToolBuilder");

  private ToolBuilder() {
  }

  /**
   * Adds the trait to the tag, taking max-count and already existing traits into account.
   * @param traitsTag The trait tag on the tool.
   * @param trait The trait to add.
   * @param color The color used on the tooltip. Will not be used if the trait already exists on the tool.
   */
  public static void addTrait(NBTTagCompound traitsTag, ITrait trait, EnumChatFormatting color) {
    // only registered traits allowed
    if (TinkerRegistry.getTrait(trait.getIdentifier()) == null) {
      log.error("Trying to apply unregistered Trait {}", trait.getIdentifier());
      return;
    }

    // find out if the trait already exists or obtain the last tag so we can add a new one
    ModifierNBT data = null;
    int i;
    for (i = 0; traitsTag.hasKey(String.valueOf(i)); i++) {
      ModifierNBT oldData = ModifierNBT.read(traitsTag, String.valueOf(i));
      if (trait.getIdentifier().equals(oldData.identifier)) {
        data = oldData;
        break;
      }
    }

    // trait is not present yet
    if(data == null) {
      data = new ModifierNBT(String.valueOf(i));
      data.color = color;
      data.level = 0;
      data.identifier = trait.getIdentifier();
    }

    // increase the count if possible
    if(data.level < trait.getMaxCount()) {
      data.level++;
    }

    // write the data
    data.write(traitsTag);
  }

  public static ItemStack tryModifyTool(ItemStack[] stacks, ItemStack toolStack, boolean removeItems) {
    ItemStack copy = toolStack.copy();
    boolean appliedModifier = false;

    // obtain a working copy of the items if the originals shouldn't be modified
    if (!removeItems) {
      ItemStack[] stacksCopy = new ItemStack[stacks.length];
      for (int i = 0; i < stacks.length; i++) {
        if (stacks[i] != null) {
          stacksCopy[i] = stacks[i].copy();
        }
      }

      stacks = stacksCopy;
    }

    for (IModifier modifier : TinkerRegistry.getAllModifiers()) {
      RecipeMatch.Match match;
      do {
         match = modifier.matches(stacks);
        // found a modifier that is applicable
        if (match != null) {
          // but can it be applied?
          if (modifier.canApply(copy)) {
            modifier.apply(copy);

            RecipeMatch.removeMatch(stacks, match);

            appliedModifier = true;
          } else {
            // materials would allow another application, but modifier doesn't
            break;
          }
        }
      } while (match != null);
    }

    if (appliedModifier) {
      return copy;
    }

    return null;
  }

  /**
   * Rebuilds a tool from its raw data, material info and applied modifiers
   *
   * @param rootNBT The root NBT tag compound of the tool to to rebuild. The NBT will be modified, overwriting old
   *                data.
   */
  public static void rebuildTool(NBTTagCompound rootNBT, TinkersItem tinkersItem) {
    NBTTagCompound baseTag = rootNBT.getCompoundTag(Tags.BASE_DATA);
    // no data present
    if (baseTag == null) {
      return;
    }

    // Recalculate tool base stats from material stats
    NBTTagCompound materialTag = TagUtil.getTagSafe(baseTag, Tags.BASE_MATERIALS);
    List<Material> materials = new LinkedList<>();
    int index = 0;
    while (materialTag.hasKey(String.valueOf(index))) {
      // load the material from the data
      String identifier = materialTag.getString(String.valueOf(index));
      // this will return Material.UNKNOWN if it doesn't exist (anymore)
      Material mat = TinkerRegistry.getMaterial(identifier);
      materials.add(mat);
      index++;
    }

    NBTTagCompound toolTag = tinkersItem.buildTag(materials);
    rootNBT.setTag(Tags.TOOL_DATA, toolTag);

    NBTTagCompound traitTag = tinkersItem.buildTraits(materials);
    rootNBT.setTag(Tags.TOOL_TRAITS, traitTag);

    // reapply modifiers
    NBTTagCompound modifiers = TagUtil.getTagSafe(baseTag, Tags.BASE_MODIFIERS);
    NBTTagCompound modifiersTag = TagUtil.getTagSafe(rootNBT, Tags.TOOL_MODIFIERS);
    index = 0;
    while (modifiers.hasKey(String.valueOf(index))) {
      String identifier = modifiers.getString(String.valueOf(index));
      index++;
      IModifier modifier = TinkerRegistry.getModifier(identifier);
      if (modifier == null) {
        log.debug("Missing modifier: {}", identifier);
        continue;
      }

      modifier.applyEffect(rootNBT, modifiersTag);
    }
  }

  /**
   * A simple Tool consists of a head and an toolrod. Head determines primary stats, toolrod multiplies primary stats.
   */
  public static NBTTagCompound buildSimpleTool(Material headMaterial, Material handleMaterial,
                                               Material... accessoriesMaterials) {
    NBTTagCompound result;
    ToolMaterialStats headStats = headMaterial.getStats(ToolMaterialStats.TYPE);
    ToolMaterialStats handleStats = handleMaterial.getStats(ToolMaterialStats.TYPE);

    // get the start values from the head
    result = calculateHeadParts(headStats);
    // add the accessories
    for (Material material : accessoriesMaterials) {
      ToolMaterialStats accessoryStats = material.getStats(ToolMaterialStats.TYPE);
      calculateAccessoryParts(result, accessoryStats);
    }

    // multiply with the handles
    calculateHandleParts(result, handleStats);

    // and don't forget the harvest level
    calculateHarvestLevel(result, headStats);

    result.setInteger(Tags.FREE_MODIFIERS, 3);

    return result;
  }

  /**
   * Takes an arbitrary amount of ToolMaterialStats and creates a tag with the mean of their stats.
   *
   * @return The resulting TagCompound
   */
  public static NBTTagCompound calculateHeadParts(ToolMaterialStats... stats) {
    int durability = 0;
    float attack = 0f;
    float speed = 0f;

    // sum up stats
    for (ToolMaterialStats stat : stats) {
      durability += stat.durability;
      attack += stat.attack;
      speed += stat.miningspeed;
    }

    // take mean
    durability /= stats.length;
    attack /= (float) stats.length;
    speed /= (float) stats.length;

    // create output tag
    NBTTagCompound tag = new NBTTagCompound();
    tag.setInteger(Tags.DURABILITY, durability);
    tag.setFloat(Tags.ATTACK, attack);
    tag.setFloat(Tags.MININGSPEED, speed);

    return tag;
  }

  /**
   * Adds the durability of the given materials to the tag.
   */
  public static void calculateAccessoryParts(NBTTagCompound baseTag, ToolMaterialStats... stats) {
    int durability = baseTag.getInteger(Tags.DURABILITY);

    // sum up stats
    for (ToolMaterialStats stat : stats) {
      durability += stat.durability;
    }

    // set value
    baseTag.setInteger(Tags.DURABILITY, durability);
  }

  /**
   * Takes an arbitrary amount of ToolMaterialStats and multiplies the durability in the basetag with the average
   */
  public static void calculateHandleParts(NBTTagCompound baseTag, ToolMaterialStats... stats) {
    int count = 0;
    float multiplier = 0;

    // sum up stats
    for (ToolMaterialStats stat : stats) {
      multiplier += stat.durabilityModifier;
      count++;
    }

    if (count > 0) {
      // calculate the multiplier from the summed up stats
      multiplier *= (0.5 + count * 0.5);
      multiplier /= count;

      int durability = baseTag.getInteger(Tags.DURABILITY);
      durability *= multiplier;
      baseTag.setInteger(Tags.DURABILITY, durability);
    }
  }

  /**
   * Choses the highest harvestlevel of the given materials and sets it in the tag.
   */
  public static void calculateHarvestLevel(NBTTagCompound baseTag, ToolMaterialStats... stats) {
    int harvestLevel = 0;

    // get max
    for (ToolMaterialStats stat : stats) {
      if (stat.harvestLevel > harvestLevel) {
        harvestLevel = stat.harvestLevel;
      }
    }

    baseTag.setInteger(Tags.HARVESTLEVEL, harvestLevel);
  }
}