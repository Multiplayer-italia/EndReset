package de.V10lator.EndReset;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.CreatureType;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldListener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

public class EndReset extends JavaPlugin
{
  private final ArrayList<V10chunk> v10chunks = new ArrayList<V10chunk>();
  // CVS i think that is current server version o something similar at regen time
  private final HashMap<String, Long> cvs = new HashMap<String, Long>();
  // PIW is Player in End World
  private final HashMap<String, Integer> piw = new HashMap<String, Integer>();
  public final static HashSet<String> reg = new HashSet<String>();
  
  private final HashMap<String, Integer> pids = new HashMap<String, Integer>();
  private long it = 1200;
  
  public static final boolean debugging = true;
  
  public void onEnable()
  {
	Server s = getServer();
	Logger log = s.getLogger();
	PluginDescriptionFile pdf = getDescription();
	try
	{
	  File f = new File("plugins/EndReset.sav");
	  if(!f.exists())
	  {
		(new File("plugins")).mkdir();
	    f.createNewFile();
	  }
	  else
	  {
	    ObjectInputStream in = new ObjectInputStream(new FileInputStream(f));
	    Object o = in.readObject();
	    in.close();
	    if(o == null || !(o instanceof Object[]))
	    {
	      log.info("["+pdf.getName()+"] ERROR: can't read savefile!");
	      s.getPluginManager().disablePlugin(this);
	      return;
	    }
	    Object[] sa = (Object[])o;
	    for(V10chunk vc: (ArrayList<V10chunk>)sa[1])
	      v10chunks.add(vc);
	    for(Entry<String, Long> e: ((HashMap<String, Long>)sa[2]).entrySet())
	      cvs.put(e.getKey(), e.getValue());
	    for(Entry<String, Integer> e: ((HashMap<String, Integer>)sa[3]).entrySet())
	      piw.put(e.getKey(), e.getValue());
	    for(String regen: (HashSet<String>)sa[4])
	      reg.add(regen);
	    it = (Long)sa[5];
	  }
	}
	catch(Exception e)
	{
	  log.info("["+pdf.getName()+"] can't read savefile!");
	  e.printStackTrace();
	  s.getPluginManager().disablePlugin(this);
	  return;
	}
	
	for(World w: s.getWorlds())
	{
	  if(w.getEnvironment() != Environment.THE_END)
		continue;
	  String wn = w.getName();
	  if(!cvs.containsKey(wn))
		cvs.put(wn, Long.MIN_VALUE);
	  if(!piw.containsKey(wn))
		piw.put(wn, w.getPlayers().size());
	  if(w.getKeepSpawnInMemory())
		w.setKeepSpawnInMemory(false);
	}
	
	s.getScheduler().scheduleSyncRepeatingTask(this, new SaveThread(), 36000L, 36000L);
	
	PluginManager pm = s.getPluginManager();
	ERPL pL = new ERPL();
	ERWL wL = new ERWL();
	pm.registerEvent(Event.Type.PLAYER_TELEPORT, pL, Event.Priority.Monitor, this);
	pm.registerEvent(Event.Type.PLAYER_JOIN, pL, Event.Priority.Monitor, this);
	pm.registerEvent(Event.Type.PLAYER_QUIT, pL, Event.Priority.Monitor, this);
	pm.registerEvent(Event.Type.CHUNK_LOAD, wL, Event.Priority.Highest, this);
	pm.registerEvent(Event.Type.WORLD_LOAD, wL, Event.Priority.Highest, this);
	pm.registerEvent(Event.Type.ENTITY_DEATH, new EndResetEntitylistener(), Event.Priority.Monitor, this);
	
	log.info("["+pdf.getName()+"] v"+pdf.getVersion()+" enabled!");
  }
  
  public void onDisable()
  {
	Server s = getServer();
	BukkitScheduler bs = s.getScheduler();
	for(BukkitTask bt: bs.getPendingTasks())
	{
	  if(bt instanceof RegenThread)
		((RegenThread)bt).run();
	}
	s.getScheduler().cancelTasks(this);
	(new SaveThread()).run();
	s.getLogger().info("["+getDescription().getName()+"] disabled!");
  }
  
  public boolean onCommand(CommandSender sender, Command command,
			String label, String[] args)
  {
	if(!sender.hasPermission("endreset.config"))
	  return true;
	if(args.length < 1)
	{
	  if(!(sender instanceof Player))
		return true;
	  World world = ((Player)sender).getWorld();
	  if(world.getEnvironment() != Environment.THE_END)
	  {
		sender.sendMessage(ChatColor.RED+"Not an end world!");
		return true;
	  }
	  String wn = world.getName();
          EndReset.debug("[onCommnad World Name] "+wn);
	  long cv = cvs.get(wn) + 1;
	  if(cv == Long.MAX_VALUE)
		cv = Long.MIN_VALUE;
	  cvs.put(wn, cv);
          EndReset.debug("[onCommnad World Num Players] "+world.getPlayers());
	  for(Player p: world.getPlayers())
	  {
          EndReset.debug("[onCommnad Player teleport is] "+getServer().getWorlds().get(0).getSpawnLocation()+" [Player name] "+p.getName());
		p.teleport(getServer().getWorlds().get(0).getSpawnLocation());
		p.sendMessage("This world is resetting!");
	  }
          EndReset.debug("[onCommand Entity?] "+world.getEntities());
	  for(Entity e: world.getEntities())
	    e.remove();
	  world.spawnCreature(new Location(world, 0, world.getMaxHeight(), 0), CreatureType.ENDER_DRAGON);
	  reg.remove(wn);
	  pids.remove(wn);
	  return true;
	}
	try
	{
	  it = Integer.parseInt(args[0]);
	}
	catch(NumberFormatException e)
	{
	  return false;
	}
	sender.sendMessage("New incative time: "+it+" minutes");
	it = it * 20 * 60;
	return true;
  }
  
  private void substractPlayer(World world)
  {
	String wn = world.getName();
	int pc = piw.get(wn) - 1;
	if(pc < 1 && reg.contains(wn))
	  pids.put(wn, getServer().getScheduler().scheduleSyncDelayedTask(this, new RegenThread(wn), it));
	piw.put(wn, pc);
  }
  
  private void cancellRegen(String wn)
  {
	int pid = pids.get(wn);
	BukkitScheduler s = getServer().getScheduler();
	if(s.isCurrentlyRunning(pid))
	  return;
	s.cancelTask(pid);
	pids.remove(wn);
  }
  
  private class RegenThread implements Runnable
  {
	private final String wn;
	
	private RegenThread(String wn)
	{
	  this.wn = wn;
	}
	
	public void run()
	{
	  World world = getServer().getWorld(wn);
	  long cv = cvs.get(wn) + 1;
	  if(cv == Long.MAX_VALUE)
		cv = Long.MIN_VALUE;
	  cvs.put(wn, cv);
	  world.spawnCreature(new Location(world, 0, world.getMaxHeight(), 0), CreatureType.ENDER_DRAGON);
	  reg.remove(wn);
	  pids.remove(wn);
	}
  }
  
  // Debug method return debug message on minecraft console
  public static void debug(String message) {
	  if (EndReset.debugging) {
                  Logger log = Logger.getLogger("Minecraft");
		  log.info(message);
	  }
  }
  
  private class SaveThread implements Runnable
  {
	public void run()
	{
	  Object[] sm = new Object[6];
	  sm[0] = 1;			// Savefile version
	  sm[1] = v10chunks;
	  sm[2] = cvs;
	  sm[3] = piw;
	  sm[4] = reg;
	  sm[5] = it;
	  try
	  {
		ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("plugins/EndReset.sav"));
		out.writeObject(sm);
		out.flush();
		out.close();
	  }
	  catch(Exception e)
	  {
		getServer().getLogger().info("["+getDescription().getName()+"] can't write savefile!");
		e.printStackTrace();
	  }
	}
  }
  
  
  private class ERPL extends PlayerListener
  {
	public void onPlayerTeleport(PlayerTeleportEvent event)
	{
	  if(event.isCancelled())
		return;
	  
	  World to = event.getTo().getWorld();
	  World from = event.getFrom().getWorld();
	  
	  if(to.equals(from))
		return;
	  EndReset.debug("[onPlaterTeleport current Envo] "+to.getEnvironment());
	  if(to.getEnvironment() == Environment.THE_END)
	  {
            EndReset.debug("[OnPlayerTeleport to World Name] "+to.getName());
	    String wn = to.getName();
	    piw.put(wn, piw.get(wn) + 1);
	    if(pids.containsKey(wn))
	      cancellRegen(wn);
	  }
	  if(from.getEnvironment() == Environment.THE_END)
	    substractPlayer(from);
	}
	
	public void onPlayerJoin(PlayerJoinEvent event)
	{
	  Player p = event.getPlayer();
	  if(p == null)
		return;
	  World w = p.getWorld();
	  if(w.getEnvironment() != Environment.THE_END)
		return;
	  String wn = w.getName();
	  piw.put(wn, piw.get(wn) + 1);
	  if(pids.containsKey(wn))
	    cancellRegen(wn);
	}
	
	public void onPlayerQuit(PlayerQuitEvent event)
	{
	  World w = event.getPlayer().getWorld();
	  if(w.getEnvironment() != Environment.THE_END)
		return;
	  substractPlayer(w);
	}
  }
  
  private class ERWL extends WorldListener
  {
	public void onChunkLoad(ChunkLoadEvent event)
	{
	  if(event.getWorld().getEnvironment() != Environment.THE_END)
		return;
	  
	  World world = event.getWorld();
	  String wn = world.getName();
	  
	  long cv;
	  if(cvs.containsKey(wn))
		cv = cvs.get(wn);
	  else
	  {
		cv = 0;
		cvs.put(wn, cv);
	  }
	  
	  Chunk chunk = event.getChunk();
	  int x = chunk.getX();
	  int z = chunk.getZ();
	  V10chunk v10chunk = new V10chunk(wn, x, z);
	  Iterator<V10chunk> iter = v10chunks.iterator();
	  boolean newChunk = true;
	  while(iter.hasNext())
	  {
		V10chunk tmp = iter.next();
		if(!tmp.equals(v10chunk))
		  continue;
		newChunk = false;
		if(tmp.v != cv)
		{
		  world.regenerateChunk(x, z);
		  tmp.v = cv;
		}
		break;
	  }
	  if(newChunk)
	  {
		v10chunk.v = cv;
		v10chunks.add(v10chunk);
	  }
	}
	
	public void onWorldLoad(WorldLoadEvent event)
	{
	  World w = event.getWorld();
	  if(w.getEnvironment() != Environment.THE_END)
		return;
	  String wn = w.getName();
	  if(!cvs.containsKey(wn))
		cvs.put(wn, Long.MIN_VALUE);
	  if(!piw.containsKey(wn))
		piw.put(wn, w.getPlayers().size());
	  if(w.getKeepSpawnInMemory())
		w.setKeepSpawnInMemory(false);
	}
  }
  
}
