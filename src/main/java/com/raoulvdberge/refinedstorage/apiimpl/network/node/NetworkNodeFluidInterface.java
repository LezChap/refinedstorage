package com.raoulvdberge.refinedstorage.apiimpl.network.node;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.network.node.INetworkNode;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.storage.externalstorage.StorageExternalFluid;
import com.raoulvdberge.refinedstorage.inventory.fluid.FluidHandlerProxy;
import com.raoulvdberge.refinedstorage.inventory.fluid.FluidInventory;
import com.raoulvdberge.refinedstorage.inventory.item.ItemHandlerBase;
import com.raoulvdberge.refinedstorage.inventory.item.ItemHandlerUpgrade;
import com.raoulvdberge.refinedstorage.inventory.listener.ListenerNetworkNode;
import com.raoulvdberge.refinedstorage.item.ItemUpgrade;
import com.raoulvdberge.refinedstorage.tile.TileFluidInterface;
import com.raoulvdberge.refinedstorage.tile.config.IType;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import org.apache.commons.lang3.tuple.Pair;

public class NetworkNodeFluidInterface extends NetworkNode {
    public static final String ID = "fluid_interface";

    public static final int TANK_CAPACITY = 16_000;

    private static final String NBT_TANK_IN = "TankIn";
    private static final String NBT_TANK_OUT = "TankOut";
    private static final String NBT_OUT = "Out";

    private FluidTank tankIn = new FluidTank(TANK_CAPACITY) {
        @Override
        protected void onContentsChanged() {
            super.onContentsChanged();

            if (!world.isRemote) {
                ((TileFluidInterface) world.getTileEntity(pos)).getDataManager().sendParameterToWatchers(TileFluidInterface.TANK_IN);
            }

            markDirty();
        }
    };
    private FluidTank tankOut = new FluidTank(TANK_CAPACITY);

    private FluidHandlerProxy tank = new FluidHandlerProxy(tankIn, tankOut);

    private ItemHandlerBase in = new ItemHandlerBase(1, new ListenerNetworkNode(this), stack -> StackUtils.getFluid(stack, true).getRight() != null);
    private FluidInventory out = new FluidInventory(1, TANK_CAPACITY, new ListenerNetworkNode(this));

    private ItemHandlerUpgrade upgrades = new ItemHandlerUpgrade(4, new ListenerNetworkNode(this)/* TODO, ItemUpgrade.TYPE_SPEED, ItemUpgrade.TYPE_STACK, ItemUpgrade.TYPE_CRAFTING*/);

    public NetworkNodeFluidInterface(World world, BlockPos pos) {
        super(world, pos);
    }

    @Override
    public void update() {
        super.update();

        if (canUpdate()) {
            ItemStack container = in.getStackInSlot(0);

            if (!container.isEmpty()) {
                Pair<ItemStack, FluidStack> result = StackUtils.getFluid(container, true);

                if (result.getValue() != null && tankIn.fill(result.getValue(), IFluidHandler.FluidAction.SIMULATE) == result.getValue().getAmount()) {
                    result = StackUtils.getFluid(container, false);

                    tankIn.fill(result.getValue(), IFluidHandler.FluidAction.EXECUTE);

                    in.setStackInSlot(0, result.getLeft());
                }
            }

            if (ticks % upgrades.getSpeed() == 0) {
                FluidStack drained = tankIn.drain(FluidAttributes.BUCKET_VOLUME * upgrades.getItemInteractCount(), IFluidHandler.FluidAction.EXECUTE);

                // Drain in tank
                if (drained != null) {
                    FluidStack remainder = network.insertFluidTracked(drained, drained.getAmount());

                    if (remainder != null) {
                        tankIn.fill(remainder, IFluidHandler.FluidAction.EXECUTE);
                    }
                }
            }

            FluidStack wanted = out.getFluid(0);
            FluidStack got = tankOut.getFluid();

            if (wanted == null) {
                if (got != null) {
                    tankOut.setFluid(network.insertFluidTracked(got, got.getAmount()));

                    onTankOutChanged();
                }
            } else if (got != null && !API.instance().getComparer().isEqual(wanted, got, IComparer.COMPARE_NBT)) {
                tankOut.setFluid(network.insertFluidTracked(got, got.getAmount()));

                onTankOutChanged();
            } else {
                int delta = got == null ? wanted.getAmount() : (wanted.getAmount() - got.getAmount());

                if (delta > 0) {
                    final boolean actingAsStorage = isActingAsStorage();

                    FluidStack result = network.extractFluid(wanted, delta, IComparer.COMPARE_NBT, Action.PERFORM, s -> {
                        // If we are not an interface acting as a storage, we can extract from anywhere.
                        if (!actingAsStorage) {
                            return true;
                        }

                        // If we are an interface acting as a storage, we don't want to extract from other interfaces to
                        // avoid stealing from each other.
                        return !(s instanceof StorageExternalFluid) || !((StorageExternalFluid) s).isConnectedToInterface();
                    });

                    if (result != null) {
                        if (tankOut.getFluid() == null) {
                            tankOut.setFluid(result);
                        } else {
                            tankOut.getFluid().grow(result.getAmount());
                        }

                        onTankOutChanged();
                    }

                    // Example: our delta is 5, we extracted 3 fluids.
                    // That means we still have to autocraft 2 fluids.
                    delta -= result == null ? 0 : result.getAmount();

                    if (delta > 0 && upgrades.hasUpgrade(ItemUpgrade.Type.CRAFTING)) {
                        network.getCraftingManager().request(this, wanted, delta);
                    }
                } else if (delta < 0) {
                    FluidStack remainder = network.insertFluidTracked(got, Math.abs(delta));

                    if (remainder == null) {
                        tankOut.getFluid().shrink(Math.abs(delta));
                    } else {
                        tankOut.getFluid().shrink(Math.abs(delta) - remainder.getAmount());
                    }

                    onTankOutChanged();
                }
            }
        }
    }

    private boolean isActingAsStorage() {
        for (Direction facing : Direction.values()) {
            INetworkNode facingNode = API.instance().getNetworkNodeManager(world).getNode(pos.offset(facing));

            if (facingNode instanceof NetworkNodeExternalStorage &&
                facingNode.canUpdate() &&
                ((NetworkNodeExternalStorage) facingNode).getDirection() == facing.getOpposite() &&
                ((NetworkNodeExternalStorage) facingNode).getType() == IType.FLUIDS) {
                return true;
            }
        }

        return false;
    }

    @Override
    public int getEnergyUsage() {
        return RS.INSTANCE.config.fluidInterfaceUsage;
    }

    @Override
    public CompoundNBT write(CompoundNBT tag) {
        super.write(tag);

        StackUtils.writeItems(upgrades, 0, tag);
        StackUtils.writeItems(in, 1, tag);

        tag.put(NBT_TANK_IN, tankIn.writeToNBT(new CompoundNBT()));
        tag.put(NBT_TANK_OUT, tankOut.writeToNBT(new CompoundNBT()));

        return tag;
    }

    @Override
    public void read(CompoundNBT tag) {
        super.read(tag);

        StackUtils.readItems(upgrades, 0, tag);
        StackUtils.readItems(in, 1, tag);

        if (tag.contains(NBT_TANK_IN)) {
            tankIn.readFromNBT(tag.getCompound(NBT_TANK_IN));
        }

        if (tag.contains(NBT_TANK_OUT)) {
            tankOut.readFromNBT(tag.getCompound(NBT_TANK_OUT));
        }
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public CompoundNBT writeConfiguration(CompoundNBT tag) {
        super.writeConfiguration(tag);

        tag.put(NBT_OUT, out.writeToNbt());

        return tag;
    }

    @Override
    public void readConfiguration(CompoundNBT tag) {
        super.readConfiguration(tag);

        if (tag.contains(NBT_OUT)) {
            out.readFromNbt(tag.getCompound(NBT_OUT));
        }
    }

    public ItemHandlerUpgrade getUpgrades() {
        return upgrades;
    }

    public ItemHandlerBase getIn() {
        return in;
    }

    public FluidInventory getOut() {
        return out;
    }

    public FluidHandlerProxy getTank() {
        return tank;
    }

    public FluidTank getTankIn() {
        return tankIn;
    }

    public FluidTank getTankOut() {
        return tankOut;
    }

    private void onTankOutChanged() {
        if (!world.isRemote) {
            ((TileFluidInterface) world.getTileEntity(pos)).getDataManager().sendParameterToWatchers(TileFluidInterface.TANK_OUT);
        }

        markDirty();
    }

    @Override
    public IItemHandler getDrops() {
        return new CombinedInvWrapper(in, upgrades);
    }

    @Override
    public boolean hasConnectivityState() {
        return true;
    }
}
