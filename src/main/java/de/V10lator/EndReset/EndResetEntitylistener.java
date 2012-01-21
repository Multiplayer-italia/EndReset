/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.V10lator.EndReset;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityListener;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.World;
import org.bukkit.World.Environment;

/**
 *
 * @author stefano
 */
public class EndResetEntitylistener extends EntityListener {

	public void onEntityDeath(EntityDeathEvent event)
	{
	  Entity e = event.getEntity();
	  if(!(e instanceof EnderDragon))
	    return;
	  World w = e.getWorld();
	  if(w.getEnvironment() != Environment.THE_END)
		return;
    
        EndReset.reg.add(w.getName());
	}
  
}
