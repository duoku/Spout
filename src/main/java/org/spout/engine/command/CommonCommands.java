/*
 * This file is part of Spout.
 *
 * Copyright (c) 2011-2012, Spout LLC <http://www.spout.org/>
 * Spout is licensed under the Spout License Version 1.
 *
 * Spout is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * In addition, 180 days after any changes are published, you can use the
 * software, incorporating those changes, under the terms of the MIT license,
 * as described in the Spout License Version 1.
 *
 * Spout is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License,
 * the MIT license and the Spout License Version 1 along with this program.
 * If not, see <http://www.gnu.org/licenses/> for the GNU Lesser General Public
 * License and see <http://spout.in/licensev1> for the full license, including
 * the MIT license.
 */
package org.spout.engine.command;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.spout.api.chat.ChatArguments;
import org.spout.api.chat.style.ChatStyle;
import org.spout.api.command.CommandBatch;
import org.spout.api.command.CommandContext;
import org.spout.api.command.CommandSource;
import org.spout.api.command.annotated.Command;
import org.spout.api.command.annotated.CommandPermissions;
import org.spout.api.entity.Player;
import org.spout.api.exception.CommandException;
import org.spout.api.geo.World;
import org.spout.api.geo.cuboid.Region;
import org.spout.api.geo.discrete.Point;
import org.spout.api.geo.discrete.Transform;
import org.spout.api.math.Quaternion;
import org.spout.api.math.Vector3;
import org.spout.api.meta.SpoutMetaPlugin;
import org.spout.api.plugin.Plugin;
import org.spout.engine.SpoutEngine;

public class CommonCommands {
	private final SpoutEngine engine;

	public CommonCommands(SpoutEngine engine) {
		this.engine = engine;
	}

	public SpoutEngine getEngine() {
		return engine;
	}

	@Command(aliases = {"bat", "batch"}, usage = "batch <file>", desc = "Executes a Spout batch file.", min = 1, max = 1)
	public void batch(CommandContext args, CommandSource source) throws CommandException {
		String fileName = args.getString(0);
		if (!(source.hasPermission("spout.command.batch." + fileName))) {
			throw new CommandException("You do not have permission to execute " + fileName);
		}
		CommandBatch bat = (CommandBatch) engine.getFilesystem().getResource("batch://Spout/batches/" + fileName);
		bat.execute(source);
		source.sendMessage(ChatStyle.BRIGHT_GREEN, "Executed " + fileName + ".");
	}

	@Command(aliases = "stop", usage = "[message]", desc = "Stop the server!", max = -1)
	@CommandPermissions("spout.command.stop")
	public void stop(CommandContext args, CommandSource source) {
		String message = "Engine halting";
		switch (engine.getPlatform()) {
			case CLIENT:
				message = "Client halting";
				break;
			case PROXY:
				message = "Proxy halting";
				break;
			case SERVER:
				message = "Server halting";
				break;
		}
		if (args.length() > 0) {
			message = args.getJoinedString(0).getPlainString();
		}
		engine.stop(message);
	}

	@Command(aliases = "stackdump", desc = "Writes the stack trace of all active threads to the logs", max = -1)
	@CommandPermissions("spout.command.dumpstack")
	public void dumpstack(CommandContext args, CommandSource source) {
		Map<Thread, StackTraceElement[]> dump = Thread.getAllStackTraces();
		Iterator<Entry<Thread, StackTraceElement[]>> i = dump.entrySet().iterator();
		engine.getLogger().info("[--------------Thread Stack Dump--------------]");
		while (i.hasNext()) {
			Entry<Thread, StackTraceElement[]> e = i.next();
			engine.getLogger().info("Thread: " + e.getKey().getName());
			for (StackTraceElement element : e.getValue()) {
				engine.getLogger().info("    " + element.toString());
			}
			engine.getLogger().info("");
		}
		engine.getLogger().info("[---------------End Stack Dump---------------]");
	}

	@Command(aliases = "reload", usage = "[plugin]", desc = "Reload engine and/or plugins", max = 1)
	@CommandPermissions("spout.command.reload")
	public void reload(CommandContext args, CommandSource source) throws CommandException {
		if (args.length() == 0) {
			source.sendMessage(ChatStyle.BRIGHT_GREEN, "Reloading engine...");

			for (Plugin plugin : getEngine().getPluginManager().getPlugins()) {
				if (plugin.getDescription().allowsReload()) {
					plugin.onReload();
				}
			}

			source.sendMessage(ChatStyle.BRIGHT_GREEN, "Reloaded.");
		} else {
			String pluginName = args.getString(0);
			if (getEngine().getPluginManager().getPlugin(pluginName) == null) {
				throw new CommandException("'" + pluginName + "' is not a valid plugin name.");
			}

			Plugin plugin = getEngine().getPluginManager().getPlugin(pluginName);
			if (!plugin.getDescription().allowsReload()) {
				throw new CommandException("The plugin '" + pluginName + "' does not allow reloads.");
			}
			plugin.onReload();
			source.sendMessage(ChatStyle.BRIGHT_GREEN, "Reloaded '", pluginName, "'.");
		}
	}

	@Command(aliases = {"plugins", "pl"}, desc = "List all plugins on the engine")
	@CommandPermissions("spout.command.plugins")
	public void plugins(CommandContext args, CommandSource source) {
		List<Plugin> plugins = getEngine().getPluginManager().getPlugins();
		ChatArguments pluginListString = new ChatArguments();
		pluginListString.append(Arrays.<Object>asList("Plugins (", plugins.size() - 1, "): "));

		for (int i = 0; i < plugins.size(); i++) {
			Plugin plugin = plugins.get(i);
			if (plugin instanceof SpoutMetaPlugin) {
				continue;
			}

			pluginListString.append(plugin.isEnabled() ? ChatStyle.BRIGHT_GREEN : ChatStyle.RED)
					.append(plugin.getName());

			if (i != plugins.size() - 1) {
				pluginListString.append(ChatStyle.RESET).append(", ");
			}
		}
		source.sendMessage(pluginListString);
	}

	@Command(aliases = {"setspawn", "ss"}, desc = "Sets the spawnpoint for a world", min = 0, max = 4)
	@CommandPermissions("spout.command.setspawn")
	public void setspawn(CommandContext args, CommandSource source) throws CommandException {
		//Not a player? Make sure the console is specifying world, x, y, z
		if (!(source instanceof Player)) {
			if (args.length() != 4) {
				throw new CommandException("Need to specify world as well as x y z when executing from the console.");
			}
		}
		Point point;
		//Source is a player and didn't provide world, x, y, z so instead set the spawn point of their current world at their current position.
		if (args.length() != 4) {
			point = ((Player) source).getScene().getPosition();
			//Either Source is the console or the player specified world, x, y, z so set those values
		} else {
			if (engine.getWorld(args.getString(0)) == null) {
				throw new CommandException("World: " + args.getString(0) + " is not loaded/existant!");
			}
			point = new Point(engine.getWorld(args.getString(0)), args.getInteger(1), args.getInteger(2), args.getInteger(3));
		}
		//Finally set the spawn point
		point.getWorld().setSpawnPoint(new Transform(point, Quaternion.IDENTITY, Vector3.ONE));
		//Notify the source
		source.sendMessage(new ChatArguments("Set the spawnpoint of world: ", ChatStyle.PURPLE, point.getWorld().getName(), ChatStyle.WHITE, " to x: ",
				ChatStyle.BRIGHT_GREEN, point.getBlockX(), ChatStyle.WHITE, ", y: ", ChatStyle.BRIGHT_GREEN, point.getBlockY(), ChatStyle.WHITE, ", z: ", ChatStyle.BRIGHT_GREEN, point.getBlockZ()));
	}

	@Command(aliases = {"whatisspawn", "wis"}, desc = "Tells you the spawnpoint of a world", min = 0, max = 1)
	@CommandPermissions("spout.command.tellspawn")
	public void tellspawn(CommandContext args, CommandSource source) throws CommandException {
		if (!(source instanceof Player)) {
			if (args.length() != 1) {
				throw new CommandException("Must specify a world to find out the spawnpoint from the console!");
			}
		}
		Point point;
		if (args.length() != 1) {
			point = ((Player) source).getScene().getPosition();
		} else {
			final World world = engine.getWorld(args.getString(0));
			if (world == null) {
				throw new CommandException("World: " + args.getString(0) + " is not loaded/existant!");
			}
			point = world.getSpawnPoint().getPosition();
		}
		source.sendMessage(new ChatArguments("The spawnpoint of world: ", ChatStyle.PURPLE, point.getWorld().getName(), ChatStyle.WHITE, " is x: ",
				ChatStyle.BRIGHT_GREEN, point.getBlockX(), ChatStyle.WHITE, ", y: ", ChatStyle.BRIGHT_GREEN, point.getBlockY(), ChatStyle.WHITE, ", z: ", ChatStyle.BRIGHT_GREEN, point.getBlockZ()));
	}

	@Command(aliases = {"worldinfo"}, desc = "Provides info about known worlds", usage = "[world]", min = 0, max = 1)
	@CommandPermissions("spout.command.worldinfo")
	public void worldInfo(CommandContext args, CommandSource source) throws CommandException {
		if (args.length() == 0) {
			Collection<World> worlds = engine.getWorlds();
			ChatArguments output = new ChatArguments("Worlds (", worlds.size(), "): ");
			for (Iterator<World> i = worlds.iterator(); i.hasNext(); ) {
				output.append(i.next().getName());
				if (i.hasNext()) {
					output.append(", ");
				}
			}
			source.sendMessage(output);
		} else {
			World world = engine.getWorld(args.getString(0));
			if (world == null) {
				throw new CommandException("Unknown world: " + world);
			}
			source.sendMessage("World: ", world.getName());
			source.sendMessage("==========================");
			source.sendMessage("Age: ", world.getAge());
			source.sendMessage("UUID: ", world.getUID());
			source.sendMessage("Seed: ", world.getSeed());
		}
	}

	@Command(aliases = {"regioninfo"}, desc = "Provides info about regions", usage = "[world]", min = 1, max = 1)
	@CommandPermissions("spout.command.regioninfo")
	public void chunkInfo(CommandContext args, CommandSource source) throws CommandException {
		World world = engine.getWorld(args.getString(0));
		if (world == null) {
			throw new CommandException("Unknown world: " + world);
		}
		source.sendMessage("World: ", world.getName());
		source.sendMessage("==========================");
		int chunks = 0;
		int regions = 0;
		for (Region r : world.getRegions()) {
			regions++;
			chunks += r.getNumLoadedChunks();
		}
		source.sendMessage("Regions:", regions);
		source.sendMessage("chunks: ", chunks);
	}

	@Command(aliases = {"tp", "teleport"}, usage = "[player] [player|x] [y] [z] [-w <world>]", flags = "w:", desc = "Teleport to a location", min = 1, max = 4)
	@CommandPermissions("spout.command.tp")
	public void tp(CommandContext args, CommandSource source) throws CommandException {
		Player player;
		Player target = null;
		Point point;
		if (args.length() == 1) {
			if (!(source instanceof Player)) {
				throw new CommandException("You must be a player to teleport yourself!");
			}

			player = engine.getPlayer(args.getString(0), true);
			if (player == null || !player.isOnline()) {
				throw new CommandException(args.getString(0) + " is not online.");
			}
			point = player.getScene().getPosition();
		} else {
			player = engine.getPlayer(args.getString(0), true);
			if (player == null || !player.isOnline()) {
				throw new CommandException(args.getString(0) + " is not online.");
			}

			if (args.length() > 2) {
				World world = player.getWorld();

				if (args.hasFlag('w')) {
					if (!source.hasPermission("spout.command.tp.world-flag")) {
						throw new CommandException("You are not allowed to use the world flag.");
					}

					world = engine.getWorld(args.getFlagString('w'));

					if (world == null) {
						throw new CommandException("Please supply an existing world.");
					}
				}

				float x = player.getScene().getPosition().getX();
				if (args.isInteger(1)) {
					x = args.getInteger(1);
				} else if (args.getString(1).startsWith("~")) {
					x += Integer.parseInt(args.getString(1).substring(1));
				} else {
					throw new CommandException("Invalid coordinates");
				}

				float y = player.getScene().getPosition().getY();
				if (args.isInteger(2)) {
					y = args.getInteger(2);
				} else if (args.getString(2).startsWith("~")) {
					y += Integer.parseInt(args.getString(2).substring(1));
				} else {
					throw new CommandException("Invalid coordinates");
				}

				float z = player.getScene().getPosition().getZ();
				if (args.isInteger(3)) {
					z = args.getInteger(3);
				} else if (args.getString(3).startsWith("~")) {
					z += Integer.parseInt(args.getString(3).substring(1));
				} else {
					throw new CommandException("Invalid coordinates");
				}

				point = new Point(world, x, y, z);
			} else {
				target = engine.getPlayer(args.getString(1), true);

				if (target == null || !target.isOnline()) {
					throw new CommandException(args.getString(1) + " is not online.");
				}

				point = target.getScene().getPosition();
			}
		}
		point.getWorld().getChunkFromBlock(point);
		player.teleport(point);

		if (target != null) {
			player.sendMessage(ChatStyle.RESET, ChatStyle.BLUE, "You teleported to ", ChatStyle.WHITE, target.getName() + ".", ChatStyle.RESET);
			target.sendMessage(ChatStyle.RESET, ChatStyle.WHITE, player.getName(), ChatStyle.BLUE, " teleported to you.", ChatStyle.RESET);
			return;
		}
		player.sendMessage(ChatStyle.RESET, ChatStyle.BLUE, "You were teleported to ", ChatStyle.BRIGHT_GREEN, point.getWorld().getName(), ChatStyle.BLUE, ", X: ", ChatStyle.WHITE, point.getX(),
				ChatStyle.BLUE, ", Y: ", ChatStyle.WHITE, point.getY(), ChatStyle.BLUE, ", Z: ", ChatStyle.WHITE, point.getZ(), ChatStyle.BLUE, ".", ChatStyle.RESET);
	}
}
