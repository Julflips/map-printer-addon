package com.julflips.nerv_printer.modules;

import com.julflips.nerv_printer.Addon;
import com.julflips.nerv_printer.utils.Utils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.utils.StarscriptTextBoxRenderer;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class FullBlockPrinter extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgError = settings.createGroup("Error Handling");
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

    private final Setting<List<Block>> startBlock = sgGeneral.add(new BlockListSetting.Builder()
        .name("start-Block")
        .description("Which block to interact with to start the printing process.")
        .defaultValue(Blocks.STONE_BUTTON, Blocks.ACACIA_BUTTON, Blocks.BAMBOO_BUTTON, Blocks.BIRCH_BUTTON,
            Blocks.CRIMSON_BUTTON, Blocks.DARK_OAK_BUTTON, Blocks.JUNGLE_BUTTON, Blocks.OAK_BUTTON,
            Blocks.POLISHED_BLACKSTONE_BUTTON, Blocks.SPRUCE_BUTTON, Blocks.WARPED_BUTTON)
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

    private final Setting<Integer> tntDistance = sgGeneral.add(new IntSetting.Builder()
        .name("tnt-distance")
        .description("How many blocks the bot should stay away from the dropped tnt (z axis).")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 30)
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

    private final Setting<Boolean> startResetNorth = sgGeneral.add(new BoolSetting.Builder()
        .name("start-reset-north")
        .description("If true, use the North Reset Trapped Chest first. Use south if not.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SprintMode> sprinting = sgGeneral.add(new EnumSetting.Builder<SprintMode>()
        .name("sprint-mode")
        .description("How to sprint.")
        .defaultValue(SprintMode.NotPlacing)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate when placing a block.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> moveToFinishedFolder = sgGeneral.add(new BoolSetting.Builder()
        .name("move-to-finished-folder")
        .description("Moves finished NBT files into the finished-maps folder in the nerv-printer folder.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> customFolderPath = sgGeneral.add(new BoolSetting.Builder()
        .name("custom-folder-path")
        .description("Allows to set a custom path to the nbt folder.")
        .defaultValue(false)
        .build()
    );

    public final Setting<String> mapPrinterFolderPath = sgGeneral.add(new StringSetting.Builder()
        .name("nerv-printer-folder-path")
        .description("The path to your nerv-printer directory.")
        .defaultValue("C:\\Users\\(username)\\AppData\\Roaming\\.minecraft\\nerv-printer")
        .wide()
        .renderer(StarscriptTextBoxRenderer.class)
        .visible(() -> customFolderPath.get())
        .build()
    );

    private final Setting<Boolean> disableOnFinished = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-on-finished")
        .description("Disables the printer when all nbt files are finished.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> displayMaxRequirements = sgGeneral.add(new BoolSetting.Builder()
        .name("print-max-requirements")
        .description("Print the maximum amount of material needed for all maps in the map-folder.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> debugPrints = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-prints")
        .description("Prints additional information.")
        .defaultValue(false)
        .build()
    );

    //Error Handling

    private final Setting<Boolean> logErrors = sgError.add(new BoolSetting.Builder()
        .name("log-errors")
        .description("Prints warning when a misplacement is detected.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ErrorAction> errorAction = sgError.add(new EnumSetting.Builder<ErrorAction>()
        .name("error-action")
        .description("What to do when a misplacement is detected.")
        .defaultValue(ErrorAction.ToggleOff)
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
    int timeoutTicks;
    int closeResetChestTicks;
    int interactTimeout;
    long lastTickTime;
    boolean closeNextInvPacket;
    boolean atEdge;
    boolean nextResetNorth;
    State state;
    State oldState;
    Pair<BlockHitResult, Vec3d> northReset;
    Pair<BlockHitResult, Vec3d> southReset;
    Pair<BlockHitResult, Vec3d> cartographyTable;
    Pair<BlockHitResult, Vec3d> finishedMapChest;
    ArrayList<Pair<BlockPos, Vec3d>> mapMaterialChests;
    Pair<Vec3d, Pair<Float, Float>> dumpStation;                    //Pos, Yaw, Pitch
    BlockPos mapCorner;
    BlockPos tempChestPos;
    BlockPos lastInteractedChest;
    Block lastSwappedMaterial;
    InventoryS2CPacket toBeHandledInvPacket;
    HashMap<Integer, Pair<Block, Integer>> blockPaletteDict;       //Maps palette block id to the Minecraft block and amount
    HashMap<Block, ArrayList<Pair<BlockPos, Vec3d>>> materialDict; //Maps block to the chest pos and the open position
    ArrayList<Integer> availableSlots;
    ArrayList<Integer> availableHotBarSlots;
    ArrayList<Triple<Block, Integer, Integer>> restockList;        //Material, Stacks, Raw Amount
    ArrayList<BlockPos> checkedChests;
    ArrayList<Pair<Vec3d, Pair<String, BlockPos>>> checkpoints;    //(GoalPos, (checkpointAction, targetBlock))
    ArrayList<File> startedFiles;
    ArrayList<ClickSlotC2SPacket> invActionPackets;
    Block[][] map;
    File mapFolder;
    File mapFile;

    public FullBlockPrinter() {
        super(Addon.CATEGORY, "full-block-printer", "Automatically builds 2D full-block maps from nbt files.");
    }

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
        northReset = null;
        southReset = null;
        mapCorner = null;
        lastInteractedChest = null;
        cartographyTable = null;
        finishedMapChest = null;
        mapMaterialChests = new ArrayList<>();
        dumpStation = null;
        lastSwappedMaterial = null;
        toBeHandledInvPacket = null;
        closeNextInvPacket = false;
        atEdge = false;
        nextResetNorth = startResetNorth.get();
        timeoutTicks = 0;
        interactTimeout = 0;
        closeResetChestTicks = 0;

        if (!customFolderPath.get()) {
            mapFolder = new File(Utils.getMinecraftDirectory(), "nerv-printer");
        } else {
            mapFolder = new File(mapPrinterFolderPath.get());
        }
        if (!Utils.createMapFolder(mapFolder)) {
            toggle();
            return;
        }

        if (displayMaxRequirements.get()) {
            HashMap<Block, Integer> materialCountDict = new HashMap<>();
            for (File file : mapFolder.listFiles()) {
                if (!file.isFile()) continue;
                if (!prepareNextMapFile()) return;
                for (Pair<Block, Integer> material : blockPaletteDict.values()) {
                    if (!materialCountDict.containsKey(material.getLeft())) {
                        materialCountDict.put(material.getLeft(), material.getRight());
                    } else {
                        materialCountDict.put(material.getLeft(), Math.max(materialCountDict.get(material.getLeft()), material.getRight()));
                    }
                }
            }
            info("§aMaterial needed for all files:");
            for (Block block : materialCountDict.keySet()) {
                float shulkerAmount = (float) Math.ceil((float) materialCountDict.get(block) / (float) (27 * 64) * 10) / (float) 10;
                if (shulkerAmount == 0) continue;
                info(block.getName().getString() + ": " + shulkerAmount + " shulker");
            }
            startedFiles.clear();
        }
        if (!prepareNextMapFile()) return;
        info("Building: §a" + mapFile.getName());
        info("Requirements: ");
        for (Pair<Block, Integer> p : blockPaletteDict.values()) {
            if (p.getRight() == 0) continue;
            info(p.getLeft().getName().getString() + ": " + p.getRight());
        }
        state = State.SelectingMapArea;
        info("Select the §aMap Building Area (128x128)");
    }

    private void refillInventory(HashMap<Block, Integer> invMaterial) {
        //Fills restockList with required items
        restockList.clear();
        HashMap<Block, Integer> requiredItems = Utils.getRequiredItems(mapCorner, linesPerRun.get(), availableSlots.size(), map);
        for (Block material : invMaterial.keySet()) {
            int oldAmount = requiredItems.remove(material);
            requiredItems.put(material, oldAmount - invMaterial.get(material));
        }

        for (Block block : requiredItems.keySet()) {
            if (requiredItems.get(block) <= 0) continue;
            int stacks = (int) Math.ceil((float) requiredItems.get(block) / 64f);
            info("Restocking §a" + stacks + " stacks " + block.getName().getString() + " (" + requiredItems.get(block) + ")");
            restockList.add(0, Triple.of(block, stacks, requiredItems.get(block)));
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

    private void calculateBuildingPath(boolean cornerSide, boolean sprintFirst) {
        //Iterate over map and skip completed lines. Player has to be able to see the complete map area
        //Fills checkpoints list
        boolean isStartSide = cornerSide;
        checkpoints.clear();
        for (int x = 0; x < 128; x += linesPerRun.get()) {
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
            Vec3d cp1 = mapCorner.toCenterPos().add(x + linesPerRun.get() - 1, 0, -1);
            Vec3d cp2 = mapCorner.toCenterPos().add(x + linesPerRun.get() - 1, 0, 128);
            if (isStartSide) {
                checkpoints.add(new Pair(cp1, new Pair("nextLine", null)));
                checkpoints.add(new Pair(cp2, new Pair("lineEnd", null)));
            } else {
                checkpoints.add(new Pair(cp2, new Pair("nextLine", null)));
                checkpoints.add(new Pair(cp1, new Pair("lineEnd", null)));
            }
            isStartSide = !isStartSide;
        }
        if (checkpoints.size() > 0 && sprintFirst) {
            //Make player sprint to the start of the map
            Pair<Vec3d, Pair<String, BlockPos>> firstPoint = checkpoints.remove(0);
            checkpoints.add(0, new Pair(firstPoint.getLeft(), new Pair("sprint", firstPoint.getRight().getRight())));
        }
    }

    private boolean arePlacementsCorrect() {
        boolean valid = true;
        for (int x = 0; x < 128; x += linesPerRun.get()) {
            for (int lineBonus = 0; lineBonus < linesPerRun.get(); lineBonus++) {
                if (x + lineBonus > 127) break;
                for (int z = 0; z < 128; z++) {
                    BlockState blockState = mc.world.getBlockState(mapCorner.add(x + lineBonus, 0, z));
                    if (!blockState.isAir()) {
                        if (map[x + lineBonus][z] != blockState.getBlock()) {
                            int xError = x + lineBonus + mapCorner.getX();
                            int zError = z + mapCorner.getZ();
                            if (logErrors.get()) warning("Error at " + xError + ", " + zError + ". " +
                                "Is " + blockState.getBlock().getName().getString() + " - Should be " + map[x + lineBonus][z].getName().getString());
                            valid = false;
                        }
                    }
                }
            }
        }
        return valid;
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof PlayerMoveC2SPacket) {
            if (mc.world.getBlockState(mc.player.getBlockPos().down()).isAir() && state == State.Walking &&
                (checkpoints.get(0).getRight().getLeft() == "" || checkpoints.get(0).getRight().getLeft() == "lineEnd")) {
                atEdge = true;
                Utils.setWPressed(false);
                mc.player.setVelocity(0, 0, 0);
            } else {
                atEdge = false;
            }
        }
        if (state == State.SelectingDumpStation && event.packet instanceof PlayerActionC2SPacket packet
            && packet.getAction() == PlayerActionC2SPacket.Action.DROP_ITEM) {
            dumpStation = new Pair<>(mc.player.getPos(), new Pair<>(mc.player.getYaw(), mc.player.getPitch()));
            state = State.SelectingFinishedMapChest;
            info("Dump Station selected. Select the §aFinished Map Chest");
            return;
        }
        if (!(event.packet instanceof PlayerInteractBlockC2SPacket packet) || state == null) return;
        switch (state) {
            case SelectingMapArea:
                BlockPos hitPos = packet.getBlockHitResult().getBlockPos().offset(packet.getBlockHitResult().getSide());
                int adjustedX = Utils.getIntervalStart(hitPos.getX());
                int adjustedZ = Utils.getIntervalStart(hitPos.getZ());
                mapCorner = new BlockPos(adjustedX, hitPos.getY(), adjustedZ);
                state = State.SelectingNorthReset;
                info("Map Area selected. Press the §aNorth Reset Trapped Chest §7used to remove the built map");
                break;
            case SelectingNorthReset:
                BlockPos blockPos = packet.getBlockHitResult().getBlockPos();
                if (mc.world.getBlockState(blockPos).getBlock() instanceof TrappedChestBlock) {
                    northReset = new Pair<>(packet.getBlockHitResult(), mc.player.getPos());
                    info("North Reset Trapped Chest selected. Select the §aSouth Reset Trapped Chest.");
                    state = State.SelectingSouthReset;
                }
                break;
            case SelectingSouthReset:
                blockPos = packet.getBlockHitResult().getBlockPos();
                if (mc.world.getBlockState(blockPos).getBlock() instanceof TrappedChestBlock) {
                    southReset = new Pair<>(packet.getBlockHitResult(), mc.player.getPos());
                    info("South Reset Trapped Chest selected. Select the §aCartography Table.");
                    state = State.SelectingTable;
                }
                break;
            case SelectingTable:
                blockPos = packet.getBlockHitResult().getBlockPos();
                if (mc.world.getBlockState(blockPos).getBlock().equals(Blocks.CARTOGRAPHY_TABLE)) {
                    cartographyTable = new Pair<>(packet.getBlockHitResult(), mc.player.getPos());
                    info("Cartography Table selected. Throw an item into the §aDump Station.");
                    state = State.SelectingDumpStation;
                }
                break;
            case SelectingFinishedMapChest:
                blockPos = packet.getBlockHitResult().getBlockPos();
                if (mc.world.getBlockState(blockPos).getBlock() instanceof AbstractChestBlock) {
                    finishedMapChest = new Pair<>(packet.getBlockHitResult(), mc.player.getPos());
                    info("Finished Map Chest selected. Select all §aMaterial- and Map-Chests.");
                    state = State.SelectingChests;
                }
                break;
            case SelectingChests:
                if (startBlock.get().isEmpty())
                    warning("No block selected as Start Block! Please select one in the settings.");
                blockPos = packet.getBlockHitResult().getBlockPos();
                BlockState blockState = mc.world.getBlockState(blockPos);
                if (startBlock.get().contains(blockState.getBlock())) {
                    //Check if requirements to start building are met
                    if (materialDict.size() == 0) {
                        warning("No Material Chests selected!");
                        return;
                    }
                    if (mapMaterialChests.size() == 0) {
                        warning("No Map Chests selected!");
                        return;
                    }
                    Utils.setWPressed(true);
                    calculateBuildingPath(true, true);
                    availableSlots = Utils.getAvailableSlots(materialDict);
                    for (int slot : availableSlots) {
                        if (slot < 9) {
                            availableHotBarSlots.add(slot);
                        }
                    }
                    info("Inventory slots available for building: " + availableSlots);

                    HashMap<Block, Integer> requiredItems = Utils.getRequiredItems(mapCorner, linesPerRun.get(), availableSlots.size(), map);
                    Pair<ArrayList<Integer>, HashMap<Block, Integer>> invInformation = Utils.getInvInformation(requiredItems, availableSlots);
                    if (invInformation.getLeft().size() != 0) {
                        checkpoints.add(0, new Pair(dumpStation.getLeft(), new Pair("dump", null)));
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
            for (int i = 0; i < packet.getContents().size() - 36; i++) {
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
            if (isMixedContent) {
                warning("Different items found in chest. Please only have one item type in the chest.");
                return;
            }

            if (foundItem == null) {
                warning("No items found in chest.");
                state = State.SelectingChests;
                return;
            }
            Block chestContentBlock = Registries.BLOCK.get(Identifier.of(foundItem.toString()));
            info("Registered §a" + chestContentBlock.getName().getString());
            if (!materialDict.containsKey(chestContentBlock)) materialDict.put(chestContentBlock, new ArrayList<>());
            ArrayList<Pair<BlockPos, Vec3d>> oldList = materialDict.get(chestContentBlock);
            ArrayList newChestList = Utils.saveAdd(oldList, tempChestPos, mc.player.getPos());
            materialDict.put(chestContentBlock, newChestList);
            state = State.SelectingChests;
        }

        List<State> allowedStates = Arrays.asList(State.AwaitRestockResponse, State.AwaitMapChestResponse,
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
                interactTimeout = 0;
                boolean foundMaterials = false;
                for (int i = 0; i < packet.getContents().size() - 36; i++) {
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
                            checkpoints.add(0, new Pair(dumpStation.getLeft(), new Pair("dump", null)));
                            state = State.Walking;
                            return;
                        }
                        invActionPackets.add(new ClickSlotC2SPacket(packet.getSyncId(), 1, i, 1, SlotActionType.QUICK_MOVE, new ItemStack(Items.AIR), Int2ObjectMaps.emptyMap()));
                        Triple<Block, Integer, Integer> oldTriple = restockList.remove(0);
                        restockList.add(0, Triple.of(oldTriple.getLeft(), oldTriple.getMiddle() - 1, oldTriple.getRight() - 64));
                    }
                }
                if (!foundMaterials) endRestocking();
                break;
            case AwaitMapChestResponse:
                int mapSlot = -1;
                int paneSlot = -1;
                //Search for map and glass pane
                for (int slot = 0; slot < packet.getContents().size() - 36; slot++) {
                    ItemStack stack = packet.getContents().get(slot);
                    if (stack.getItem() == Items.MAP) mapSlot = slot;
                    if (stack.getItem() == Items.GLASS_PANE) paneSlot = slot;
                }
                if (mapSlot == -1 || paneSlot == -1) {
                    warning("Not enough Empty Maps/Glass Panes in Map Material Chest");
                    return;
                }
                interactTimeout = 0;
                timeoutTicks = postRestockDelay.get();
                Utils.getOneItem(mapSlot, false, availableSlots, availableHotBarSlots, packet);
                Utils.getOneItem(paneSlot, true, availableSlots, availableHotBarSlots, packet);
                mc.player.getInventory().selectedSlot = availableHotBarSlots.get(0);

                Vec3d center = mapCorner.add(map.length / 2 - 1, 0, map[0].length / 2 - 1).toCenterPos();
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
                for (int slot = packet.getContents().size() - 36; slot < packet.getContents().size(); slot++) {
                    ItemStack stack = packet.getContents().get(slot);
                    if (stack.getItem() == Items.FILLED_MAP) {
                        mc.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(packet.getSyncId(), 1, slot, 0, SlotActionType.QUICK_MOVE, new ItemStack(Items.AIR), Int2ObjectMaps.emptyMap()));
                        break;
                    }
                }
                if (nextResetNorth) {
                    checkpoints.add(new Pair(northReset.getRight(), new Pair("reset", null)));
                } else {
                    checkpoints.add(new Pair(southReset.getRight(), new Pair("reset", null)));
                }
                state = State.Walking;
                break;
            case AwaitResetResponse:
                interactTimeout = 0;
                closeNextInvPacket = false;
                closeResetChestTicks = resetChestCloseDelay.get();
                break;
        }
    }

    private int getFirstIntactRow() {
        for (int z = 0; z < map[0].length; z++) {
            int adjustedZ = z;
            if (nextResetNorth) adjustedZ = map[0].length - z - 1;
            for (int x = 0; x < map.length; x++) {
                BlockPos pos = new BlockPos(mapCorner.add(x, 0, adjustedZ));
                if (mc.world.getBlockState(pos).isAir()) {
                    return adjustedZ;
                }
            }
        }
        if (nextResetNorth) {
            return -1;
        } else {
            return map[0].length;
        }
    }

    private boolean isCleared() {
        for (int z = 0; z < map[0].length; z++) {
            for (int x = 0; x < map.length; x++) {
                BlockPos pos = new BlockPos(mapCorner.add(x, 0, z));
                if (!mc.world.getBlockState(pos).isAir()) return false;
            }
        }
        return true;
    }

    private void endTNTAvoid() {
        if (nextResetNorth) {
            Vec3d southCP = mapCorner.add(-1, 1, map[0].length).toCenterPos();
            checkpoints.add(new Pair<>(southCP, new Pair<>("sprint", null)));
            Vec3d northCP = mapCorner.add(-1, 1, -1).toCenterPos();
            checkpoints.add(new Pair<>(northCP, new Pair<>("finishedAvoid", null)));
        } else {
            Vec3d centerCP = mapCorner.add(map.length / 2, 1, -1).toCenterPos();
            checkpoints.add(new Pair<>(centerCP, new Pair<>("finishedAvoid", null)));
        }
        nextResetNorth = !nextResetNorth;
        timeoutTicks = resetDelay.get();
        state = State.AwaitNBTFile;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (state == null) return;

        if (oldState != state) {
            oldState = state;
            if (debugPrints.get()) info("Changed state to " + state.name());
        }

        long timeDifference = System.currentTimeMillis() - lastTickTime;
        int allowedPlacements = (int) Math.floor(timeDifference / (long) placeDelay.get());
        lastTickTime += (long) allowedPlacements * placeDelay.get();

        if (interactTimeout > 0) {
            interactTimeout--;
            if (interactTimeout == 0) {
                info("Interaction timed out. Interacting again...");
                if (state == State.AwaitCartographyResponse) {
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
                state = State.AvoidTNT;
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
            } else {
                timeoutTicks = invActionDelay.get();
            }
            return;
        }

        if (state == State.Dumping) {
            int dumpSlot = getDumpSlot();
            if (dumpSlot == -1) {
                HashMap<Block, Integer> requiredItems = Utils.getRequiredItems(mapCorner, linesPerRun.get(), availableSlots.size(), map);
                Pair<ArrayList<Integer>, HashMap<Block, Integer>> invInformation = Utils.getInvInformation(requiredItems, availableSlots);
                refillInventory(invInformation.getRight());
                state = State.Walking;
            } else {
                if (debugPrints.get())
                    info("Dumping §a" + mc.player.getInventory().getStack(dumpSlot).getName().getString() + " (slot " + dumpSlot + ")");
                InvUtils.drop().slot(dumpSlot);
                timeoutTicks = invActionDelay.get();
            }
        }

        if (state == State.AvoidTNT) {
            if (isCleared()) {
                endTNTAvoid();
                return;
            }
            int offset = tntDistance.get();
            if (!nextResetNorth) offset *= -1;
            Vec3d targetPos = mapCorner.add(map.length / 2, 1, getFirstIntactRow() + offset).toCenterPos();
            targetPos.add(0, mc.player.getY() - targetPos.y, 0);
            if (PlayerUtils.distanceTo(targetPos) > 0.9) {
                checkpoints.add(0, new Pair<>(targetPos, new Pair<>("switchAvoidTNT", null)));
                state = State.Walking;
                Utils.setWPressed(true);
            }
            return;
        }

        if (state == State.AwaitNBTFile) {
            if (!prepareNextMapFile()) return;
            info("Building: §a" + mapFile.getName());
            info("Requirements: ");
            for (Pair<Block, Integer> p : blockPaletteDict.values()) {
                if (p.getRight() == 0) continue;
                info(p.getLeft().getName().getString() + ": " + p.getRight());
            }
            state = State.Walking;
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
        if (!atEdge) Utils.setWPressed(true);
        if (checkpoints.isEmpty()) {
            error("Checkpoints are empty. Stopping...");
            Utils.setWPressed(false);
            toggle();
            return;
        }
        Vec3d goal = checkpoints.get(0).getLeft();
        if (PlayerUtils.distanceTo(goal.add(0, mc.player.getY() - goal.y, 0)) < checkpointBuffer.get()) {
            Pair<String, BlockPos> checkpointAction = checkpoints.get(0).getRight();
            if (debugPrints.get() && checkpointAction.getLeft() != null) info("Reached " + checkpointAction.getLeft());
            checkpoints.remove(0);
            mc.player.setPosition(goal.getX(), mc.player.getY(), goal.getZ());
            mc.player.setVelocity(0, 0, 0);
            switch (checkpointAction.getLeft()) {
                case "lineEnd":
                    arePlacementsCorrect();
                    boolean atCornerSide = goal.z == mapCorner.north().toCenterPos().z;
                    calculateBuildingPath(atCornerSide, false);
                    break;
                case "mapMaterialChest":
                    BlockPos mapMaterialChest = getBestChest(Blocks.CARTOGRAPHY_TABLE).getLeft();
                    interactWithBlock(mapMaterialChest);
                    state = State.AwaitMapChestResponse;
                    return;
                case "fillMap":
                    mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, Utils.getNextInteractID(), mc.player.getYaw(), mc.player.getPitch()));
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
                    state = State.AwaitCartographyResponse;
                    interactWithBlock(cartographyTable.getLeft());
                    return;
                case "finishedMapChest":
                    state = State.AwaitFinishedMapChestResponse;
                    interactWithBlock(finishedMapChest.getLeft().getBlockPos());
                    return;
                case "reset":
                    state = State.AwaitResetResponse;
                    info("Resetting...");
                    if (nextResetNorth) {
                        interactWithBlock(northReset.getLeft());
                        lastInteractedChest = northReset.getLeft().getBlockPos();
                    } else {
                        interactWithBlock(southReset.getLeft());
                        lastInteractedChest = southReset.getLeft().getBlockPos();
                    }
                    return;
                case "switchAvoidTNT":
                    state = State.AvoidTNT;
                    Utils.setWPressed(false);
                    return;
                case "finishedAvoid":
                    calculateBuildingPath(true, true);
                    checkpoints.add(0, new Pair(dumpStation.getLeft(), new Pair("dump", null)));
                    return;
                case "dump":
                    state = State.Dumping;
                    Utils.setWPressed(false);
                    mc.player.setYaw(dumpStation.getRight().getLeft());
                    mc.player.setPitch(dumpStation.getRight().getRight());
                    return;
                case "refill":
                    state = State.AwaitRestockResponse;
                    interactWithBlock(checkpointAction.getRight());
                    return;
            }
            if (checkpoints.size() == 0) {
                if (!arePlacementsCorrect() && errorAction.get() == ErrorAction.ToggleOff) {
                    checkpoints.add(new Pair(mc.player.getPos(), new Pair("lineEnd", null)));
                    warning("ErrorAction is ToggleOff: Stopping because of error...");
                    toggle();
                    return;
                }
                info("Finished building map");
                Pair<BlockPos, Vec3d> bestChest = getBestChest(Blocks.CARTOGRAPHY_TABLE);
                checkpoints.add(0, new Pair(bestChest.getRight(), new Pair("mapMaterialChest", bestChest.getLeft())));
                try {
                    if (moveToFinishedFolder.get()) {
                        mapFile.renameTo(new File(mapFile.getParentFile().getAbsolutePath() + File.separator + "_finished_maps" + File.separator + mapFile.getName()));
                    }
                } catch (Exception e) {
                    warning("Failed to move map file " + mapFile.getName() + " to finished map folder");
                    e.printStackTrace();
                }
                checkpoints.add(0, new Pair(dumpStation.getLeft(), new Pair("dump", null)));
            }
            goal = checkpoints.get(0).getLeft();
        }
        mc.player.setYaw((float) Rotations.getYaw(goal));
        String nextAction = checkpoints.get(0).getRight().getLeft();

        if ((nextAction == "" || nextAction == "lineEnd") && sprinting.get() != SprintMode.Always) {
            mc.player.setSprinting(false);
        } else if (sprinting.get() != SprintMode.Off) {
            mc.player.setSprinting(true);
        }
        if (nextAction == "refill" || nextAction == "dump" || nextAction == "walkRestock"
            || nextAction == "switchAvoidTNT" || nextAction == "nextLine") return;

        ArrayList<BlockPos> placements = new ArrayList<>();
        for (int i = 0; i < allowedPlacements; i++) {
            AtomicReference<BlockPos> closestPos = new AtomicReference<>();
            final Vec3d currentGoal = goal;
            BlockPos playerGroundPos = mc.player.getBlockPos().add(0, mapCorner.getY() - mc.player.getBlockY(), 0);
            Utils.iterateBlocks(playerGroundPos, (int) Math.ceil(placeRange.get()) + 1, 0, ((blockPos, blockState) -> {
                Double posDistance = PlayerUtils.distanceTo(blockPos.toCenterPos());
                if ((blockState.isAir()) && posDistance <= placeRange.get() && isWithingMap(blockPos)
                    && blockPos.getX() <= currentGoal.getX() && !placements.contains(blockPos)) {
                    if (closestPos.get() == null) {
                        if (!mc.world.getBlockState(blockPos.west()).isAir())
                            closestPos.set(new BlockPos(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
                        return;
                    }
                    int blockPosZDiff = Math.abs(mc.player.getBlockPos().getZ() - blockPos.getZ());
                    int closestPosZDiff = Math.abs(mc.player.getBlockPos().getZ() - closestPos.get().getZ());
                    if (!mc.world.getBlockState(blockPos.west()).isAir() && (blockPosZDiff < closestPosZDiff ||
                        (blockPosZDiff == closestPosZDiff && blockPos.getX() < closestPos.get().getX()))) {
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

    private int getDumpSlot() {
        HashMap<Block, Integer> requiredItems = Utils.getRequiredItems(mapCorner, linesPerRun.get(), availableSlots.size(), map);
        Pair<ArrayList<Integer>, HashMap<Block, Integer>> invInformation = Utils.getInvInformation(requiredItems, availableSlots);
        if (invInformation.getLeft().isEmpty()) {
            return -1;
        }
        return invInformation.getLeft().get(0);
    }

    private boolean tryPlacingBlock(BlockPos pos) {
        BlockPos relativePos = pos.subtract(mapCorner);
        Block material = map[relativePos.getX()][relativePos.getZ()];
        //info("Placing " + material.getName().getString() + " at: " + relativePos.toShortString());
        //Check hot-bar slots
        for (int slot : availableHotBarSlots) {
            if (mc.player.getInventory().getStack(slot).isEmpty()) continue;
            Block foundMaterial = Registries.BLOCK.get(Identifier.of(mc.player.getInventory().getStack(slot).getItem().toString()));
            if (foundMaterial.equals(material)) {
                BlockUtils.place(pos, Hand.MAIN_HAND, slot, rotate.get(), 50, true, true, false);
                if (material == lastSwappedMaterial) lastSwappedMaterial = null;
                return true;
            }
        }
        for (int slot : availableSlots) {
            if (mc.player.getInventory().getStack(slot).isEmpty() || availableHotBarSlots.contains(slot)) continue;
            Block foundMaterial = Registries.BLOCK.get(Identifier.of(mc.player.getInventory().getStack(slot).getItem().toString()));
            if (foundMaterial.equals(material)) {
                lastSwappedMaterial = material;
                Utils.swapIntoHotbar(slot, availableHotBarSlots);
                //BlockUtils.place(pos, Hand.MAIN_HAND, resultSlot, true,50, true, true, false);
                Utils.setWPressed(false);
                mc.player.setVelocity(0, 0, 0);
                timeoutTicks = swapDelay.get();
                return false;
            }
        }
        if (lastSwappedMaterial == material) return false;      //Wait for swapped material
        info("No " + material.getName().getString() + " found in inventory. Resetting...");
        Vec3d pathCheckpoint1 = mc.player.getPos().offset(Direction.WEST, linesPerRun.get());
        Vec3d pathCheckpoint2 = new Vec3d(pathCheckpoint1.getX(), pathCheckpoint1.y, mapCorner.north().toCenterPos().getZ());
        checkpoints.add(0, new Pair(mc.player.getPos(), new Pair("walkRestock", null)));
        checkpoints.add(0, new Pair(pathCheckpoint1, new Pair("walkRestock", null)));
        checkpoints.add(0, new Pair(pathCheckpoint2, new Pair("walkRestock", null)));
        checkpoints.add(0, new Pair(dumpStation.getLeft(), new Pair("dump", null)));
        checkpoints.add(0, new Pair(pathCheckpoint2, new Pair("walkRestock", null)));
        checkpoints.add(0, new Pair(pathCheckpoint1, new Pair("walkRestock", null)));
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
        if (material.equals(Blocks.CARTOGRAPHY_TABLE)) {
            list = mapMaterialChests;
        } else if (materialDict.containsKey(material)) {
            list = materialDict.get(material);
        } else {
            warning("No chest found for " + material.getName().getString());
            toggle();
            return new Pair<>(new BlockPos(0, 0, 0), new Vec3d(0, 0, 0));
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
            checkedChests.clear();
            return getBestChest(material);
        }
        return new Pair(bestChestPos, bestPos);
    }

    private void interactWithBlock(BlockPos chestPos) {
        Utils.setWPressed(false);
        mc.player.setVelocity(0, 0, 0);
        mc.player.setYaw((float) Rotations.getYaw(chestPos.toCenterPos()));
        mc.player.setPitch((float) Rotations.getPitch(chestPos.toCenterPos()));

        BlockHitResult hitResult = new BlockHitResult(chestPos.toCenterPos(), Utils.getInteractionSide(chestPos), chestPos, false);
        BlockUtils.interact(hitResult, Hand.MAIN_HAND, true);
        //Set timeout for chest interaction
        interactTimeout = retryInteractTimer.get();
        lastInteractedChest = chestPos;
    }

    private void interactWithBlock(BlockHitResult hitResult) {
        Utils.setWPressed(false);
        mc.player.setVelocity(0, 0, 0);
        mc.player.setYaw((float) Rotations.getYaw(hitResult.getBlockPos().toCenterPos()));
        mc.player.setPitch((float) Rotations.getPitch(hitResult.getBlockPos().toCenterPos()));
        BlockUtils.interact(hitResult, Hand.MAIN_HAND, true);
        interactTimeout = retryInteractTimer.get();
    }

    private boolean isWithingMap(BlockPos pos) {
        BlockPos relativePos = pos.subtract(mapCorner);
        return relativePos.getX() >= 0 && relativePos.getX() < map.length && relativePos.getZ() >= 0 && relativePos.getZ() < map[0].length;
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

    private boolean prepareNextMapFile() {
        mapFile = Utils.getNextMapFile(mapFolder, startedFiles, moveToFinishedFolder.get());

        if (mapFile == null) {
            if (disableOnFinished.get()) {
                info("All nbt files finished");
                toggle();
                return false;
            } else {
                return false;
            }
        }
        if (!loadNBTFile()) {
            warning("Failed to read nbt file.");
            toggle();
            return false;
        }

        return true;
    }

    private boolean loadNBTFile() {
        try {
            NbtSizeTracker sizeTracker = new NbtSizeTracker(0x20000000L, 100);
            NbtCompound nbt = NbtIo.readCompressed(mapFile.toPath(), sizeTracker);
            //Extracting the palette
            NbtList paletteList = (NbtList) nbt.get("palette");
            blockPaletteDict = Utils.getBlockPalette(paletteList);

            NbtList blockList = (NbtList) nbt.get("blocks");
            map = Utils.generateMapArray(blockList, blockPaletteDict);

            //Check if a full 128x128 map is present
            for (int x = 0; x < map.length; x++) {
                for (int z = 0; z < map[x].length; z++) {
                    if (map[x][z] == null) {
                        warning("No 2D 128x128 map present in file: " + mapFile.getName());
                        return false;
                    }
                }
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
        if (mapCorner == null || !render.get()) return;
        event.renderer.box(mapCorner, color.get(), color.get(), ShapeMode.Lines, 0);
        event.renderer.box(mapCorner.getX(), mapCorner.getY(), mapCorner.getZ(), mapCorner.getX() + 128, mapCorner.getY(), mapCorner.getZ() + 128, color.get(), color.get(), ShapeMode.Lines, 0);

        ArrayList<Pair<BlockPos, Vec3d>> renderedPairs = new ArrayList<>();
        for (ArrayList<Pair<BlockPos, Vec3d>> list : materialDict.values()) {
            renderedPairs.addAll(list);
        }
        renderedPairs.addAll(mapMaterialChests);
        for (Pair<BlockPos, Vec3d> pair : renderedPairs) {
            if (renderChestPositions.get())
                event.renderer.box(pair.getLeft(), color.get(), color.get(), ShapeMode.Lines, 0);
            if (renderOpenPositions.get()) {
                Vec3d openPos = pair.getRight();
                event.renderer.box(openPos.x - indicatorSize.get(), openPos.y - indicatorSize.get(), openPos.z - indicatorSize.get(), openPos.x + indicatorSize.get(), openPos.y + indicatorSize.get(), openPos.z + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
        }

        if (renderCheckpoints.get()) {
            for (Pair<Vec3d, Pair<String, BlockPos>> pair : checkpoints) {
                Vec3d cp = pair.getLeft();
                event.renderer.box(cp.x - indicatorSize.get(), cp.y - indicatorSize.get(), cp.z - indicatorSize.get(), cp.getX() + indicatorSize.get(), cp.getY() + indicatorSize.get(), cp.getZ() + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
        }

        if (renderSpecialInteractions.get()) {
            if (northReset != null) {
                event.renderer.box(northReset.getLeft().getBlockPos(), color.get(), color.get(), ShapeMode.Lines, 0);
                event.renderer.box(northReset.getRight().x - indicatorSize.get(), northReset.getRight().y - indicatorSize.get(), northReset.getRight().z - indicatorSize.get(), northReset.getRight().getX() + indicatorSize.get(), northReset.getRight().getY() + indicatorSize.get(), northReset.getRight().getZ() + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
            if (southReset != null) {
                event.renderer.box(southReset.getLeft().getBlockPos(), color.get(), color.get(), ShapeMode.Lines, 0);
                event.renderer.box(southReset.getRight().x - indicatorSize.get(), northReset.getRight().y - indicatorSize.get(), southReset.getRight().z - indicatorSize.get(), southReset.getRight().getX() + indicatorSize.get(), southReset.getRight().getY() + indicatorSize.get(), southReset.getRight().getZ() + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
            if (cartographyTable != null) {
                event.renderer.box(cartographyTable.getLeft().getBlockPos(), color.get(), color.get(), ShapeMode.Lines, 0);
                event.renderer.box(cartographyTable.getRight().x - indicatorSize.get(), cartographyTable.getRight().y - indicatorSize.get(), cartographyTable.getRight().z - indicatorSize.get(), cartographyTable.getRight().getX() + indicatorSize.get(), cartographyTable.getRight().getY() + indicatorSize.get(), cartographyTable.getRight().getZ() + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
            if (dumpStation != null) {
                event.renderer.box(dumpStation.getLeft().x - indicatorSize.get(), dumpStation.getLeft().y - indicatorSize.get(), dumpStation.getLeft().z - indicatorSize.get(), dumpStation.getLeft().getX() + indicatorSize.get(), dumpStation.getLeft().getY() + indicatorSize.get(), dumpStation.getLeft().getZ() + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
            if (finishedMapChest != null) {
                event.renderer.box(finishedMapChest.getLeft().getBlockPos(), color.get(), color.get(), ShapeMode.Lines, 0);
                event.renderer.box(finishedMapChest.getRight().x - indicatorSize.get(), finishedMapChest.getRight().y - indicatorSize.get(), finishedMapChest.getRight().z - indicatorSize.get(), finishedMapChest.getRight().getX() + indicatorSize.get(), finishedMapChest.getRight().getY() + indicatorSize.get(), finishedMapChest.getRight().getZ() + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
        }
    }

    private enum State {
        AwaitContent,
        SelectingNorthReset,
        SelectingSouthReset,
        SelectingChests,
        SelectingFinishedMapChest,
        SelectingDumpStation,
        SelectingTable,
        SelectingMapArea,
        AwaitRestockResponse,
        AwaitResetResponse,
        AwaitMapChestResponse,
        AwaitFinishedMapChestResponse,
        AwaitCartographyResponse,
        AwaitNBTFile,
        AvoidTNT,
        Walking,
        Dumping
    }

    private enum SprintMode {
        Off,
        NotPlacing,
        Always
    }

    private enum ErrorAction {
        Ignore,
        ToggleOff
    }
}
