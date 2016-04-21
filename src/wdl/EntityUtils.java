package wdl;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.client.Minecraft;
import net.minecraft.crash.CrashReport;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityHanging;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.item.EntityEnderEye;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.item.EntityExpBottle;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityFireworkRocket;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.item.EntityPainting;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.IAnimals;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityEgg;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.entity.projectile.EntitySmallFireball;
import net.minecraft.entity.projectile.EntitySnowball;
import wdl.api.IEntityAdder;
import wdl.api.ISpecialEntityHandler;
import wdl.api.WDLApi;
import wdl.api.WDLApi.ModInfo;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

/**
 * Provides utility functions for recognising entities.
 */
public class EntityUtils {
	private static final Logger logger = LogManager.getLogger();
	
	/**
	 * Reference to the {@link EntityList#stringToClassMapping} field.
	 */
	public static final Map<String, Class<?>> stringToClassMapping;
	/**
	 * Reference to the {@link EntityList#classToStringMapping} field.
	 */
	public static final Map<Class<?>, String> classToStringMapping;
	
	/*
	 * Add the vanilla entities to entitiesByGroup and steal the 
	 * classToStringMapping and stringToClassMapping fields.
	 */
	static {
		try {
			Map<String, Class<?>> mappingSTC = null;
			Map<Class<?>, String> mappingCTS = null;
			
			//Attempt to steal the 'stringToClassMapping' field. 
			for (Field field : EntityList.class.getDeclaredFields()) {
				if (field.getType().equals(Map.class)) {
					field.setAccessible(true);
					Map<?, ?> m = (Map<?, ?>)field.get(null);
					
					Map.Entry<?, ?> e = (Map.Entry<?, ?>)m.entrySet().toArray()[0];
					if (e.getKey() instanceof String && 
							e.getValue() instanceof Class<?>) {
						//Temp is used here to allow @SuppressWarnings.
						@SuppressWarnings("unchecked")
						Map<String, Class<?>> temp = (Map<String, Class<?>>) m; 
						mappingSTC = temp;
					}
					if (e.getKey() instanceof Class<?> &&
							e.getValue() instanceof String) {
						//Temp is used here to allow @SuppressWarnings.
						@SuppressWarnings("unchecked")
						Map<Class<?>, String> temp = (Map<Class<?>, String>)m;
						mappingCTS = temp;
					}
				}
			}
			
			if (mappingSTC == null) {
				throw new Exception("WDL: Failed to find stringToClassMapping!");
			}
			if (mappingCTS == null) {
				throw new Exception("WDL: Failed to find classToStringMapping!");
			}
			
			stringToClassMapping = mappingSTC;
			classToStringMapping = mappingCTS;
			
			List<String> passiveEntities = new ArrayList<String>();
			List<String> hostileEntities = new ArrayList<String>();
			List<String> otherEntities = new ArrayList<String>();
			
			//Now build an actual list.
			for (Map.Entry<String, Class<?>> e : 
					EntityUtils.stringToClassMapping.entrySet()) {
				String entity = e.getKey();
				Class<?> c = e.getValue();
				
				if (Modifier.isAbstract(c.getModifiers())) {
					//Don't include abstract classes.
					continue;
				}
				
				if (IMob.class.isAssignableFrom(c)) {
					hostileEntities.add(entity);
				} else if (IAnimals.class.isAssignableFrom(c)) {
					passiveEntities.add(entity);
				} else {
					otherEntities.add(entity);
				}
			}
			
			Collections.sort(hostileEntities, Collator.getInstance());
			Collections.sort(passiveEntities, Collator.getInstance());
			Collections.sort(otherEntities, Collator.getInstance());
			
			entitiesByGroup.putAll("Hostile", hostileEntities);
			entitiesByGroup.putAll("Passive", passiveEntities);
			entitiesByGroup.putAll("Other", otherEntities);
		} catch (Exception e) {
			Minecraft.getMinecraft().crashed(
					new CrashReport("World Downloader Mod: failed to set up entity ranges!",
							e));
			//Will never happen.
			throw new Error("World Downloader Mod: failed to set up entity ranges!", e);
		}
	}
	
	/**
	 * Gets a list of all types of entities.
	 */
	public static List<String> getEntityTypes() {
		List<String> returned = new ArrayList<String>();
		
		for (Map.Entry<String, Class<?>> e : stringToClassMapping.entrySet()) {
			if (Modifier.isAbstract(e.getValue().getModifiers())) {
				continue;
			}
			
			returned.add(e.getKey());
		}
		
		for (ModInfo<ISpecialEntityHandler> info : WDLApi
				.getImplementingExtensions(ISpecialEntityHandler.class)) {
			returned.addAll(info.mod.getSpecialEntities().values());
		}
		
		return returned;
	}
	
	/**
	 * Gets the entity tracking range used by vanilla minecraft.
	 * <br/>
	 * Proper tracking ranges can be found in EntityTracker#trackEntity
	 * (the one that takes an Entity as a paremeter) -- it's the 2nd arg
	 * given to addEntityToTracker.
	 * 
	 * @param type The vanilla minecraft entity string.
	 * @return 
	 */
	public static int getDefaultEntityRange(String type) {
		if (type == null) {
			return -1;
		}
		for (ModInfo<IEntityAdder> info : WDLApi
				.getImplementingExtensions(IEntityAdder.class)) {
			List<String> names = info.mod.getModEntities();
			if (names == null) {
				logger.warn(info.toString()
						+ " returned null for getModEntities()!");
				continue;
			}
			if (names.contains(type)) {
				return info.mod.getDefaultEntityTrackDistance(type);
			}
		}
		for (ModInfo<ISpecialEntityHandler> info : WDLApi
				.getImplementingExtensions(ISpecialEntityHandler.class)) {
			Multimap<String, String> specialEntities = info.mod.getSpecialEntities();
			if (specialEntities == null) {
				logger.warn(info.toString()
						+ " returned null for getSpecialEntities()!");
			}
			for (Map.Entry<String, String> e : specialEntities.entries()) {
				if (e.getValue().equals(type)) {
					int trackDistance = info.mod
							.getSpecialEntityTrackDistance(e.getValue());
					if (trackDistance < 0) {
						// Use vanilla value.
						trackDistance = getDefaultEntityRange(e.getKey());
					}
					return trackDistance;
				}
			}
		}
		
		return getVanillaEntityRange(stringToClassMapping.get(type));
	}
	
	/**
	 * Gets the track distance for the given entity in the current mode.
	 * 
	 * @param e
	 * @return
	 */
	public static int getEntityTrackDistance(Entity e) {
		return getEntityTrackDistance(
				WDL.worldProps.getProperty("Entity.TrackDistanceMode"), e);
	}
	
	/**
	 * Gets the track distance for the given entity in the given mode.
	 * 
	 * @param type
	 * @return
	 */
	public static int getEntityTrackDistance(String mode, Entity e) {
		if ("default".equals(mode)) {
			return getMostLikelyEntityTrackDistance(e);
		} else if ("server".equals(mode)) {
			int serverDistance = WDLPluginChannels
					.getEntityRange(getEntityType(e));
			
			if (serverDistance < 0) {
				return getMostLikelyEntityTrackDistance(e);
			}
			
			return serverDistance;
		} else if ("user".equals(mode)) {
			String prop = WDL.worldProps.getProperty("Entity." +
					getEntityType(e) + ".TrackDistance", "-1");
			
			return Integer.valueOf(prop);
		} else {
			throw new IllegalArgumentException("Mode is not a valid mode: " + mode);
		}
	}
	
	/**
	 * Gets the track distance for the given entity in the current mode.
	 * 
	 * @param type
	 * @return
	 */
	public static int getEntityTrackDistance(String type) {
		return getEntityTrackDistance(
				WDL.worldProps.getProperty("Entity.TrackDistanceMode"), type);
	}
	
	/**
	 * Gets the track distance for the given entity in the specified mode.
	 * 
	 * @param mode
	 * @param type
	 * @return
	 */
	public static int getEntityTrackDistance(String mode, String type) {
		if ("default".equals(mode)) {
			return getMostLikelyEntityTrackDistance(type);
		} else if ("server".equals(mode)) {
			int serverDistance = WDLPluginChannels
					.getEntityRange(mode);
			
			if (serverDistance < 0) {
				return getMostLikelyEntityTrackDistance(type);
			}
			
			return serverDistance;
		} else if ("user".equals(mode)) {
			String prop = WDL.worldProps.getProperty("Entity." +
					type + ".TrackDistance", "-1");
			
			return Integer.valueOf(prop);
		} else {
			throw new IllegalArgumentException("Mode is not a valid mode: " + mode);
		}
	}
	
	/**
	 * Gets the group for the given entity type.
	 * 
	 * @param type
	 * @return The group, or <code>null</code> if none is found.
	 */
	public static String getEntityGroup(String type) {
		if (type == null) {
			return null;
		}
		for (Map.Entry<String, String> e : entitiesByGroup.entries()) {
			if (type.equals(e.getValue())) {
				return e.getKey();
			}
		}
		
		return null;
	}
	
	/**
	 * Checks if an entity is enabled.
	 * 
	 * @param e The entity to check.
	 * @return
	 */
	public static boolean isEntityEnabled(Entity e) {
		return isEntityEnabled(getEntityType(e));
	}
	
	/**
	 * Checks if an entity is enabled.
	 * 
	 * @param type The type of the entity (from {@link #getEntityType(Entity)})
	 * @return
	 */
	public static boolean isEntityEnabled(String type) {
		boolean groupEnabled = WDL.worldProps.getProperty("EntityGroup." +
				getEntityGroup(type) + ".Enabled", "true").equals("true");
		boolean singleEnabled = WDL.worldProps.getProperty("Entity." +
				type + ".Enabled", "true").equals("true");
		
		return groupEnabled && singleEnabled;
	}
	
	/**
	 * Gets the type string for an entity.
	 * 
	 * @param e
	 * @return
	 */
	public static String getEntityType(Entity e) {
		String vanillaName = EntityList.getEntityString(e);
		
		for (ModInfo<ISpecialEntityHandler> info : WDLApi.getImplementingExtensions(ISpecialEntityHandler.class)) {
			if (info.mod.getSpecialEntities().containsKey(vanillaName)) {
				String specialName = info.mod.getSpecialEntityName(e);
				if (specialName != null) {
					return specialName;
				}
			}
		}
		
		return vanillaName;
	}
	
	/**
	 * Gets the track distance for the given entity, making a guess about
	 * whether to use spigot track distances based off of the server brand.
	 */
	public static int getMostLikelyEntityTrackDistance(Entity e) {
		if (WDL.isSpigot()) {
			return getDefaultSpigotEntityRange(e.getClass());
		} else {
			return getDefaultEntityRange(getEntityType(e));
		}
	}
	
	/**
	 * Gets the track distance for the given entity, making a guess about
	 * whether to use spigot track distances based off of the server brand.
	 */
	public static int getMostLikelyEntityTrackDistance(String type) {
		if (WDL.isSpigot()) {
			Class<?> c = stringToClassMapping.get(type);
			
			if (c != null) {
				return getDefaultSpigotEntityRange(c);
			} else {
				return getDefaultEntityRange(type);
			}
		} else {
			return getDefaultEntityRange(type);
		}
	}
	
	/**
	 * Gets the entity tracking range used by vanilla minecraft.
	 * <br/>
	 * Proper tracking ranges can be found in EntityTracker#trackEntity
	 * (the one that takes an Entity as a paremeter) -- it's the 2nd arg
	 * given to addEntityToTracker.
	 * 
	 * @param type The name of the entity.
	 * @return
	 */
	public static int getVanillaEntityRange(String type) {
		return getVanillaEntityRange(classToStringMapping.get(type)); 
	}
	
	/**
	 * Gets the entity tracking range used by vanilla minecraft.
	 * <br/>
	 * Proper tracking ranges can be found in EntityTracker#trackEntity
	 * (the one that takes an Entity as a paremeter) -- it's the 2nd arg
	 * given to addEntityToTracker.
	 * 
	 * @param c The entity class.
	 * @return
	 */
	public static int getVanillaEntityRange(Class<?> c) {
		if (c == null) {
			return -1;
		}
		if (EntityFishHook.class.isAssignableFrom(c)
				|| EntityArrow.class.isAssignableFrom(c)
				|| EntitySmallFireball.class.isAssignableFrom(c)
				|| EntityFireball.class.isAssignableFrom(c)
				|| EntitySnowball.class.isAssignableFrom(c)
				|| EntityEnderPearl.class.isAssignableFrom(c)
				|| EntityEnderEye.class.isAssignableFrom(c)
				|| EntityEgg.class.isAssignableFrom(c)
				|| EntityPotion.class.isAssignableFrom(c)
				|| EntityExpBottle.class.isAssignableFrom(c)
				|| EntityFireworkRocket.class.isAssignableFrom(c)
				|| EntityItem.class.isAssignableFrom(c)
				|| EntitySquid.class.isAssignableFrom(c)) {
			return 64;
		} else if (EntityMinecart.class.isAssignableFrom(c)
				|| EntityBoat.class.isAssignableFrom(c)
				|| EntityWither.class.isAssignableFrom(c)
				|| EntityBat.class.isAssignableFrom(c)
				|| IAnimals.class.isAssignableFrom(c)) {
			return 80; 
		} else if (EntityDragon.class.isAssignableFrom(c)
				|| EntityTNTPrimed.class.isAssignableFrom(c)
				|| EntityFallingBlock.class.isAssignableFrom(c)
				|| EntityHanging.class.isAssignableFrom(c)
				|| EntityArmorStand.class.isAssignableFrom(c)
				|| EntityXPOrb.class.isAssignableFrom(c)) {
			return 160;
		} else if (EntityEnderCrystal.class.isAssignableFrom(c)) {
			return 256;
		} else {
			return -1;
		}
	}

	/**
	 * Gets the entity range used by Spigot by default.
	 * Mostly a utility method for presets.
	 * 
	 * @param entity
	 * @return
	 */
	public static int getDefaultSpigotEntityRange(Class<?> c) {
		final int monsterRange = 48;
		final int animalRange = 48;
		final int miscRange = 32;
		final int otherRange = 64;
		
		//Spigot's mapping.  It's silly.
		if (EntityMob.class.isAssignableFrom(c) ||
				EntitySlime.class.isAssignableFrom(c)) {
			return monsterRange;
		} else if (EntityCreature.class.isAssignableFrom(c) ||
				EntityAmbientCreature.class.isAssignableFrom(c)) {
			return animalRange;
		} else if (EntityItemFrame.class.isAssignableFrom(c) ||
				EntityPainting.class.isAssignableFrom(c) ||
				EntityItem.class.isAssignableFrom(c) ||
				EntityXPOrb.class.isAssignableFrom(c)) {
			return miscRange;
		} else {
			return otherRange;
		}
	}
}
