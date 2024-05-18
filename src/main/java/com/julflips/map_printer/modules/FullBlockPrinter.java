package com.julflips.map_printer.modules;

import com.julflips.map_printer.Addon;
import com.julflips.map_printer.utils.Utils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.*;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.tuple.Triple;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class FullBlockPrinter extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> linesPerRun = sgGeneral.add(new IntSetting.Builder()
        .name("lines-per-run")
        .description("How many lines to place in parallel per run.")
        .defaultValue(3)
        .min(1)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Double> checkpointBuffer = sgGeneral.add(new DoubleSetting.Builder()
        .name("checkpoint-buffer")
        .description("The buffer area of the checkpoints. Larger means less precise walking, but might be desired at higher speeds.")
        .defaultValue(0.2)
        .min(0)
        .sliderRange(0, 1)
        .build()
    );

    private final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("The maximum range you can place blocks around yourself.")
        .defaultValue(4)
        .min(1)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("How many milliseconds to wait after placing.")
        .defaultValue(50)
        .min(1)
        .sliderRange(10, 300)
        .build()
    );

    private final Setting<Integer> mapFillSquareSize = sgGeneral.add(new IntSetting.Builder()
        .name("map-fill-square-size")
        .description("The radius of the square the bot fill walk to explore the map.")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 50)
        .build()
    );

    private final Setting<Integer> preRestockDelay = sgGeneral.add(new IntSetting.Builder()
        .name("pre-restock-delay")
        .description("How many ticks to wait to take items after opening the chest.")
        .defaultValue(20)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Integer> invActionDelay = sgGeneral.add(new IntSetting.Builder()
        .name("inventory-action-delay")
        .description("How many ticks to wait between each inventory action (moving a stack).")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Integer> postRestockDelay = sgGeneral.add(new IntSetting.Builder()
        .name("post-restock-delay")
        .description("How many ticks to wait after restocking.")
        .defaultValue(20)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Integer> swapDelay = sgGeneral.add(new IntSetting.Builder()
        .name("swap-delay")
        .description("How many ticks to wait before swapping into hotbar.")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Integer> resetDelay = sgGeneral.add(new IntSetting.Builder()
        .name("reset-delay")
        .description("How many ticks to wait after after reset button was pressed.")
        .defaultValue(400)
        .min(1)
        .sliderRange(50, 600)
        .build()
    );

    private final Setting<Integer> resetChestCloseDelay = sgGeneral.add(new IntSetting.Builder()
        .name("reset-chest-close-delay")
        .description("How many ticks to wait before closing the reset trap chest again.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Integer> retryInteractTimer = sgGeneral.add(new IntSetting.Builder()
        .name("retry-interact-timer")
        .description("How many ticks to wait for chest response before interacting with it again.")
        .defaultValue(80)
        .min(1)
        .sliderRange(20, 200)
        .build()
    );

    private final Setting<Boolean> activationReset = sgGeneral.add(new BoolSetting.Builder()
        .name("activation-reset")
        .description("Disable if the bot should continue after reconnecting to the server.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SprintMode> sprinting = sgGeneral.add(new EnumSetting.Builder<SprintMode>()
        .name("sprint-mode")
        .description("How to sprint.")
        .defaultValue(SprintMode.NotPlacing)
        .build()
    );

    private final Setting<Boolean> moveToFinishedFolder = sgGeneral.add(new BoolSetting.Builder()
        .name("move-to-finished-folder")
        .description("Moves finished NBT files into the finished-maps folder in the map-printer folder.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disableOnFinished = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-on-finished")
        .description("Disables the printer when all nbt files are finished.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> debugPrints = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-prints")
        .description("Prints additional information.")
        .defaultValue(false)
        .build()
    );

    //Render

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Highlights the selected areas.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renderChestPositions = sgRender.add(new BoolSetting.Builder()
        .name("render-chest-positions")
        .description("Highlights the selected chests.")
        .defaultValue(true)
        .visible(() -> render.get())
        .build()
    );

    private final Setting<Boolean> renderOpenPositions = sgRender.add(new BoolSetting.Builder()
        .name("render-open-positions")
        .description("Indicate the position the bot will go to in order to interact with the chest.")
        .defaultValue(true)
        .visible(() -> render.get())
        .build()
    );

    private final Setting<Boolean> renderCheckpoints = sgRender.add(new BoolSetting.Builder()
        .name("render-checkpoints")
        .description("Indicate the checkpoints the bot will traverse.")
        .defaultValue(true)
        .visible(() -> render.get())
        .build()
    );

    private final Setting<Boolean> renderSpecialInteractions = sgRender.add(new BoolSetting.Builder()
        .name("render-special-interactions")
        .description("Indicate the position where the reset button and cartography table will be used.")
        .defaultValue(true)
        .visible(() -> render.get())
        .build()
    );

    private final Setting<Double> indicatorSize = sgRender.add(new DoubleSetting.Builder()
        .name("indicator-size")
        .description("How big the rendered indicator will be.")
        .defaultValue(0.2)
        .min(0)
        .sliderRange(0, 1)
        .visible(() -> render.get())
        .build()
    );

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color")
        .description("The render color.")
        .defaultValue(new SettingColor(22, 230, 206, 155))
        .visible(() -> render.get())
        .build()
    );

    public FullBlockPrinter() {
        super(Addon.CATEGORY, "full-block-printer", "Automatically builds 2D full-block maps from nbt files.");
    }

    int timeoutTicks;
    int closeResetChestTicks;
    int interactTimeout;
    long lastTickTime;
    boolean pressedReset;
    boolean closeNextInvPacket;
    State state;
    State oldState;
    Pair<BlockHitResult, Vec3d> reset;
    Pair<BlockHitResult, Vec3d> cartographyTable;
    Pair<BlockHitResult, Vec3d> finishedMapChest;
    ArrayList<Pair<BlockPos, Vec3d>> mapMaterialChests;
    ArrayList<Pair<BlockPos, Vec3d>> dumpChests;
    BlockPos mapCorner;
    BlockPos tempChestPos;
    BlockPos lastInteractedChest;
    InventoryS2CPacket toBeHandledInvPacket;
    HashMap<Integer, Pair<Block, Integer>> blockPaletteDict;       //Maps palette block id to the Minecraft block and amount
    HashMap<Block, ArrayList<Pair<BlockPos, Vec3d>>> materialDict; //Maps block to the chest pos and the open position
    ArrayList<Integer> availableSlots;
    ArrayList<Integer> availableHotBarSlots;
    ArrayList<Triple<Block, Integer, Integer>> restockList;        //Material, Stacks, Raw Amount
    ArrayList<BlockPos> checkedChests;
    ArrayList<Pair<Vec3d, Pair<String, BlockPos>>> checkpoints;    //(GoalPos, (checkpointAction, targetBlock))
    ArrayList<File> startedFiles;
    ArrayList<ClickSlotC2SPacket> invActionPackets = new ArrayList<>();
    Block[][] map;
    File mapFolder;
    File mapFile;

    @Override
    public void onActivate() {
        lastTickTime = System.currentTimeMillis();
        if (!activationReset.get() && checkpoints != null) {
            return;
        }
        materialDict = new HashMap<>();
        availableSlots = new ArrayList<>();
        availableHotBarSlots = new ArrayList<>();
        restockList = new ArrayList<>();
        checkedChests = new ArrayList<>();
        checkpoints = new ArrayList<>();
        startedFiles = new ArrayList<>();
        invActionPackets = new ArrayList<>();
        reset = null;
        mapCorner = null;
        lastInteractedChest = null;
        cartographyTable = null;
        finishedMapChest = null;
        mapMaterialChests = new ArrayList<>();
        dumpChests = new ArrayList<>();
        toBeHandledInvPacket = null;
        closeNextInvPacket = false;
        pressedReset = false;
        timeoutTicks = 0;
        interactTimeout = 0;
        closeResetChestTicks = 0;

        mapFolder = new File(Utils.getMinecraftDirectory() + File.separator + "map-printer");
        if (!Utils.createMapFolder(mapFolder)) {
            toggle();
            return;
        }
        mapFile = getNextMapFile();
        if (mapFile == null) {
            warning("No nbt files found in map-printer folder.");
            toggle();
            return;
        }
        if (!loadNBTFiles()) {
            warning("Failed to read nbt file.");
            toggle();
            return;
        }
        state = State.SelectingMapArea;
        info("Select the §aMap Building Area (128x128)");
    }

    private void refillInventory(HashMap<Block, Integer> invMaterial) {
        //Fills restockList with required items
        restockList.clear();
        HashMap<Block, Integer> requiredItems = Utils.getRequiredItems(mapCorner, linesPerRun.get(), blockPaletteDict, availableSlots.size(), map);
        for (Block material : invMaterial.keySet()) {
            int oldAmount = requiredItems.remove(material);
            requiredItems.put(material, oldAmount - invMaterial.get(material));
        }

        for (Block block: requiredItems.keySet()) {
            if (requiredItems.get(block) <= 0) continue;
            int stacks = (int) Math.ceil((float) requiredItems.get(block) / 64f);
            info("Restocking §a" + stacks + " stacks " + block.getName().getString() + " (" + requiredItems.get(block) + ")");
            restockList.add(0, Triple.of(block , stacks, requiredItems.get(block)));
        }
        addClosestRestockCheckpoint();
    }

    private void addClosestRestockCheckpoint() {
        //Determine closest restock chest for material in restock list
        if (restockList.size() == 0) return;
        double smallestDistance = Double.MAX_VALUE;
        Triple<Block, Integer, Integer> closestEntry = null;
        Pair<BlockPos, Vec3d> restockPos = null;
        for (Triple<Block, Integer, Integer> entry : restockList) {
            Pair<BlockPos, Vec3d> bestRestockPos = getBestChest(entry.getLeft());
            if (bestRestockPos.getLeft() == null) {
                warning("No chest found for " + entry.getLeft().getName().getString());
                toggle();
                return;
            }
            double chestDistance = PlayerUtils.distanceTo(bestRestockPos.getRight());
            if (chestDistance < smallestDistance) {
                smallestDistance = chestDistance;
                closestEntry = entry;
                restockPos = bestRestockPos;
            }
        }
        //Set closest material as first and as checkpoint
        restockList.remove(closestEntry);
        restockList.add(0, closestEntry);
        checkpoints.add(0, new Pair(restockPos.getRight(), new Pair("refill", restockPos.getLeft())));
    }

    private void calculateBuildingPath() {
        //Iterate over map and skip completed lines. Player has to be able to see the complete map area
        //Fills checkpoints list
        boolean isStartSide = true;
        checkpoints.clear();
        for (int x = 0; x < 128; x+=linesPerRun.get()) {
            boolean lineFinished = true;
            for (int lineBonus = 0; lineBonus < linesPerRun.get(); lineBonus++) {
                if (x + lineBonus > 127) break;
                for (int z = 0; z < 128; z++) {
                    BlockState blockstate = mc.world.getBlockState(mapCorner.add(x + lineBonus, 0, z));
                    if (blockstate.isAir()) {
                        lineFinished = false;
                        break;
                    }
                }
            }
            if (lineFinished) continue;
            Vec3d cp1 = mapCorner.toCenterPos().add(x,0,0);
            Vec3d cp2 = mapCorner.toCenterPos().add(x,0,127);
            if (isStartSide) {
                checkpoints.add(new Pair(cp1, new Pair("", null)));
                checkpoints.add(new Pair(cp2, new Pair("", null)));
            } else {
                checkpoints.add(new Pair(cp2, new Pair("", null)));
                checkpoints.add(new Pair(cp1, new Pair("", null)));
            }
            isStartSide = !isStartSide;
        }
        if (checkpoints.size() > 0) {
            //Make player sprint to the start of the map
            Pair<Vec3d, Pair<String, BlockPos>>firstPoint = checkpoints.remove(0);
            checkpoints.add(0, new Pair(firstPoint.getLeft(), new Pair("sprint", firstPoint.getRight().getRight())));
        }
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (!(event.packet instanceof PlayerInteractBlockC2SPacket packet) || state == null) return;
        switch (state) {
            case SelectingMapArea:
                BlockPos hitPos = packet.getBlockHitResult().getBlockPos().offset(packet.getBlockHitResult().getSide());
                int adjustedX = Utils.getIntervalStart(hitPos.getX());
                int adjustedZ = Utils.getIntervalStart(hitPos.getZ());
                mapCorner = new BlockPos(adjustedX, hitPos.getY(), adjustedZ);
                state = State.SelectingReset;
                info("Map Area selected. Press the §aReset Trapped Chest §7used to remove the built map");
                break;
            case SelectingReset:
                BlockPos blockPos = packet.getBlockHitResult().getBlockPos();
                if (mc.world.getBlockState(blockPos).getBlock() instanceof TrappedChestBlock) {
                    reset = new Pair<>(packet.getBlockHitResult(), mc.player.getPos());
                    info("Reset Trapped Chest selected. Select the §aCartography Table.");
                    state = State.SelectingTable;
                }
                break;
            case SelectingTable:
                blockPos = packet.getBlockHitResult().getBlockPos();
                if (mc.world.getBlockState(blockPos).getBlock().equals(Blocks.CARTOGRAPHY_TABLE)) {
                    cartographyTable = new Pair<>(packet.getBlockHitResult(), mc.player.getPos());
                    info("Cartography Table selected. Select the §aFinished Map Chest.");
                    state = State.SelectingFinishedMapChest;
                }
                break;
            case SelectingFinishedMapChest:
                blockPos = packet.getBlockHitResult().getBlockPos();
                if (mc.world.getBlockState(blockPos).getBlock() instanceof AbstractChestBlock) {
                    finishedMapChest = new Pair<>(packet.getBlockHitResult(), mc.player.getPos());
                    info("Finished Map Chest selected. Select all §aMaterial-, Map- and Dump-Chests.");
                    state = State.SelectingChests;
                }
                break;
            case SelectingChests:
                blockPos = packet.getBlockHitResult().getBlockPos();
                if (blockPos.offset(packet.getBlockHitResult().getSide()).equals(mapCorner)) {
                    //Check if requirements to start building are met
                    if (materialDict.size() == 0) {
                        warning("No Material Chests selected!");
                        return;
                    }
                    if (dumpChests.size() == 0) {
                        warning("No Dump Chests selected!");
                        return;
                    }
                    if (mapMaterialChests.size() == 0) {
                        warning("No Map Chests selected!");
                        return;
                    }
                    Utils.setWPressed(true);
                    calculateBuildingPath();
                    availableSlots = Utils.getAvailableSlots(materialDict);
                    for (int slot : availableSlots) {
                        if (slot < 9) {
                            availableHotBarSlots.add(slot);
                        }
                    }
                    info("Inventory slots available for building: " + availableSlots);

                    HashMap<Block, Integer> requiredItems = Utils.getRequiredItems(mapCorner, linesPerRun.get(), blockPaletteDict, availableSlots.size(), map);
                    Pair<ArrayList<Integer>, HashMap<Block, Integer>> invInformation = Utils.getInvInformation(debugPrints.get(), requiredItems, availableSlots);
                    if (invInformation.getLeft().size() != 0) {
                        Pair<BlockPos, Vec3d> bestChest = getBestChest(null);
                        checkpoints.add(0, new Pair(bestChest.getRight(), new Pair("dump", bestChest.getLeft())));
                    } else {
                        refillInventory(invInformation.getRight());
                    }
                    if (availableHotBarSlots.size() == 0) {
                        warning("No free slots found in hot-bar!");
                        toggle();
                        return;
                    }
                    if (availableSlots.size() < 2) {
                        warning("You need at least 2 free inventory slots!");
                        toggle();
                        return;
                    }
                    state = State.Walking;
                }
                if (mc.world.getBlockState(blockPos).getBlock().equals(Blocks.CHEST)) {
                    tempChestPos = blockPos;
                    state = State.AwaitContent;
                }
                break;
        }
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof InventoryS2CPacket packet) || state == null) return;
        if (state.equals(State.AwaitContent)) {
            //info("Chest content received.");
            Item foundItem = null;
            boolean isMixedContent = false;
            for (int i = 0; i < packet.getContents().size()-36; i++) {
                ItemStack stack = packet.getContents().get(i);
                if (!stack.isEmpty()) {
                    if (foundItem != null && foundItem != stack.getItem().asItem()) {
                        isMixedContent = true;
                    }
                    foundItem = stack.getItem().asItem();
                    if (foundItem == Items.MAP || foundItem == Items.GLASS_PANE) {
                        info("Registered §aMapChest");
                        mapMaterialChests = Utils.saveAdd(mapMaterialChests, tempChestPos, mc.player.getPos());
                        state = State.SelectingChests;
                        return;
                    }
                }
            }
            if (foundItem == null) {
                info("Registered §aDumpChest");
                dumpChests = Utils.saveAdd(dumpChests, tempChestPos, mc.player.getPos());
                state = State.SelectingChests;
                return;
            }
            if (isMixedContent) {
                warning("Different items found in chest. Please only have one item type in the chest.");
                return;
            }

            Block chestContentBlock = Registries.BLOCK.get(new Identifier(foundItem.toString()));
            info("Registered §a" + chestContentBlock.getName().getString());
            if (!materialDict.containsKey(chestContentBlock)) materialDict.put(chestContentBlock, new ArrayList<>());
            ArrayList<Pair<BlockPos, Vec3d>> oldList = materialDict.get(chestContentBlock);
            ArrayList newChestList = Utils.saveAdd(oldList, tempChestPos, mc.player.getPos());
            materialDict.put(chestContentBlock, newChestList);
            state = State.SelectingChests;
        }

        List<State> allowedStates = Arrays.asList(State.AwaitRestockResponse, State.AwaitDumpResponse, State.AwaitMapChestResponse,
            State.AwaitCartographyResponse, State.AwaitFinishedMapChestResponse, State.AwaitResetResponse);
        if (allowedStates.contains(state)) {
            toBeHandledInvPacket = packet;
            timeoutTicks = preRestockDelay.get();
        }
    }

    private void handleInventoryPacket(InventoryS2CPacket packet) {
        if (debugPrints.get()) info("Handling InvPacket for: " + state);
        closeNextInvPacket = true;
        switch (state) {
            case AwaitRestockResponse:
                boolean foundMaterials = false;
                for (int i = 0; i < packet.getContents().size()-36; i++) {
                    ItemStack stack = packet.getContents().get(i);

                    if (restockList.get(0).getMiddle() == 0) {
                        foundMaterials = true;
                        break;
                    }
                    if (!stack.isEmpty() && stack.getCount() == 64) {
                        //info("Taking Stack of " + restockList.get(0).getLeft().getName().getString());
                        foundMaterials = true;
                        int highestFreeSlot = Utils.findHighestFreeSlot(packet);
                        if (highestFreeSlot == -1) {
                            warning("No free slots found in inventory.");
                            Pair<BlockPos, Vec3d> dumpChest = getBestChest(null);
                            checkpoints.add(0, new Pair(dumpChest.getRight(), new Pair("dump", dumpChest.getLeft())));
                            state = State.Walking;
                            return;
                        }
                        invActionPackets.add(new ClickSlotC2SPacket(packet.getSyncId(), 1, i, 1, SlotActionType.QUICK_MOVE, new ItemStack(Items.AIR), Int2ObjectMaps.emptyMap()));
                        Triple<Block, Integer, Integer> oldTriple = restockList.remove(0);
                        restockList.add(0, Triple.of(oldTriple.getLeft(), oldTriple.getMiddle() - 1, oldTriple.getRight() - 64));
                    }
                }
                if (!foundMaterials) return;
                interactTimeout = 0;
                timeoutTicks = invActionDelay.get();
                break;
            case AwaitDumpResponse:
                interactTimeout = 0;
                HashMap<Block, Integer> requiredItems = Utils.getRequiredItems(mapCorner, linesPerRun.get(), blockPaletteDict, availableSlots.size(), map);
                Pair<ArrayList<Integer>, HashMap<Block, Integer>> invInformation = Utils.getInvInformation(debugPrints.get(), requiredItems, availableSlots);
                for (int slot : invInformation.getLeft()) {
                    //info("Initial slot: " + slot);
                    //Slot adjustment because slot IDs are different when opening a container
                    if (slot < 9) {
                        slot += 27;
                    } else {
                        slot -= 9;
                    }
                    slot = packet.getContents().size() - 36 + slot;
                    //info("Try to dump slot: " + slot);

                    ItemStack stack = packet.getContents().get(slot);
                    if (!stack.isEmpty()) {
                        //info("Dumped slot " + slot + ": " + stack.getName().getString());
                        invActionPackets.add(new ClickSlotC2SPacket(packet.getSyncId(), 1, slot, 1, SlotActionType.QUICK_MOVE, new ItemStack(Items.AIR), Int2ObjectMaps.emptyMap()));
                    }
                }

                if (invActionPackets.isEmpty()) {
                    refillInventory(invInformation.getRight());
                    state = State.Walking;
                    timeoutTicks = postRestockDelay.get();
                    break;
                }
                break;
            case AwaitMapChestResponse:
                interactTimeout = 0;
                timeoutTicks = postRestockDelay.get();
                //Search for map and glass pane
                for (int slot = 0; slot < packet.getContents().size()-36; slot++) {
                    ItemStack stack = packet.getContents().get(slot);
                    if (stack.getItem() == Items.MAP) {
                        getOneItem(slot, false, packet);
                        break;
                    }
                }
                //Has to be done after map switch to have map at selected slot
                for (int slot = 0; slot < packet.getContents().size()-36; slot++) {
                    ItemStack stack = packet.getContents().get(slot);
                    if (stack.getItem() == Items.GLASS_PANE) {
                        getOneItem(slot, true, packet);
                        break;
                    }
                }
                mc.player.getInventory().selectedSlot = availableSlots.get(0);
                Vec3d center = mapCorner.add(map.length/2 - 1, 0, map[0].length/2 - 1).toCenterPos();
                checkpoints.add(new Pair(center, new Pair("fillMap", null)));
                state = State.Walking;
                break;
            case AwaitCartographyResponse:
                interactTimeout = 0;
                timeoutTicks = postRestockDelay.get();
                boolean searchingMap = true;
                for (int slot : availableSlots) {
                    if (slot < 9) {  //Stupid slot correction
                        slot += 30;
                    } else {
                        slot -= 6;
                    }
                    ItemStack stack = packet.getContents().get(slot);
                    if (searchingMap && stack.getItem() == Items.FILLED_MAP) {
                        mc.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(packet.getSyncId(), 1, slot, 0, SlotActionType.QUICK_MOVE, new ItemStack(Items.AIR), Int2ObjectMaps.emptyMap()));
                        searchingMap = false;
                    }
                }
                for (int slot : availableSlots) {
                    if (slot < 9) {  //Stupid slot correction
                        slot += 30;
                    } else {
                        slot -= 6;
                    }
                    ItemStack stack = packet.getContents().get(slot);
                    if (!searchingMap && stack.getItem() == Items.GLASS_PANE) {
                        mc.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(packet.getSyncId(), 1, slot, 0, SlotActionType.QUICK_MOVE, new ItemStack(Items.AIR), Int2ObjectMaps.emptyMap()));
                        break;
                    }
                }
                mc.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(packet.getSyncId(), 1, 2, 0, SlotActionType.QUICK_MOVE, new ItemStack(Items.AIR), Int2ObjectMaps.emptyMap()));
                checkpoints.add(new Pair(finishedMapChest.getRight(), new Pair("finishedMapChest", null)));
                state = State.Walking;
                break;
            case AwaitFinishedMapChestResponse:
                interactTimeout = 0;
                timeoutTicks = postRestockDelay.get();
                for (int slot = packet.getContents().size()-36; slot < packet.getContents().size(); slot++) {
                    ItemStack stack = packet.getContents().get(slot);
                    if (stack.getItem() == Items.FILLED_MAP) {
                        mc.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(packet.getSyncId(), 1, slot, 0, SlotActionType.QUICK_MOVE, new ItemStack(Items.AIR), Int2ObjectMaps.emptyMap()));
                        break;
                    }
                }
                checkpoints.add(new Pair(reset.getRight(), new Pair("reset", null)));
                state = State.Walking;
                break;
            case AwaitResetResponse:
                interactTimeout = 0;
                timeoutTicks = resetDelay.get();
                pressedReset = true;
                closeNextInvPacket = false;
                closeResetChestTicks = resetChestCloseDelay.get();
                break;
        }
    }

    private void getOneItem(int sourceSlot, boolean avoidFirstHotBar, InventoryS2CPacket packet) {
        int targetSlot = availableHotBarSlots.get(0);
        if (avoidFirstHotBar) {
            targetSlot = availableSlots.get(0);
            if (availableSlots.get(0) == availableHotBarSlots.get(0)) {
                targetSlot = availableSlots.get(1);
            }
        }
        if (targetSlot < 9) {
            targetSlot += 27;
        } else {
            targetSlot -= 9;
        }
        targetSlot = packet.getContents().size() - 36 + targetSlot;
        mc.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(packet.getSyncId(), 1, sourceSlot, 0, SlotActionType.PICKUP , new ItemStack(Items.MAP), Int2ObjectMaps.emptyMap()));
        mc.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(packet.getSyncId(), 1, targetSlot, 1, SlotActionType.PICKUP, new ItemStack(Items.MAP), Int2ObjectMaps.emptyMap()));
        mc.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(packet.getSyncId(), 1, sourceSlot, 0, SlotActionType.PICKUP , new ItemStack(Items.AIR), Int2ObjectMaps.emptyMap()));
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (state == null) return;

        if (oldState != state) {
            oldState = state;
            if (debugPrints.get()) info("Changed state to " + state.name());
        }

        long timeDifference = System.currentTimeMillis() - lastTickTime;
        int allowedPlacements = (int) Math.floor(timeDifference / placeDelay.get());
        lastTickTime += allowedPlacements * placeDelay.get();

        if (interactTimeout > 0) {
            interactTimeout--;
            if (interactTimeout == 0) {
                info("Interaction timed out. Interacting again...");
                if (pressedReset) {
                    interactWithBlock(reset.getLeft());
                    timeoutTicks = resetDelay.get();
                } else if (state == State.AwaitCartographyResponse) {
                    interactWithBlock(cartographyTable.getLeft());
                } else {
                    interactWithBlock(lastInteractedChest);
                }
            }
        }

        if (closeResetChestTicks > 0) {
            closeResetChestTicks--;
            if (closeResetChestTicks == 0) {
                mc.player.closeHandledScreen();
                state = State.AwaitNBTFile;
            }
        }

        if (timeoutTicks > 0) {
            timeoutTicks--;
            return;
        }

        if (invActionPackets.size() > 0) {
            mc.getNetworkHandler().sendPacket(invActionPackets.get(0));
            invActionPackets.remove(0);
            if (invActionPackets.size() == 0) {
                if (state.equals(State.AwaitRestockResponse)) {
                    endRestocking();
                }
                if (state.equals(State.AwaitDumpResponse)) {
                    HashMap<Block, Integer> requiredItems = Utils.getRequiredItems(mapCorner, linesPerRun.get(), blockPaletteDict, availableSlots.size(), map);
                    Pair<ArrayList<Integer>, HashMap<Block, Integer>> invInformation = Utils.getInvInformation(debugPrints.get(), requiredItems, availableSlots);
                    refillInventory(invInformation.getRight());
                    state = State.Walking;
                    timeoutTicks = postRestockDelay.get();
                }
            } else {
                timeoutTicks = invActionDelay.get();
            }
            return;
        }

        if (state == State.AwaitNBTFile) {
            mapFile = getNextMapFile();
            if (mapFile == null) {
                if (disableOnFinished.get()) {
                    info("All nbt files finished");
                    toggle();
                    return;
                } else {
                    return;
                }
            }
            if (!loadNBTFiles()) {
                warning("Failed to read schematic file.");
                toggle();
                return;
            }
            state = State.Walking;
        }

        if (pressedReset) {
            pressedReset = false;
            calculateBuildingPath();
            Pair<BlockPos, Vec3d> bestChest = getBestChest(null);
            checkpoints.add(0, new Pair(bestChest.getRight(), new Pair("dump", bestChest.getLeft())));
            Utils.setWPressed(true);
            return;
        }

        if (toBeHandledInvPacket != null) {
            handleInventoryPacket(toBeHandledInvPacket);
            toBeHandledInvPacket = null;
            return;
        }

        if (closeNextInvPacket) {
            //info("Closing Inventory");
            if (mc.currentScreen != null) {
                mc.player.closeHandledScreen();
            }
            closeNextInvPacket = false;
        }

        if (!state.equals(State.Walking)) return;
        Utils.setWPressed(true);
        Vec3d goal = checkpoints.get(0).getLeft();
        if (PlayerUtils.distanceTo(goal.add(0,mc.player.getY()-goal.y,0)) < checkpointBuffer.get()) {
            Pair<String, BlockPos> checkpointAction = checkpoints.get(0).getRight();
            if (debugPrints.get() && checkpointAction.getLeft() != null && checkpointAction.getRight() != null) info("Reached " + checkpointAction.getLeft());
            checkpoints.remove(0);
            mc.player.setPosition(goal.getX(), mc.player.getY(), goal.getZ());
            mc.player.setVelocity(0,0,0);
            switch (checkpointAction.getLeft()) {
                case "mapMaterialChest":
                    BlockPos mapMaterialChest = getBestChest(Blocks.CARTOGRAPHY_TABLE).getLeft();
                    interactWithBlock(mapMaterialChest);
                    state = State.AwaitMapChestResponse;
                    return;
                case "fillMap":
                    mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, Utils.getNextInteractID()));
                    if (mapFillSquareSize.get() == 0) {
                        checkpoints.add(0, new Pair(cartographyTable.getRight(), new Pair<>("cartographyTable", null)));
                    } else {
                        checkpoints.add(new Pair(goal.add(-mapFillSquareSize.get(), 0, mapFillSquareSize.get()), new Pair("sprint", null)));
                        checkpoints.add(new Pair(goal.add(mapFillSquareSize.get(), 0, mapFillSquareSize.get()), new Pair("sprint", null)));
                        checkpoints.add(new Pair(goal.add(mapFillSquareSize.get(), 0, -mapFillSquareSize.get()), new Pair("sprint", null)));
                        checkpoints.add(new Pair(goal.add(-mapFillSquareSize.get(), 0, -mapFillSquareSize.get()), new Pair("sprint", null)));
                        checkpoints.add(new Pair(cartographyTable.getRight(), new Pair("cartographyTable", null)));
                    }
                    return;
                case "cartographyTable":
                    interactWithBlock(cartographyTable.getLeft());
                    state = State.AwaitCartographyResponse;
                    return;
                case "finishedMapChest":
                    interactWithBlock(finishedMapChest.getLeft().getBlockPos());
                    state = State.AwaitFinishedMapChestResponse;
                    return;
                case "reset":
                    info("Resetting...");
                    interactWithBlock(reset.getLeft());
                    state = State.AwaitResetResponse;
                    lastInteractedChest = reset.getLeft().getBlockPos();
                    return;
                case "dump":
                    interactWithBlock(checkpointAction.getRight());
                    state = State.AwaitDumpResponse;
                    return;
                case "refill":
                    interactWithBlock(checkpointAction.getRight());
                    state = State.AwaitRestockResponse;
                    return;
            }
            if (checkpoints.size() == 0) {
                calculateBuildingPath();
                if (checkpoints.size() == 0) {
                    info("Finished building map");
                    Pair<BlockPos, Vec3d> bestChest = getBestChest(Blocks.CARTOGRAPHY_TABLE);
                    checkpoints.add(0, new Pair(bestChest.getRight(), new Pair("mapMaterialChest", bestChest.getLeft())));
                    try {
                        if (moveToFinishedFolder.get()) mapFile.renameTo(new File(mapFile.getParentFile().getAbsolutePath()+File.separator+"_finished_maps"+File.separator+mapFile.getName()));
                    } catch (Exception e) {
                        warning("Failed to move map file " + mapFile.getName() + " to finished map folder");
                        e.printStackTrace();
                    }
                } else {
                    info("Patching up missed parts of the map...");
                }

                Pair<BlockPos, Vec3d> bestChest = getBestChest(null);
                checkpoints.add(0, new Pair(bestChest.getRight(), new Pair("dump", bestChest.getLeft())));
            }
            goal = checkpoints.get(0).getLeft();
        }
        mc.player.setYaw((float) Rotations.getYaw(goal));
        String nextAction = checkpoints.get(0).getRight().getLeft();

        if (nextAction == "" && sprinting.get() != SprintMode.Always) {
            mc.player.setSprinting(false);
        } else if (sprinting.get() != SprintMode.Off) {
            mc.player.setSprinting(true);
        }
        if (nextAction == "refill" || nextAction == "dump") return;

        ArrayList<BlockPos> placements = new ArrayList<>();
        for (int i = 0; i < allowedPlacements; i++) {
            AtomicReference<BlockPos> closestPos = new AtomicReference<>();
            final Vec3d currentGoal = goal;
            BlockPos playerGroundPos = mc.player.getBlockPos().add(0 , mapCorner.getY() - mc.player.getBlockY(), 0);
            Utils.iterateBlocks(playerGroundPos, (int) Math.ceil(placeRange.get()) + 1, 0,((blockPos, blockState) -> {
                Double posDistance = PlayerUtils.distanceTo(blockPos);
                if ((blockState.isAir()) && posDistance <= placeRange.get() && isWithingMap(blockPos)
                    && blockPos.getX() <= currentGoal.getX() + linesPerRun.get()-1 && !placements.contains(blockPos)) {
                    if (closestPos.get() == null) {
                        closestPos.set(new BlockPos(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
                        return;
                    }
                    if (blockPos.getX() < closestPos.get().getX() ||
                        (blockPos.getX() == closestPos.get().getX() && blockPos.getZ() > closestPos.get().getZ())) {
                        closestPos.set(new BlockPos(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
                    }
                }
            }));

            if (closestPos.get() != null) {
                //Stop placing if restocking
                placements.add(closestPos.get());
                if (!tryPlacingBlock(closestPos.get())) {
                    return;
                }
            }
        }
    }

    private boolean tryPlacingBlock(BlockPos pos) {
        BlockPos relativePos = pos.subtract(mapCorner);
        Block material = map[relativePos.getX()][relativePos.getZ()];
        //info("Placing " + material.getName().getString() + " at: " + relativePos.toShortString());
        //Check hot-bar slots
        for (int slot : availableHotBarSlots) {
            if (mc.player.getInventory().getStack(slot).isEmpty()) continue;
            Block foundMaterial = Registries.BLOCK.get(new Identifier(mc.player.getInventory().getStack(slot).getItem().toString()));
            if (foundMaterial.equals(material)) {
                BlockUtils.place(pos, Hand.MAIN_HAND, slot, true,50, true, true, false);
                return true;
            }
        }
        for (int slot : availableSlots) {
            if (mc.player.getInventory().getStack(slot).isEmpty() || availableHotBarSlots.contains(slot)) continue;
            Block foundMaterial = Registries.BLOCK.get(new Identifier(mc.player.getInventory().getStack(slot).getItem().toString()));
            if (foundMaterial.equals(material)) {
                Utils.swapIntoHotbar(slot, availableHotBarSlots);
                //BlockUtils.place(pos, Hand.MAIN_HAND, resultSlot, true,50, true, true, false);
                Utils.setWPressed(false);
                mc.player.setVelocity(0,0,0);
                timeoutTicks = swapDelay.get();
                return false;
            }
        }
        info("No "+ material.getName().getString() + " found in inventory. Resetting...");
        Pair<BlockPos, Vec3d> bestChest = getBestChest(null);
        checkpoints.add(0, new Pair(mc.player.getPos(), new Pair("sprint", null)));
        checkpoints.add(0, new Pair(bestChest.getRight(), new Pair("dump", bestChest.getLeft())));
        return false;
    }

    private void endRestocking() {
        if (restockList.get(0).getMiddle() > 0) {
            warning("Not all necessary stacks restocked. Searching for another chest...");
            //Search for the next best chest
            checkedChests.add(lastInteractedChest);
            Pair<BlockPos, Vec3d> bestRestockPos = getBestChest(getMaterialFromPos(lastInteractedChest));
            checkpoints.add(0, new Pair<>(bestRestockPos.getRight(), new Pair<>("refill", bestRestockPos.getLeft())));
        } else {
            checkedChests.clear();
            restockList.remove(0);
            addClosestRestockCheckpoint();
        }
        timeoutTicks = postRestockDelay.get();
        state = State.Walking;
    }

    private Pair<BlockPos, Vec3d> getBestChest(Block material) {
        Vec3d bestPos = null;
        BlockPos bestChestPos = null;
        ArrayList<Pair<BlockPos, Vec3d>> list = new ArrayList<>();
        if (material == null) {
            list = dumpChests;
        } else if (material.equals(Blocks.CARTOGRAPHY_TABLE)) {
            list = mapMaterialChests;
        } else if (materialDict.containsKey(material)) {
            list =  materialDict.get(material);
        } else {
            warning("No chest found for " + material.getName().getString());
            toggle();
            return new Pair<>(new BlockPos(0,0,0), new Vec3d(0,0,0));
        }
        //Get nearest chest
        for (Pair<BlockPos, Vec3d> p : list) {
            //Skip chests that have already been checked
            if (checkedChests.contains(p.getLeft())) continue;
            if (bestPos == null || PlayerUtils.distanceTo(p.getRight()) < PlayerUtils.distanceTo(bestPos)) {
                bestPos = p.getRight();
                bestChestPos = p.getLeft();
            }
        }
        if (bestPos == null || bestChestPos == null) {
            warning("All chests are been checked. Choosing a random one...");
            Random random = new Random();
            return list.get(random.nextInt(list.size()));
        }
        return new Pair(bestChestPos, bestPos);
    }

    private void interactWithBlock(BlockPos chestPos) {
        Utils.setWPressed(false);
        mc.player.setVelocity(0,0,0);
        mc.player.setYaw((float) Rotations.getYaw(chestPos.toCenterPos()));
        mc.player.setPitch((float) Rotations.getPitch(chestPos.toCenterPos()));
        BlockHitResult hitResult = new BlockHitResult(chestPos.toCenterPos(), Direction.UP, chestPos, false);
        BlockUtils.interact(hitResult, Hand.MAIN_HAND, true);
        //Set timeout for chest interaction
        interactTimeout = retryInteractTimer.get();
        lastInteractedChest = chestPos;
    }

    private void interactWithBlock(BlockHitResult hitResult) {
        Utils.setWPressed(false);
        mc.player.setVelocity(0,0,0);
        mc.player.setYaw((float) Rotations.getYaw(hitResult.getBlockPos().toCenterPos()));
        mc.player.setPitch((float) Rotations.getPitch(hitResult.getBlockPos().toCenterPos()));
        BlockUtils.interact(hitResult, Hand.MAIN_HAND, true);
        interactTimeout = retryInteractTimer.get();
    }

    private boolean isWithingMap(BlockPos pos) {
        return Utils.getIntervalStart(pos.getX()) == mapCorner.getX() && Utils.getIntervalStart(pos.getZ()) == mapCorner.getZ();
    }

    private Block getMaterialFromPos(BlockPos pos) {
        for (Block material : materialDict.keySet()) {
            for (Pair<BlockPos, Vec3d> p : materialDict.get(material)) {
                if (p.getLeft().equals(pos)) return material;
            }
        }
        warning("Could not find material for chest position : " + pos.toShortString());
        toggle();
        return null;
    }

    private File getNextMapFile() {
        for (File file : mapFolder.listFiles()) {
            if (!startedFiles.contains(file) && file.isFile()) {
                startedFiles.add(file);
                return file;
            }
        }
        return null;
    }

    private boolean loadNBTFiles() {
        info("Building: §a" + mapFile.getName());
        try {
            NbtSizeTracker sizeTracker = new NbtSizeTracker(0x20000000L, 100);
            NbtCompound nbt = NbtIo.readCompressed(mapFile.toPath(), sizeTracker);
            //Extracting the palette
            NbtList paletteList  = (NbtList) nbt.get("palette");
            blockPaletteDict = new HashMap<>();
            for (int i = 0; i < paletteList.size(); i++) {
                NbtCompound block = paletteList.getCompound(i);
                String blockName = block.getString("Name");
                Block material = Registries.BLOCK.get(new Identifier(blockName));
                blockPaletteDict.put(i, new Pair(Registries.BLOCK.get(new Identifier(blockName)), 0));
            }

            //Counting required materials and calculating the map offset
            NbtList blockList  = (NbtList) nbt.get("blocks");
            int maxHeight = Integer.MIN_VALUE;
            int minX = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            for (int i = 0; i < blockList.size(); i++) {
                NbtCompound block = blockList.getCompound(i);
                int blockId = block.getInt("state");
                if (!blockPaletteDict.containsKey(blockId)) continue;
                blockPaletteDict.put(blockId, new Pair(blockPaletteDict.get(blockId).getLeft(), blockPaletteDict.get(blockId).getRight() + 1));
                NbtList pos = block.getList("pos", 3);
                if (pos.getInt(1) > maxHeight) maxHeight = pos.getInt(1);
                if (pos.getInt(0) < minX) minX = pos.getInt(0);
                if (pos.getInt(2) < minZ) minZ = pos.getInt(2);
            }
            info("Requirements: ");
            for (Pair<Block, Integer> p: blockPaletteDict.values()) {
                info(p.getLeft().getName().getString() + ": " + p.getRight());
            }

            //Extracting the map block positions
            map = new Block[128][128];
            for (int i = 0; i < blockList.size(); i++) {
                NbtCompound block = blockList.getCompound(i);
                if (!blockPaletteDict.containsKey(block.getInt("state"))) continue;
                NbtList pos = block.getList("pos", 3);
                int x = pos.getInt(0) - minX;
                int y = pos.getInt(1);
                int z = pos.getInt(2) - minZ;
                if (y == maxHeight && x < map.length && z < map.length & x >= 0 && z >= 0) {
                    map[x][z] = blockPaletteDict.get(block.getInt("state")).getLeft();
                }
            }
            //Check if a full 128x128 map is present
            for (int x = 0; x < map.length; x++) {
                for (int z = 0; z < map[x].length; z++) {
                    if (map[x][z] == null) {
                        warning("No 2D 128x128 map preset in file: " + mapFile.getName());
                        return false;
                    }
                }
            }

            //info("MaxHeight: " + maxHeight + "MinX: " + minX + " MinZ: " + minZ);
            info("Palette:");
            for (Pair<Block, Integer> c : blockPaletteDict.values()) {
                info(c.getLeft().getName().getString());
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public String getInfoString() {
        if (mapFile != null) {
            return mapFile.getName();
        } else {
            return "None";
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if(mapCorner == null || !render.get()) return;
        event.renderer.box(mapCorner, color.get(), color.get(), ShapeMode.Lines, 0);
        event.renderer.box(mapCorner.getX(), mapCorner.getY(), mapCorner.getZ(), mapCorner.getX()+128, mapCorner.getY(), mapCorner.getZ()+128, color.get(), color.get(), ShapeMode.Lines, 0);

        ArrayList<Pair<BlockPos, Vec3d>> renderedPairs = new ArrayList<>();
        for (ArrayList<Pair<BlockPos, Vec3d>> list: materialDict.values()) {
            renderedPairs.addAll(list);
        }
        renderedPairs.addAll(mapMaterialChests);
        renderedPairs.addAll(dumpChests);
        for (Pair<BlockPos, Vec3d> pair: renderedPairs) {
            if (renderChestPositions.get()) event.renderer.box(pair.getLeft(), color.get(), color.get(), ShapeMode.Lines, 0);
            if (renderOpenPositions.get()) {
                Vec3d openPos = pair.getRight();
                event.renderer.box(openPos.x-indicatorSize.get(), openPos.y-indicatorSize.get(), openPos.z-indicatorSize.get(), openPos.x+indicatorSize.get(), openPos.y+indicatorSize.get(), openPos.z+indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
        }

        if (renderCheckpoints.get()) {
            for (Pair<Vec3d, Pair<String, BlockPos>> pair: checkpoints) {
                Vec3d cp = pair.getLeft();
                event.renderer.box(cp.x-indicatorSize.get(), cp.y-indicatorSize.get(), cp.z-indicatorSize.get(), cp.getX()+indicatorSize.get(), cp.getY()+indicatorSize.get(), cp.getZ()+indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
        }

        if (renderSpecialInteractions.get()) {
            if (reset != null) {
                event.renderer.box(reset.getLeft().getBlockPos(), color.get(), color.get(), ShapeMode.Lines, 0);
                event.renderer.box(reset.getRight().x-indicatorSize.get(), reset.getRight().y-indicatorSize.get(), reset.getRight().z-indicatorSize.get(), reset.getRight().getX()+indicatorSize.get(), reset.getRight().getY()+indicatorSize.get(), reset.getRight().getZ()+indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
            if (cartographyTable != null) {
                event.renderer.box(cartographyTable.getLeft().getBlockPos(), color.get(), color.get(), ShapeMode.Lines, 0);
                event.renderer.box(cartographyTable.getRight().x-indicatorSize.get(), cartographyTable.getRight().y-indicatorSize.get(), cartographyTable.getRight().z-indicatorSize.get(), cartographyTable.getRight().getX()+indicatorSize.get(), cartographyTable.getRight().getY()+indicatorSize.get(), cartographyTable.getRight().getZ()+indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
            if (finishedMapChest != null) {
                event.renderer.box(finishedMapChest.getLeft().getBlockPos(), color.get(), color.get(), ShapeMode.Lines, 0);
                event.renderer.box(finishedMapChest.getRight().x-indicatorSize.get(), finishedMapChest.getRight().y-indicatorSize.get(), finishedMapChest.getRight().z-indicatorSize.get(), finishedMapChest.getRight().getX()+indicatorSize.get(), finishedMapChest.getRight().getY()+indicatorSize.get(), finishedMapChest.getRight().getZ()+indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
        }
    }

    private enum State {
        AwaitContent,
        SelectingReset,
        SelectingChests,
        SelectingFinishedMapChest,
        SelectingTable,
        SelectingMapArea,
        AwaitRestockResponse,
        AwaitResetResponse,
        AwaitDumpResponse,
        AwaitMapChestResponse,
        AwaitFinishedMapChestResponse,
        AwaitCartographyResponse,
        AwaitNBTFile,
        Walking
    }

    private enum SprintMode {
        Off,
        NotPlacing,
        Always
    }
}
