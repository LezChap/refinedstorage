package com.raoulvdberge.refinedstorage.inventory.item;

import com.raoulvdberge.refinedstorage.inventory.item.validator.ItemValidatorBasic;
import com.raoulvdberge.refinedstorage.inventory.item.validator.ItemValidatorUpgrade;
import com.raoulvdberge.refinedstorage.item.ItemUpgrade;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public class ItemHandlerUpgrade extends ItemHandlerBase {
    public ItemHandlerUpgrade(int size, @Nullable Consumer<Integer> listener, ItemUpgrade.Type... supportedUpgrades) {
        super(size, listener, new ItemValidatorBasic[supportedUpgrades.length]);

        for (int i = 0; i < supportedUpgrades.length; ++i) {
            this.validators[i] = new ItemValidatorUpgrade(supportedUpgrades[i]);
        }
    }

    public int getSpeed() {
        return getSpeed(9, 2);
    }

    public int getSpeed(int speed, int speedIncrease) {
        for (int i = 0; i < getSlots(); ++i) {
            ItemStack slot = getStackInSlot(i);

            if (slot.getItem() instanceof ItemUpgrade && ((ItemUpgrade) slot.getItem()).getType() == ItemUpgrade.Type.SPEED) {
                speed -= speedIncrease;
            }
        }

        return speed;
    }

    public boolean hasUpgrade(ItemUpgrade.Type type) {
        for (int i = 0; i < getSlots(); ++i) {
            ItemStack slot = getStackInSlot(i);

            if (slot.getItem() instanceof ItemUpgrade && ((ItemUpgrade) slot.getItem()).getType() == type) {
                return true;
            }
        }

        return false;
    }

    public int getUpgradeCount(ItemUpgrade.Type type) {
        int upgrades = 0;

        for (int i = 0; i < getSlots(); ++i) {
            ItemStack slot = getStackInSlot(i);

            if (slot.getItem() instanceof ItemUpgrade && ((ItemUpgrade) slot.getItem()).getType() == type) {
                upgrades++;
            }
        }

        return upgrades;
    }

    public int getEnergyUsage() {
        int usage = 0;

        for (int i = 0; i < getSlots(); ++i) {
            ItemStack slot = getStackInSlot(i);

            if (slot.getItem() instanceof ItemUpgrade) {
                usage += ((ItemUpgrade) slot.getItem()).getType().getEnergyUsage();
            }
        }

        return usage;
    }

    public int getFortuneLevel() {
        int maxFortune = 0;

        for (int i = 0; i < getSlots(); ++i) {
            ItemStack slot = getStackInSlot(i);

            if (slot.getItem() instanceof ItemUpgrade) {
                int fortune = ((ItemUpgrade) slot.getItem()).getType().getFortuneLevel();

                if (fortune > maxFortune) {
                    maxFortune = fortune;
                }
            }
        }

        return maxFortune;
    }

    public int getItemInteractCount() {
        return hasUpgrade(ItemUpgrade.Type.STACK) ? 64 : 1;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }
}
