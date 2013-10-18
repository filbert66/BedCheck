/* 
 * BedCheck.java
 * 
 * Author: Filbert66
 * 
 * History: 
 *  7 Sep 2013 : PSW : Started from scratch
 * 24 Sep 2013 : PSW : Added tool check on startup.
 * 25 Sep 2013 : PSW : Added NoBedList to not check people without beds each time.
 *                     Add formatting doubles to 3 digits after decimal
 * 26 Sep 2013 : PSW : Added commands to set tools, reload, save.
 * 26 Sep 2013 : PSW : Added maxLoginAge.
 *                   : Modified bedcheck.own to print other users who have same bed as you.
 * 27 Sep 2013 : PSW : Adjust tp locations by 0.5F.
 *
 * TODO: 
 */

/*    */ package com.yahoo.phil_work.bedcheck;

/*    */ 
/*    */ import java.util.logging.Logger;
import java.util.Iterator;
import java.text.NumberFormat;
import java.lang.System;
 
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
import org.bukkit.event.block.BlockBreakEvent;


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
	private NumberFormat nf;
	
	// Statistics
	int commands = 0, checks = 0, teleports = 0;
	
	public void onEnable()
	{
		// this.srv = getServer();
		log = this.getLogger();
		bcVersion = this.getDescription().getVersion();
		bcName = ChatColor.DARK_RED + this.getDescription().getName() + ": " + ChatColor.RESET;
		nf = NumberFormat.getInstance();
		nf.setMaximumFractionDigits (3);
		
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

		initConfig();		
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
		NoBedList.clear();
		HandlerList.unregisterAll ((Plugin)this);
	}
		
	private void initConfig() {
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
		
		String tool = getConfig().getString ("tool.bedcheck");
		if (Material.getMaterial (tool) == null)
			log.warning ("Unknown bedcheck tool '" + tool + "'");

		tool = getConfig().getString ("tool.teleport");
		if (Material.getMaterial (tool) == null)
			log.warning ("Unknown teleport tool '" + tool + "'");
	}	

	Location getBedHeadLoc (World w, int x, int y, int z) {
		return getBedHeadLoc (new Location (w, x,y,z));
	}
	Location getBedHeadLoc (Location l) {
		if (l == null)
			return null;
			
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

	// Returns list of offline users who have that as bed
	//  Checks optional config to limit search by login age of user.
	private ArrayList<String> getWhoseBed (Location bedLoc)
	{
		ArrayList<String> nameList = new ArrayList();
		
		boolean checkAge = getConfig().isSet("maxLoginAge");
		int maxAge = getConfig().getInt ("maxLoginAge"); // may be zero
		long currTime = System.currentTimeMillis();

		for (OfflinePlayer op : this.getServer().getOfflinePlayers()) {
			if (checkAge) {
				long daysAge = (currTime - op.getLastPlayed ()) / 1000 / 60 / 60 / 24;
				if (daysAge > maxAge) {
					log.finer ("skipping check for " + op.getName() + " with age " + daysAge);
					continue;
				}
			}
			
			String name = op.getName();
				
			if (spawnDistance (op, bedLoc) == 0) {
				Player online = op.getPlayer();
				if (online != null)
					name = online.getDisplayName();
				nameList.add (name);
			}
		}						
		return nameList;
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
			p.sendMessage (this.bcName + "Your bed is " + nf.format (dist) + " from this bed");
			
		if (dist == 0 || p.hasPermission ("bedcheck.all"))
		{
			ArrayList<String> nameList = getWhoseBed (bedLoc);

			if ( !nameList.isEmpty())	{
				String catnames = "";
				for (String s : nameList) {
					if (s.equals (p.getName()))
						continue;  // know it's my bed already
					catnames += s + ", ";
				}
				if (catnames.length() > 0)
					p.sendMessage (this.bcName + "Also bed of following : " + 
									catnames.substring (0, catnames.lastIndexOf (", ")) );
			}								
		}
	 }
	static private ArrayList <String> NoBedList = new ArrayList();

	/*** spawnDistance (OfflinePlayer, bed Location)
	 * Returns: 0 if this location is player's bed
	 *  0-N if bed is some distance away
	 *  -1 if player has no bed
	 */
	double spawnDistance (OfflinePlayer p, Location bedLoc) 
	{
		Location spawn = p.getBedSpawnLocation();
		String pName = p.getName();
				
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
				spawn = getBedHeadLoc (spawn); // at risk: no head found

				NoBedList.remove (pName);	// in case it's changed.
			}
			else {
				// Likely some obstructing object nearby caused spawnpoint to move from normal.
				log.fine ("Darn, found " + spawn.getBlock().getType() + " at spawn at " + spawn);
				spawn.add (-1,0,-1);

				// If player online or player NOT in ignore list, do the following
				if (p.isOnline() || !NoBedList.contains (pName)) {
				
				// Look around spawn. If only one bed within same distance, must be the same
				int s1=spawn.getBlockX(), s2=spawn.getBlockY(), s3=spawn.getBlockZ();
			    int dist = 3;		// distance that NMS looks around.		
				int bedsFound = 0;
				World w= bedLoc.getWorld();
				Location foundSpawn = null;
				
				int i, j, k; 
				for (i= -dist; i < dist && bedsFound < 3; i++)
				  for (j= -dist; j < dist && bedsFound < 3; j++)
				     for (k= -dist; k < dist && bedsFound < 3; k++)
				     	if (w.getBlockAt (s1 + i, s2 + j, s3 + k).getType() == Material.BED_BLOCK) {
				     		bedsFound++;
							foundSpawn = new Location (w, s1 + i, s2 + j, s3 + k);
				     		log.finer ("spawnDistance: Bed block found at " + (s1 + i) + "," + (s2 + j) + "," + (s3 + k));
						}
				if (bedsFound > 2)
					log.warning ("Too many nearby beds at " + 
								 s1 + "," + s2 + "," + s3 + "; may be inaccurate");
				else if (bedsFound > 0 ) {
					// must be the same; it was the only one nearby  
					spawn = getBedHeadLoc (foundSpawn);
					if (p.isOnline())
						NoBedList.remove (pName);	// in case it's changed.
				}
				else {
					// Log bed of these users and then skip them on later searches
					NoBedList.add (pName);
					
					log.info ("No bed found near " + pName + "'s spawn at " + s1 + "," + s2 + "," + s3);
				}
				} // end if not already on nobedlist
			}
		}	
		double dist = spawn.distance (bedLoc);
		if (dist <= 1.0F)
			return 0F;
		else
			return dist;				
	}

	/* 
	 * Begin Event Handlers
	 */

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
						spawn.setX (spawn.getX() - 0.5F);  // avoid walls
						spawn.setZ (spawn.getZ() - 0.5F);
						spawn.setY (spawn.getY () +1);
						p.teleport (spawn, TeleportCause.PLUGIN);
						e.setCancelled(true);
						this.teleports++;
					} else 
						p.sendMessage (this.bcName + "You have no bed set");
				}
			}
		}
	}
		
	/*  BlockBreakEvent()
	 *    Adds check on bed break to only allow if:
	 *     this is your own bed. (config t/f)
	 *     (and) this is not someone else's bed (config t/f), plus permissions
	 * Caveat: Could use Player Interactive Event, but this is more efficient
	 */		
	@EventHandler (ignoreCancelled = true)
	public void bedBreakCheck (BlockBreakEvent event) 
	{
		Block b = event.getBlock();
		Material m = b.getType();
		Player p = event.getPlayer();

		if (m == Material.BED || m == Material.BED_BLOCK) 
		{
			boolean ownBed = spawnDistance (p, b.getLocation()) == 0;
		
			if (getConfig().getBoolean ("breakrule.mustbeown"))
			{
				if (! ownBed && ! p.hasPermission ("bedbreak.beds")) 
				{
					// not own bed and don't have permission to break other beds
					p.sendMessage (bcName + ChatColor.RED + "May only break your own bed");
					event.setCancelled (true);
					return;
				} else	
					log.fine ("Allowing break of " + (ownBed ? "own" : "not own") + " bed by " + p.getName());								 	
			}

			// Also check that it's not someone else's bed
			if (getConfig().getBoolean ("breakrule.nooneelse"))
			{
				ArrayList<String> nameList = getWhoseBed (b.getLocation());
				if ( !nameList.isEmpty() &&
					 !(ownBed && nameList.size() == 1) && ! p.hasPermission ("bedbreak.others"))
				{ 
					p.sendMessage (bcName + ChatColor.RED + "May not break other's beds");
					event.setCancelled (true);
				} else	
					log.fine ("Allowing break of bed of " + nameList.size() + " users.");							 	
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
	
	private void sendMsg (CommandSender sender, String message) {
		if ( !(sender instanceof Player))
			sender.sendMessage (ChatColor.stripColor (message));
		else 
			sender.sendMessage (message);
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
			spawn.setX (spawn.getX() - 0.5F);  // avoid walls
			spawn.setZ (spawn.getZ() - 0.5F);
			spawn.setY (spawn.getY() + 1); // tp into the ground sometimes.
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
				sendMsg (sender, ChatColor.GRAY + name + ChatColor.RESET + " bed in " + loc.getWorld().getName() + " at " +
					ChatColor.BLUE + loc.getX() + ", " + loc.getY() + ", " + loc.getZ() );
			}					
			if ( !noBedList.isEmpty()) {
				String catnames = "";
				for (String s : noBedList)
					catnames += s + ", ";
				sendMsg (sender, "Following have no bed : " + 
									catnames.substring (0, catnames.lastIndexOf (", ")) );
			}	
			return true;
		}	
		else if (commandName.equals ("tool") || commandName.equals ("print")) {
			if (args.length < 2) {
				// print current settings
				sendMsg (sender, "Current " + ChatColor.BLUE + "tool.bedcheck: " + ChatColor.RESET +
						 getConfig().getString ("tool.bedcheck"));
				sendMsg (sender, "Current " + ChatColor.BLUE + "tool.teleport: "  + ChatColor.RESET + 
						 getConfig().getString ("tool.teleport"));	

				if (commandName.equals ("print")) {
					if (getConfig().isSet("breakrule.mustbeown"))
						sendMsg (sender, "Current " + ChatColor.BLUE + "breakrule.mustbeown: "  + ChatColor.RESET + 
								 getConfig().getBoolean ("breakrule.mustbeown"));	
					if (getConfig().isSet ("breakrule.nooneelse"))
						sendMsg (sender, "Current " + ChatColor.BLUE + "breakrule.nooneelse: "  + ChatColor.RESET + 
								 getConfig().getBoolean ("breakrule.nooneelse"));	
				}			
				return true;
			}
			String tool = args [1].toLowerCase();
			
			if (args.length <3)
				return false;
			String newTool = args [2].toUpperCase();
			if (Material.getMaterial (newTool) == null) {
				sendMsg (sender, 
						ChatColor.RED + "Unrecognizable Material: " + ChatColor.RESET + "'" + newTool + "'");
				return true;
			}
			String configItem;
			if (tool.indexOf ("check") != -1) 
				configItem = "tool.bedcheck";
			else if (tool.equals ("teleport")) 
				configItem = "tool.teleport";
			else 
				return false;
				
			getConfig().set (configItem, newTool);
			sendMsg (sender, "Set " + ChatColor.DARK_BLUE + configItem + ChatColor.RESET + " to " + newTool);
			return true;				
		}
		else if (commandName.equals ("rule")) {
			if (args.length < 2) {
				// print current settings
				sendMsg (sender, "Current " + ChatColor.BLUE + "breakrule.mustbeown: " + ChatColor.RESET +
						 getConfig().getString ("breakrule.mustbeown"));
				sendMsg (sender, "Current " + ChatColor.BLUE + "breakrule.nooneelse: "  + ChatColor.RESET + 
						 getConfig().getString ("breakrule.nooneelse"));	
				return true;
			}
			String rule = args [1].toLowerCase();
			String configItem;
			if (rule.indexOf ("own") != -1) 
				configItem = "breakrule.mustbeown";
			else if (rule.indexOf ("else") != -1) 
				configItem = "breakrule.nooneelse";
			else 
				return false;
			
			if (args.length <3)
				return false;
			boolean setting = args [2].toLowerCase().equals ("true");
				
			getConfig().set (configItem, setting);
			sendMsg (sender, "Set " + ChatColor.DARK_BLUE + configItem + ChatColor.RESET + " to " + setting);
			return true;				
		} else if (commandName.equals("save")) {
			this.saveConfig();
			return true;									
		}
		else if (commandName.equals("reload")) {
			this.reloadConfig();
			this.initConfig();
			return true;									
		}		
		return false;						
	}
				
    public static boolean validName(String name) {
        return name.length() > 2 && name.length() < 17 && 
	        	!name.matches("(?i).*[^a-z0-9_].*");
    }
}