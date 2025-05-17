package net.maninthebag.leashableFences

import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Bat
import org.bukkit.entity.EntityType
import org.bukkit.entity.LeashHitch
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityUnleashEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.BoundingBox
import kotlin.collections.get

class ItemClickListener(private val plugin: JavaPlugin) : Listener {

    private val activeLeashes = mutableMapOf<LeashHitch, Bat>()
    private val activeBats = mutableMapOf<Bat, LeashHitch>()

    private val leashMap = mutableMapOf<Player, LeashHitch>()

    private val playerMap = mutableMapOf<Bat, Player>()
    private val batMap = mutableMapOf<Player, Bat>()

    private val leashes = mutableMapOf<Location, LeashHitch>()

    @EventHandler
    fun onPlayerClick(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return

        val player = event.player
        val clicked = event.clickedBlock ?: return

        if (player.isSneaking) return
        if (event.action != Action.RIGHT_CLICK_BLOCK || event.clickedBlock?.type?.name?.endsWith("_FENCE") == false) return

        if (event.item?.type != Material.LEAD && leashMap[event.player] == null) return

        event.isCancelled = true

        val leashLoc = clicked.location.add(0.5,0.0,0.5)

        if (!leashMap.containsKey(player)) {
            val loc = clicked.location
            val boundingBox = BoundingBox(
                loc.x, loc.y, loc.z,
                loc.x + 1, loc.y + 1, loc.z + 1
            )

            val areaLeashes = clicked.world.getNearbyEntities(boundingBox).filterIsInstance<LeashHitch>()
            for (leash in areaLeashes) {
                leash.remove()
            }

            val hitch = clicked.world.spawn(leashLoc, LeashHitch::class.java)

            val bat = clicked.world.spawn(leashLoc, Bat::class.java) {
                it.isInvisible = true
                it.isSilent = true
                it.isInvulnerable = true
                it.setAI(false)
                it.leashHolder = hitch
            }

            activeLeashes[hitch] = bat
            leashMap[player] = hitch
            leashes[hitch.location] = hitch
            playerMap[bat] = player
            batMap[player] = bat
            if (player.gameMode != GameMode.CREATIVE) {
                for (itemStack in player.inventory) {
                    if (itemStack == null || itemStack.type != Material.LEAD) {
                        continue
                    }
                    itemStack.amount = itemStack.amount - 1
                    break
                }
            }

            object  : BukkitRunnable() {
                override fun run() {
                    if (!bat.isValid || !player.isOnline) {
                        bat.remove()
                        cancel()
                        return
                    }

                    bat.teleport(player.location)
                }
            }.runTaskTimer(plugin, 0L, 1L)
        } else {
            val firstLoc = leashMap.remove(player) ?: return
            activeLeashes[firstLoc]?.remove()

            val hitch1 = clicked.world.spawn(firstLoc.location, LeashHitch::class.java)
            clicked.world.spawn(leashLoc, LeashHitch::class.java)

            val bat = clicked.world.spawn(leashLoc, Bat::class.java) {
                it.isInvisible = true
                it.isSilent = true
                it.setAI(false)
                it.isInvulnerable = true
                it.setLeashHolder(hitch1)
                it.isPersistent = true
                it.removeWhenFarAway = false
            }

            bat.teleport(leashLoc)

            val dirBetweenLocations = hitch1.location.toVector().subtract(bat.location.toVector()).multiply(-1)
            val loc = bat.location
            loc.direction = dirBetweenLocations

            bat.teleport(loc)

            activeLeashes[hitch1] = bat
            activeBats[bat] = hitch1
            playerMap.remove(batMap[player])

            firstLoc.remove()
        }
    }

    @EventHandler
    fun onEntityInteract(event: PlayerInteractEntityEvent) {
        if (event.rightClicked.type == EntityType.BAT && event.player.inventory.itemInMainHand.type != Material.LEAD && !leashMap.containsKey(event.player)) {
            if (event.player.gameMode != GameMode.CREATIVE) {
            event.rightClicked.world.dropItemNaturally(event.rightClicked.location, ItemStack(Material.LEAD, 1))
            }
            activeBats[event.rightClicked]?.remove()
            event.rightClicked.remove()
        }
    }

    @EventHandler
    fun onLeadBreak(event: EntityUnleashEvent) {
        if (playerMap[event.entity]?.inventory?.itemInMainHand?.type == Material.LEAD || leashMap.containsKey(playerMap[event.entity])) {event.isCancelled = true; return}
        if (event.entity.type == EntityType.BAT && playerMap[event.entity] != null) {
            leashMap.remove(playerMap[event.entity])
            event.entity.remove()
        }
    }

    @EventHandler
    fun entityRemoved(event: EntityUnleashEvent) {
        if (event.entity is LeashHitch) {
            val hitch = event.entity
            val nearbyItems = hitch.world.getNearbyEntities(hitch.location, 1.0,1.0,1.0)
                .filterIsInstance<org.bukkit.entity.Item>()
                .filter { it.itemStack.type == Material.LEAD }

            nearbyItems.forEach { it.remove() }
        }
    }
}
