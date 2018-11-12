/*
 * This file is part of World Downloader: A mod to make backups of your
 * multiplayer worlds.
 * http://www.minecraftforum.net/forums/mapping-and-modding/minecraft-mods/2520465
 *
 * Copyright (c) 2014 nairol, cubic72
 * Copyright (c) 2018 Pokechu22, julialy
 *
 * This project is licensed under the MMPLv2.  The full text of the MMPL can be
 * found in LICENSE.md, or online at https://github.com/iopleke/MMPLv2/blob/master/LICENSE.md
 * For information about this the MMPLv2, see http://stopmodreposts.org/
 *
 * Do not redistribute (in modified or unmodified form) without prior permission.
 */
package wdl.versioned;

import javax.annotation.Nullable;

import net.minecraft.item.ItemMap;
import net.minecraft.network.play.server.SPacketMaps;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.storage.MapData;

/**
 * Functions related to maps (the item).
 *
 * In 1.13.0, loadMapData was changed to a string, but the dimension was still a
 * byte.
 */
class MapFunctions {
	/* (non-javadoc)
	 * {@see VersionedFunctions#getMapData}
	 */
	@Nullable
	static MapData getMapData(World world, SPacketMaps mapPacket) {
		return ItemMap.loadMapData(world, "map_" + mapPacket.getMapId());
	}

	/* (non-javadoc)
	 * {@see VersionedFunctions#isMapDimensionNull}
	 */
	static boolean isMapDimensionNull(MapData map) {
		return false; // A primitive byte can't be null
	}

	/* (non-javadoc)
	 * {@see VersionedFunctions#setMapDimension}
	 */
	static void setMapDimension(MapData map, DimensionType dim) {
		map.dimension = (byte)dim.getId();
	}
}
