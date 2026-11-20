package dev.auto_mine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;

final class InventoryMending implements Listener {
    private static final int DURABILITY_PER_XP = 2;
    private static final List<EquipmentSlot> EQUIPMENT_SLOTS = List.of(
            EquipmentSlot.OFF_HAND, EquipmentSlot.FEET,
            EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD);

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNativeMend(PlayerItemMendEvent event) {
        if (!(event instanceof RankedMendEvent) && event.getExperienceOrb() != null) {
            // Cancelling the native repair lets the full orb reach PlayerExpChangeEvent.
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExperienceChange(PlayerExpChangeEvent event) {
        int remaining = event.getAmount();
        if (remaining <= 0 || !(event.getSource() instanceof ExperienceOrb orb)) {
            return;
        }

        PlayerInventory inventory = event.getPlayer().getInventory();
        for (RepairCandidate candidate : findCandidates(inventory)) {
            if (remaining == 0) {
                break;
            }
            ItemStack original = candidate.item();
            if (!original.equals(candidate.currentItem(inventory))) {
                continue;
            }

            Damageable damageable = candidate.damageable();
            int damage = damageable.getDamage();
            int maxRepair = (int) Math.min(damage, (long) remaining * DURABILITY_PER_XP);
            PlayerItemMendEvent mend = new RankedMendEvent(event, candidate, orb, maxRepair);
            if (!mend.callEvent() || !original.equals(candidate.currentItem(inventory))) {
                continue;
            }

            int repair = Math.clamp(mend.getRepairAmount(), 0, maxRepair);
            if (repair > 0) {
                damageable.setDamage(damage - repair);
                original.setItemMeta(damageable);
                candidate.updateItem(inventory);
                // Match vanilla Mending's integer rounding for odd repairs.
                remaining -= repair / DURABILITY_PER_XP;
            }
        }
        event.setAmount(remaining);
    }

    private List<RepairCandidate> findCandidates(PlayerInventory inventory) {
        List<RepairCandidate> candidates = new ArrayList<>();
        int storageSize = inventory.getStorageContents().length;
        int heldSlot = inventory.getHeldItemSlot();
        for (int slot = 0; slot < storageSize; slot++) {
            EquipmentSlot equipmentSlot = slot == heldSlot ? EquipmentSlot.HAND : null;
            addCandidate(candidates, slot, equipmentSlot, inventory.getItem(slot));
        }
        for (EquipmentSlot equipmentSlot : EQUIPMENT_SLOTS) {
            addCandidate(candidates, -1, equipmentSlot, inventory.getItem(equipmentSlot));
        }
        candidates.sort(Comparator.comparingInt(RepairCandidate::damage).reversed());
        return candidates;
    }

    private void addCandidate(List<RepairCandidate> candidates, int slot,
                              EquipmentSlot equipmentSlot, ItemStack item) {
        if (item != null && item.containsEnchantment(Enchantment.MENDING)
                && item.getItemMeta() instanceof Damageable damageable && damageable.hasDamage()) {
            candidates.add(new RepairCandidate(slot, equipmentSlot, item.clone(), damageable));
        }
    }

    private record RepairCandidate(int slot, EquipmentSlot equipmentSlot, ItemStack item,
                                   Damageable damageable) {
        private int damage() {
            return damageable.getDamage();
        }

        private ItemStack currentItem(PlayerInventory inventory) {
            return slot >= 0 ? inventory.getItem(slot) : inventory.getItem(equipmentSlot);
        }

        private void updateItem(PlayerInventory inventory) {
            if (slot >= 0) {
                inventory.setItem(slot, item);
            } else {
                inventory.setItem(equipmentSlot, item);
            }
        }
    }

    private static final class RankedMendEvent extends PlayerItemMendEvent {
        private RankedMendEvent(PlayerExpChangeEvent event, RepairCandidate candidate,
                                ExperienceOrb orb, int repair) {
            super(event.getPlayer(), candidate.item().clone(), candidate.equipmentSlot(),
                    orb, repair, repair / DURABILITY_PER_XP);
        }
    }
}
