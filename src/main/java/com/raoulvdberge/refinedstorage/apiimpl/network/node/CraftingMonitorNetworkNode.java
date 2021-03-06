package com.raoulvdberge.refinedstorage.apiimpl.network.node;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingManager;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.ICraftingTask;
import com.raoulvdberge.refinedstorage.block.CraftingMonitorBlock;
import com.raoulvdberge.refinedstorage.block.NetworkNodeBlock;
import com.raoulvdberge.refinedstorage.tile.craftingmonitor.CraftingMonitorTile;
import com.raoulvdberge.refinedstorage.tile.craftingmonitor.ICraftingMonitor;
import com.raoulvdberge.refinedstorage.tile.data.TileDataManager;
import com.raoulvdberge.refinedstorage.tile.data.TileDataParameter;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

public class CraftingMonitorNetworkNode extends NetworkNode implements ICraftingMonitor {
    public static final ResourceLocation ID = new ResourceLocation(RS.ID, "crafting_monitor");

    private static final String NBT_TAB_SELECTED = "TabSelected";
    private static final String NBT_TAB_PAGE = "TabPage";

    private Optional<UUID> tabSelected = Optional.empty();
    private int tabPage;

    public CraftingMonitorNetworkNode(World world, BlockPos pos) {
        super(world, pos);
    }

    @Override
    public int getEnergyUsage() {
        return RS.SERVER_CONFIG.getCraftingMonitor().getUsage();
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public ITextComponent getTitle() {
        return new TranslationTextComponent("gui.refinedstorage.crafting_monitor");
    }

    @Override
    public void onCancelled(ServerPlayerEntity player, @Nullable UUID id) {
        if (network != null) {
            network.getItemGridHandler().onCraftingCancelRequested(player, id);
        }
    }

    @Override
    public TileDataParameter<Integer, ?> getRedstoneModeParameter() {
        return CraftingMonitorTile.REDSTONE_MODE;
    }

    @Override
    public Collection<ICraftingTask> getTasks() {
        return network != null ? network.getCraftingManager().getTasks() : Collections.emptyList();
    }

    @Nullable
    @Override
    public ICraftingManager getCraftingManager() {
        return network != null ? network.getCraftingManager() : null;
    }

    @Override
    public boolean isActive() {
        BlockState state = world.getBlockState(pos);

        if (state.getBlock() instanceof CraftingMonitorBlock) {
            return state.get(NetworkNodeBlock.CONNECTED);
        }

        return false;
    }

    @Override
    public CompoundNBT write(CompoundNBT tag) {
        super.write(tag);

        tag.putInt(NBT_TAB_PAGE, tabPage);

        if (tabSelected.isPresent()) {
            tag.putUniqueId(NBT_TAB_SELECTED, tabSelected.get());
        }

        return tag;
    }

    @Override
    public void read(CompoundNBT tag) {
        super.read(tag);

        if (tag.contains(NBT_TAB_PAGE)) {
            tabPage = tag.getInt(NBT_TAB_PAGE);
        }

        if (tag.hasUniqueId(NBT_TAB_SELECTED)) {
            tabSelected = Optional.of(tag.getUniqueId(NBT_TAB_SELECTED));
        }
    }

    public void setTabSelected(Optional<UUID> tabSelected) {
        this.tabSelected = tabSelected;
    }

    public void setTabPage(int tabPage) {
        this.tabPage = tabPage;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        // NO OP
    }

    @Override
    public Optional<UUID> getTabSelected() {
        return world.isRemote ? CraftingMonitorTile.TAB_SELECTED.getValue() : tabSelected;
    }

    @Override
    public int getTabPage() {
        return world.isRemote ? CraftingMonitorTile.TAB_PAGE.getValue() : tabPage;
    }

    @Override
    public void onTabSelectionChanged(Optional<UUID> tab) {
        TileDataManager.setParameter(CraftingMonitorTile.TAB_SELECTED, tab);
    }

    @Override
    public void onTabPageChanged(int page) {
        if (page >= 0) {
            TileDataManager.setParameter(CraftingMonitorTile.TAB_PAGE, page);
        }
    }

    @Override
    public int getSlotId() {
        return -1;
    }
}
