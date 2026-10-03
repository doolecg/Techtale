package com.doolecg.techtale.hytale.machine;

import com.doolecg.techtale.TechtalePlugin;
import com.doolecg.techtale.core.machine.MachineDefinition;
import com.doolecg.techtale.core.machine.MachineDefinition.SlotRole;
import com.doolecg.techtale.core.machine.MachineState;
import com.doolecg.techtale.core.machine.Machines;
import com.doolecg.techtale.core.resource.ResourceBuffer;
import com.doolecg.techtale.core.resource.ResourceKind;
import com.doolecg.techtale.hytale.ui.MachineSlotRules;
import com.doolecg.techtale.hytale.ui.UiFormat;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Arrays;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A machine's screen: item slots grouped by role, energy / progress / buffer bars and the player's inventory, with
 * drag and drop in both directions. Opened by {@link OpenMachineInteraction}; refreshed from the machine tick.
 */
public class MachinePage extends InteractiveCustomUIPage<MachinePage.PageEvent> {
    public static final String UI = "Pages/Techtale/Machine.ui";
    private static final long REFRESH_MS = 250;
    private static final int MAX_TANKS = 3;
    /** A drop only counts when its drag-completed event follows this soon. */
    private static final long DROP_WINDOW_MS = 2000;

    private static final String ACTION_DROPPED = "Dropped";
    private static final String ACTION_DRAG_DONE = "DragDone";

    /** The item grids on the page. Machine grids show the slots of one role, in {@code def.slots()} order. */
    private enum Grid {
        INPUT("#InputGrid", SlotRole.INPUT),
        EXTRA("#ExtraGrid", SlotRole.EXTRA),
        OUTPUT("#OutputGrid", SlotRole.OUTPUT),
        FUEL("#FuelGrid", SlotRole.FUEL),
        UPGRADE("#UpgradeGrid", SlotRole.UPGRADE),
        PLAYER("#PlayerGrid", null);

        static final Grid[] VALUES = values();
        final String selector;
        @Nullable
        final SlotRole role;

        Grid(String selector, @Nullable SlotRole role) {
            this.selector = selector;
            this.role = role;
        }

        @Nullable
        static Grid byName(@Nullable String name) {
            if (name == null) {
                return null;
            }
            for (Grid g : VALUES) {
                if (g.name().equals(name)) {
                    return g;
                }
            }
            return null;
        }
    }

    /** A resolved slot: a container and the slot index inside it. */
    private record Slot(Grid grid, ItemContainer container, short index) {
        ItemStack stack() {
            return container.getItemStack(index);
        }
    }

    /** The target half of a drag, remembered until the source grid reports the drag finished. */
    private record Drop(Grid target, int toSlot, int fromSlot, int quantity, long time) {
    }

    private final Ref<ChunkStore> blockRef;
    private final MachineBlock machine;
    private long lastRefresh;
    @Nullable
    private Drop pendingDrop;
    private final int[] gridSignatures = new int[Grid.VALUES.length];
    private long statusKey = Long.MIN_VALUE;

    public MachinePage(PlayerRef playerRef, Ref<ChunkStore> blockRef, MachineBlock machine) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageEvent.CODEC);
        this.blockRef = blockRef;
        this.machine = machine;
        Arrays.fill(gridSignatures, Integer.MIN_VALUE);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        cmd.append(UI);
        MachineDefinition def = machine.getDefinition();
        cmd.set("#MachineTitle.Text", Message.translation("techtale.items." + def.id() + ".name"));

        boolean cube = def.kind() == MachineDefinition.Kind.ENERGY_CUBE;
        boolean processor = def.kind() == MachineDefinition.Kind.PROCESSOR;
        cmd.set("#SlotRow.Visible", !def.slots().isEmpty());
        cmd.set("#InputBox.Visible", has(def, SlotRole.INPUT));
        cmd.set("#ExtraBox.Visible", has(def, SlotRole.EXTRA));
        cmd.set("#OutputBox.Visible", has(def, SlotRole.OUTPUT));
        cmd.set("#FuelBox.Visible", has(def, SlotRole.FUEL));
        cmd.set("#UpgradeBox.Visible", has(def, SlotRole.UPGRADE));
        cmd.set("#ProgressBox.Visible", processor);
        cmd.set("#BurnBox.Visible", def.kind() == MachineDefinition.Kind.HEAT_GENERATOR);
        cmd.set("#SunBox.Visible", def.kind() == MachineDefinition.Kind.SOLAR_GENERATOR);
        cmd.set("#EnergyBox.Visible", !cube && def.energyCapacity() > 0);
        cmd.set("#EnergyBigBox.Visible", cube);
        cmd.set("#SecondaryBox.Visible", def.usesSecondary());
        cmd.set("#ExtraLabel.Text", extraLabel(def));
        for (int i = 0; i < MAX_TANKS; i++) {
            boolean shown = i < def.tanks().size();
            boolean chemical = shown && def.tanks().get(i).kind() == ResourceKind.CHEMICAL;
            cmd.set("#Tank" + i + "Box.Visible", shown);
            cmd.set("#Tank" + i + "FluidFrame.Visible", shown && !chemical);
            cmd.set("#Tank" + i + "ChemicalFrame.Visible", chemical);
        }

        for (Grid grid : Grid.VALUES) {
            if (grid.role != null && !has(def, grid.role)) {
                continue;
            }
            events.addEventBinding(CustomUIEventBindingType.Dropped, grid.selector,
                new EventData().append("Action", ACTION_DROPPED).append("Target", grid.name()), false);
            events.addEventBinding(CustomUIEventBindingType.SlotMouseDragCompleted, grid.selector,
                new EventData().append("Action", ACTION_DRAG_DONE).append("Target", grid.name()), false);
        }

        writeStatus(cmd, machine);
        ItemContainer player = playerItems(ref, store);
        writeSlots(cmd, machine, player, true);
    }

    private static boolean has(MachineDefinition def, SlotRole role) {
        return def.slot(role) >= 0;
    }

    private static String extraLabel(MachineDefinition def) {
        if (def == Machines.METALLURGIC_INFUSER) {
            return "Infuse";
        }
        if (def == Machines.OSMIUM_COMPRESSOR) {
            return "Osmium";
        }
        return "Extra";
    }

    // Bars and text

    private static void writeStatus(UICommandBuilder cmd, MachineBlock m) {
        MachineDefinition def = m.getDefinition();
        MachineState state = m.getState();
        String energyText = UiFormat.energy(state.energy.getStored()) + " / " + UiFormat.energy(state.energy.getCapacity());
        float energyRatio = (float) state.energy.getFillRatio();
        if (def.kind() == MachineDefinition.Kind.ENERGY_CUBE) {
            cmd.set("#EnergyBigBar.Value", energyRatio);
            cmd.set("#EnergyBigText.Text", energyText);
        } else {
            cmd.set("#EnergyBar.Value", energyRatio);
            cmd.set("#EnergyText.Text", energyText);
        }
        switch (def.kind()) {
            case PROCESSOR -> cmd.set("#ProgressBar.Value", state.progressRatio());
            case HEAT_GENERATOR -> {
                cmd.set("#BurnBar.Value", state.burnTotal > 0 ? Math.min(1f, (float) state.burnTicks / state.burnTotal) : 0f);
                cmd.set("#BurnText.Text", state.burnTicks > 0 ? "Burning " + UiFormat.seconds(state.burnTicks) : "Idle");
            }
            case SOLAR_GENERATOR -> cmd.set("#SunText.Text", state.active
                ? "Producing " + UiFormat.energy(def.energyPerTick()) + "/t"
                : state.energy.getNeeded() <= 0 ? "Buffer full" : "No sun");
            case ENERGY_CUBE -> {
            }
        }
        for (int i = 0; i < def.tanks().size() && i < MAX_TANKS && i < state.tanks.length; i++) {
            ResourceBuffer tank = state.tanks[i];
            String bar = def.tanks().get(i).kind() == ResourceKind.CHEMICAL ? "Chemical" : "Fluid";
            cmd.set("#Tank" + i + bar + "Bar.Value", tank.getFillRatio());
            cmd.set("#Tank" + i + "Text.Text", UiFormat.typeName(tank.getType()) + "  " + UiFormat.amount(tank.getAmount(), tank.getCapacity()));
        }
        if (def.usesSecondary()) {
            String type = state.secondaryType != null ? state.secondaryType : def.secondaryType() != null ? def.secondaryType() : "empty";
            cmd.set("#SecondaryBar.Value", def.secondaryCapacity() > 0 ? Math.min(1f, (float) state.secondaryStored / def.secondaryCapacity()) : 0f);
            cmd.set("#SecondaryText.Text", type + " " + state.secondaryStored + " / " + def.secondaryCapacity());
        }
    }

    /** Cheap fingerprint of everything {@link #writeStatus} shows, so unchanged frames are not sent. */
    private static long statusKey(MachineBlock m) {
        MachineState s = m.getState();
        long h = s.energy.getStored();
        h = h * 31 + s.energy.getCapacity();
        h = h * 31 + s.progress;
        h = h * 31 + s.ticksRequired;
        h = h * 31 + s.secondaryStored;
        h = h * 31 + (s.secondaryType == null ? 0 : s.secondaryType.hashCode());
        h = h * 31 + s.burnTicks;
        h = h * 31 + s.burnTotal;
        for (ResourceBuffer tank : s.tanks) {
            h = h * 31 + tank.getAmount();
            h = h * 31 + tank.getCapacity();
            h = h * 31 + (tank.getType() == null ? 0 : tank.getType().hashCode());
        }
        return h * 31 + (s.active ? 1 : 0);
    }

    // Item grids

    private static int[] slotMap(MachineDefinition def, SlotRole role) {
        return def.slotsOf(role);
    }

    private static ItemGridSlot[] gridSlots(ItemContainer container, int[] map) {
        ItemGridSlot[] out = new ItemGridSlot[map.length];
        for (int i = 0; i < map.length; i++) {
            out[i] = new ItemGridSlot(container.getItemStack((short) map[i]));
            out[i].setActivatable(true);
        }
        return out;
    }

    private static int[] playerMap(ItemContainer player) {
        int[] map = new int[player.getCapacity()];
        for (int i = 0; i < map.length; i++) {
            map[i] = i;
        }
        return map;
    }

    private static int signature(ItemContainer container, int[] map) {
        int h = 1;
        for (int slot : map) {
            ItemStack s = container.getItemStack((short) slot);
            h = h * 31 + (ItemStack.isEmpty(s) ? 0 : s.getItemId().hashCode() * 31 + s.getQuantity());
        }
        return h;
    }

    /** Writes the slots of every grid whose contents changed since the last write (all of them when forced). */
    private boolean writeSlots(UICommandBuilder cmd, MachineBlock m, @Nullable ItemContainer player, boolean force) {
        boolean any = false;
        MachineDefinition def = m.getDefinition();
        for (Grid grid : Grid.VALUES) {
            ItemContainer container;
            int[] map;
            if (grid.role == null) {
                if (player == null) {
                    continue;
                }
                container = player;
                map = playerMap(player);
            } else {
                map = slotMap(def, grid.role);
                if (map.length == 0) {
                    continue;
                }
                container = m.getItems();
            }
            int sig = signature(container, map);
            if (force || sig != gridSignatures[grid.ordinal()]) {
                gridSignatures[grid.ordinal()] = sig;
                cmd.set(grid.selector + ".Slots", gridSlots(container, map));
                any = true;
            }
        }
        return any;
    }

    @Nullable
    private ItemContainer playerItems(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        return InventoryComponent.getCombined(store, ref, InventoryComponent.STORAGE_FIRST);
    }

    /** Called from the machine tick; refreshes bars and changed grids a few times a second. */
    void onMachineTick() {
        long now = System.currentTimeMillis();
        if (now - lastRefresh < REFRESH_MS) {
            return;
        }
        lastRefresh = now;
        if (!machine.isInitialized()) {
            return;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        ItemContainer player = ref == null ? null : playerItems(ref, ref.getStore());
        UICommandBuilder cmd = new UICommandBuilder();
        boolean changed = writeSlots(cmd, machine, player, false);
        long key = statusKey(machine);
        if (key != statusKey) {
            statusKey = key;
            writeStatus(cmd, machine);
            changed = true;
        }
        if (changed) {
            sendUpdate(cmd);
        }
    }

    /** The machine block went away. */
    void closeFromMachine() {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null) {
            return;
        }
        ref.getStore().getExternalData().getWorld().execute(() -> {
            if (ref.isValid()) {
                close();
            }
        });
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        machine.getOpenPages().remove(this);
    }

    // Drag and drop

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageEvent data) {
        Grid grid = Grid.byName(data.target);
        if (grid == null || data.action == null) {
            return;
        }
        switch (data.action) {
            case ACTION_DROPPED -> {
                if (data.slotIndex != null && data.sourceSlotId != null) {
                    int quantity = data.itemStackQuantity == null ? 0 : data.itemStackQuantity;
                    pendingDrop = new Drop(grid, data.slotIndex, data.sourceSlotId, quantity, System.currentTimeMillis());
                }
            }
            case ACTION_DRAG_DONE -> {
                // Dropped is sent by the target grid, then the source grid reports the drag finished.
                Drop drop = pendingDrop;
                pendingDrop = null;
                if (drop != null && System.currentTimeMillis() - drop.time() <= DROP_WINDOW_MS) {
                    transfer(ref, store, grid, drop);
                }
            }
            default -> {
            }
        }
    }

    private void transfer(Ref<EntityStore> ref, Store<EntityStore> store, Grid sourceGrid, Drop drop) {
        MachineBlock m = liveMachine();
        if (m == null) {
            closeFromMachine();
            return;
        }
        ItemContainer player = playerItems(ref, store);
        if (player != null) {
            Slot from = resolve(sourceGrid, drop.fromSlot(), m, player);
            Slot to = resolve(drop.target(), drop.toSlot(), m, player);
            if (from != null && to != null) {
                move(m, from, to, drop.quantity());
            }
        }
        // Resend everything so the client matches the server, whether the move happened or not.
        UICommandBuilder cmd = new UICommandBuilder();
        writeSlots(cmd, m, player, true);
        writeStatus(cmd, m);
        statusKey = statusKey(m);
        sendUpdate(cmd);
    }

    /** Re-fetches the machine: the block ref may have gone invalid since the page opened. */
    @Nullable
    private MachineBlock liveMachine() {
        if (!blockRef.isValid()) {
            return null;
        }
        MachineBlock m = blockRef.getStore().getComponent(blockRef, MachineBlock.getComponentType());
        return m != null && m.isInitialized() ? m : null;
    }

    @Nullable
    private static Slot resolve(Grid grid, int index, MachineBlock m, ItemContainer player) {
        if (grid.role == null) {
            return index >= 0 && index < player.getCapacity() ? new Slot(grid, player, (short) index) : null;
        }
        int[] map = m.getDefinition().slotsOf(grid.role);
        return index >= 0 && index < map.length ? new Slot(grid, m.getItems(), (short) map[index]) : null;
    }

    private static boolean accepts(MachineBlock m, Slot slot, @Nullable ItemStack stack) {
        SlotRole role = slot.grid().role;
        if (role == null) {
            return true;
        }
        return !ItemStack.isEmpty(stack)
            && MachineSlotRules.accepts(m.getDefinition(), role, stack.getItemId(), TechtalePlugin.get().getRecipes(), MachinePage::isFuel);
    }

    private static boolean isFuel(String itemId) {
        Item item = Item.getAssetMap().getAsset(itemId);
        return item != null && item.getFuelQuality() > 0;
    }

    private static void move(MachineBlock m, Slot from, Slot to, int requested) {
        if (from.container() == to.container() && from.index() == to.index()) {
            return;
        }
        ItemStack moving = from.stack();
        if (ItemStack.isEmpty(moving)) {
            return;
        }
        int quantity = requested <= 0 ? moving.getQuantity() : Math.min(requested, moving.getQuantity());
        if (!accepts(m, to, moving)) {
            return;
        }
        ItemStack there = to.stack();
        boolean swap = !ItemStack.isEmpty(there) && !moving.isStackableWith(there);
        if (swap && (quantity < moving.getQuantity() || !accepts(m, from, there))) {
            return;
        }
        from.container().moveItemStackFromSlotToSlot(from.index(), quantity, to.container(), to.index());
    }

    /** What the client sends with a slot drag. Only some fields are used; the rest are declared so decoding accepts them. */
    public static class PageEvent {
        public static final BuilderCodec<PageEvent> CODEC = BuilderCodec.builder(PageEvent.class, PageEvent::new)
            .addField(new KeyedCodec<>("Action", Codec.STRING), (d, s) -> d.action = s, d -> d.action)
            .addField(new KeyedCodec<>("Target", Codec.STRING), (d, s) -> d.target = s, d -> d.target)
            .addField(new KeyedCodec<>("SlotIndex", Codec.INTEGER), (d, s) -> d.slotIndex = s, d -> d.slotIndex)
            .addField(new KeyedCodec<>("SourceSlotId", Codec.INTEGER), (d, s) -> d.sourceSlotId = s, d -> d.sourceSlotId)
            .addField(new KeyedCodec<>("SourceItemGridIndex", Codec.INTEGER), (d, s) -> { }, d -> null)
            .addField(new KeyedCodec<>("SourceInventorySectionId", Codec.STRING), (d, s) -> { }, d -> null)
            .addField(new KeyedCodec<>("ItemStackId", Codec.STRING), (d, s) -> { }, d -> null)
            .addField(new KeyedCodec<>("ItemStackQuantity", Codec.INTEGER), (d, s) -> d.itemStackQuantity = s, d -> d.itemStackQuantity)
            .addField(new KeyedCodec<>("PressedMouseButton", Codec.INTEGER), (d, s) -> { }, d -> null)
            .addField(new KeyedCodec<>("DragItemStackId", Codec.STRING), (d, s) -> { }, d -> null)
            .addField(new KeyedCodec<>("DragItemStackQuantity", Codec.INTEGER), (d, s) -> { }, d -> null)
            .addField(new KeyedCodec<>("DragSourceInventorySectionId", Codec.STRING), (d, s) -> { }, d -> null)
            .addField(new KeyedCodec<>("DragSourceItemGridIndex", Codec.INTEGER), (d, s) -> { }, d -> null)
            .addField(new KeyedCodec<>("DragSourceSlotId", Codec.INTEGER), (d, s) -> { }, d -> null)
            .build();
        public String action;
        public String target;
        public Integer slotIndex;
        public Integer sourceSlotId;
        public Integer itemStackQuantity;
    }
}
