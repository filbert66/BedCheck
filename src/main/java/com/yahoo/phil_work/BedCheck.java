/* 
 * BedCheck.java
 * 
 * History: 
 *  7 Sep 2013 : PSW : Started from scratch
 */

/*    */ package com.yahoo.phil_work.bedcheck;

/*    */ 
/*    */ import java.util.logging.Logger;
import java.util.Iterator;
 
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BlockFace;
import org.bukkit.material.MaterialData;
import org.bukkit.material.Bed;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;


import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;

import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;

import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;


import org.bukkit.Bukkit;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Arrays;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.IOException;


/*    */ import org.bukkit.Server;
import org.bukkit.event.Event;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Listener;
import org.bukkit.event.HandlerList;
/*    */ import org.bukkit.event.EventPriority;
/*    */ import org.bukkit.plugin.*;
import org.bukkit.plugin.PluginLogger;
/*    */ import org.bukkit.plugin.java.JavaPlugin;
/*    */ import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.World.Environment;

import org.mcstats.Metrics;

public class BedCheck extends JavaPlugin implements Listener {
	public Logger log;
	private String Log_Level;
	public static String bcVersion;
	public String bcName;

	// Statistics
	int commands = 0, checks = 0, teleports = 0;
	
	public void onEnable()
	{
		// this.srv = getServer();
		log = this.getLogger();
		bcVersion = this.getDescription().getVersion();
		bcName = ChatColor.DARK_RED + this.getDescription().getName() + ": " + ChatColor.RESET;

		log.info("Developed by Filbert66; inspired by TehBeginner");
		
		getServer().getPluginManager().registerEvents ((Listener)this, this);

		 /* 
		  * Begin Configuration section
		  */	
		boolean writeDefault = false;
		if ( !getDataFolder().exists()) {
			writeDefault = true;
			getConfig().options().copyDefaults(true);
			log.info ("No config found in " + getDescription().getName() + "/; writing defaults");
		}

		if (getConfig().isString ("log_level")) {
			// Hidden config, in case we set the level for a logged message too high. Or for debugging
			//   NOTE that only INFO and higher get logged to console; the rest go to the server.log file.
			Log_Level = getConfig().getString("log_level", "INFO");
			try {
				log.setLevel (log.getLevel().parse (Log_Level));
				log.info ("successfully set log level to " + log.getLevel());
			}
			catch (Throwable IllegalArgumentException) {
				log.warning ("Illegal log_level string argument '" + Log_Level);
			}
		} else {
			log.setLevel (Level.INFO); // make sure! Ticket 17 seems to indicate default doesn't work.
		}
		
		// printConfig ();			
		
		// Add Metrics to mcstats.org
		try {
			Metrics metrics = new Metrics(this);
			
			Metrics.Graph graph = metrics.createGraph("Usage");
			graph.addPlotter(new Metrics.Plotter("Commands executed") {
		
					@Override
					public int getValue() {
							return commands; 
					}
			});

			graph.addPlotter(new Metrics.Plotter("Bed checks") {
		
					@Override
					public int getValue() {
							return checks; 
					}
			});
		
			graph.addPlotter(new Metrics.Plotter("Teleports") {
		
					@Override
					public int getValue() {
							return teleports;
					}
			});
			
			metrics.start();
		} catch (IOException e) {
			// Failed to submit the stats :-(
			log.warning ("Unable to start mcstats.org metrics: " +e);
		}
	
		if (writeDefault)
			saveDefaultConfig();
	}
	
	public void onDisable () 
	{
		HandlerList.unregisterAll ((Plugin)this);
	}
	
	Location getBedHeadLoc (Location l) {
		Material m = l.getBlock().getType();
		if (m.getData() != Bed.class) {
			log.warning ("getBedHeadLoc: not a bed at " + l);
			return null;
		}
		
		Bed b = (Bed) l.getBlock().getState().getData();
		if ( !b.isHeadOfBed()) {
			log.fine ("Not head of bed");
			BlockFace face = b.getFacing();
			l.add (face.getModX(), face.getModY(), face.getModZ());
			
			if ( !((Bed) l.getBlock().getState().getData()).isHeadOfBed()) {
				log.warning ("Couldn't find head of bed!");
				return null;
			}
		}
		return l;
	}

	// Expects Player and bed he clicked on
	//  Assumes : player has bedcheck.own permission at least
	//  Then also checks for bedcheck.all
	private void bedCheck (Player p, Location bedLoc) {
		double dist = spawnDistance (p, bedLoc);
		if (dist == 0F)
			p.sendMessage (this.bcName + "This is your bed");
		else if (dist < 0)
			p.sendMessage (this.bcName + "You have no spawning bed.");
		else
			p.sendMessage (this.bcName + "Your bed is " + dist + " from this bed");
			
		if (p.hasPermission ("bedcheck.all"))
		{
			ArrayList<String> nameList = new ArrayList();
			for (OfflinePlayer op : this.getServer().getOfflinePlayers()) {
				String name = op.getName();
				if (name.equals (p.getName()))
					continue;  // know it's my bed already
					
				if (spawnDistance (op, bedLoc) == 0) {
					Player online = op.getPlayer();
					if (online != null)
						name = online.getDisplayName();
					nameList.add (name);
				}
			}						
			if ( !nameList.isEmpty())	{
				String catnames = "";
				for (String s : nameList)
					catnames += s + ", ";
				p.sendMessage (this.bcName + "Also bed of following : " + 
								catnames.substring (0, catnames.lastIndexOf (", ")) );
			}								
		}
	 }

	/*** spawnDistance (OfflinePlayer, bed Location)
	 * Returns: 0 if this location is player's bed
	 *  0-N if bed is some distance away
	 *  -1 if player has no bed
	 */
	double spawnDistance (OfflinePlayer p, Location bedLoc) 
	{
		Location spawn = p.getBedSpawnLocation();
				
		if ( !(spawn != null))
			return -1;
		
		// Find head of bed. 
		bedLoc = getBedHeadLoc (bedLoc);
		if ( !(bedLoc != null))
			return -1;
		
		// Find bed that spawn relates to
		if (spawn.getBlock().getType().getData() != Bed.class) {
			// appears standard is -1, -1 no matter bed orientation
			if (spawn.add (1, 0, 1).getBlock().getType().getData() == Bed.class) {
				log.fine ("Found spawn bed at " + spawn);
			}
			else {
				log.fine ("Darn, found " + spawn.getBlock().getType() + " at spawn at " + spawn);
				spawn.add (-1,0,-1);
			}
		}	
		double dist = spawn.distance (bedLoc);
		if (dist <= 1.0F)
			return 0F;
		else
			return dist;				
	}

	@EventHandler (ignoreCancelled = true)
	public void onInteract (PlayerInteractEvent e) {
		if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
			Material m =  e.getClickedBlock().getType();
			
			Player p = e.getPlayer();

			if (m == Material.BED || m == Material.BED_BLOCK) {
				Material item = p.getItemInHand().getType();
				
				if (item.equals (Material.getMaterial (getConfig().getString ("tool.bedcheck"))) &&
					(p.hasPermission ("bedcheck.own") || p.hasPermission ("bedcheck.all")) )
				{
					log.fine ("Right Clicked " + m + " at " + e.getClickedBlock().getLocation());
					bedCheck (p, e.getClickedBlock().getLocation());
					this.checks++;
					e.setCancelled(true);
				} else if (item.equals (Material.getMaterial (getConfig().getString ("tool.teleport"))) &&
						p.hasPermission ("bedcheck.teleport") ) 
				{
					Location spawn = p.getBedSpawnLocation();
					if (spawn != null) {
						p.sendMessage (this.bcName + "Teleporting to your bed");
						p.teleport (spawn, TeleportCause.PLUGIN);
						e.setCancelled(true);
						this.teleports++;
					} else 
						p.sendMessage (this.bcName + "You have no bed set");
				}
			}
		}
	}
	
	@Override
    public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args) {
        String commandName = command.getName().toLowerCase();
        String[] trimmedArgs = args;

        // sender.sendMessage(ChatColor.GREEN + trimmedArgs[0]);
        if (commandName.equals("bc")) {
            this.commands++; 
            return bcCommands(sender, trimmedArgs);
		}
		
		return false;
	}
	// usage: <command> list|(tp <username>)
	private boolean bcCommands(CommandSender sender, String[] args) {
		if (args.length == 0)
			return false;			
		String commandName = args[0].toLowerCase();
	
		if (commandName.equals ("tp") || commandName.equals ("teleport")) 
		{
			if ( !(sender instanceof Player)) {
				sender.sendMessage (getDescription().getName() + ": Cannot teleport SERVER");
				return true;
			}
			Player p = (Player)sender;
			
			if (args.length == 1)
				sender.sendMessage (bcName + "teleport requires username");

			String userName = args[1];
			if ( !validName (userName)) {
				sender.sendMessage(ChatColor.RED + "bad player name '" + userName + "'");
                return true;
            } 

            OfflinePlayer op = sender.getServer().getOfflinePlayer (userName);            
            if ( !op.hasPlayedBefore()) {
				sender.sendMessage (bcName + "'" + userName + "' has never played before");
				return true;
			}

			Location spawn = op.getBedSpawnLocation();
			if (spawn == null) {
				sender.sendMessage (bcName +  "'" + userName + "' has no spawn bed set");
				return true;
			}
			p.teleport (spawn, TeleportCause.COMMAND);	
			p.sendMessage (bcName + "teleported to " + userName + "'s bed in " + spawn.getWorld().getName() + " at " +
					spawn.getX() + ", " + spawn.getY() + ", " + spawn.getZ() );
			
			return true;
		}	
		else if (commandName.equals ("list")) 
		{
			ArrayList<String> noBedList = new ArrayList();
			
			for (OfflinePlayer op : this.getServer().getOfflinePlayers()) {
				Location loc = op.getBedSpawnLocation();
				String name = (sender instanceof Player) && op.isOnline() ? 
					op.getPlayer().getDisplayName() : op.getName();
				if (loc == null) {
					noBedList.add (name);
					continue;
				}
				sender.sendMessage (name + " bed in " + loc.getWorld().getName() + " at " +
					loc.getX() + ", " + loc.getY() + ", " + loc.getZ() );
			}					
			if ( !noBedList.isEmpty()) {
				String catnames = "";
				for (String s : noBedList)
					catnames += s + ", ";
				sender.sendMessage ("Following have no bed : " + 
									catnames.substring (0, catnames.lastIndexOf (", ")) );
			}	
			return true;
		}	
		
		return false;						
	}
				
    public static boolean validName(String name) {
        return name.length() > 2 && name.length() < 17 && 
	        	!name.matches("(?i).*[^a-z0-9_].*");
    }
			
}