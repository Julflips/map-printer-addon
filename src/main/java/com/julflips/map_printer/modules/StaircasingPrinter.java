package com.julflips.map_printer.modules;

import com.julflips.map_printer.Addon;
import com.julflips.map_printer.utils.Utils;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.gui.utils.StarscriptTextBoxRenderer;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.util.Pair;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.tuple.Triple;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class StaircasingPrinter extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgError = settings.createGroup("Error Handling");
    private final SettingGroup sgRender = settings.createGroup("Render");

    //General

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

    private final Setting<Boolean> moveToFinishedFolder = sgGeneral.add(new BoolSetting.Builder()
        .name("move-to-finished-folder")
        .description("Moves finished NBT files into the finished-maps folder in the map-printer folder.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoFolderDetection = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-folder-detection")
        .description("Attempts to automatically find the path to your Minecraft directory.")
        .defaultValue(true)
        .build()
    );

    public final Setting<String> mapPrinterFolderPath = sgGeneral.add(new StringSetting.Builder()
        .name("map-printer-folder-path")
        .description("The path to your map-printer directory.")
        .defaultValue("C:\\Users\\(username)\\AppData\\Roaming\\.minecraft\\map-printer")
        .wide()
        .renderer(StarscriptTextBoxRenderer.class)
        .visible(() -> !autoFolderDetection.get())
        .build()
    );

    private final Setting<Boolean> disableOnFinished = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-on-finished")
        .description("Disables the printer when all nbt files are finished.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> printMaxRequirements = sgGeneral.add(new BoolSetting.Builder()
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

    public StaircasingPrinter() {
        super(Addon.CATEGORY, "staircasing-printer", "Automatically builds full-block maps with staircasing from nbt files.");
    }

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
    Pair<Block, Integer>[][] map;
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
        timeoutTicks = 0;
        interactTimeout = 0;
        closeResetChestTicks = 0;

        if (autoFolderDetection.get()) {
            mapFolder = new File(Utils.getMinecraftDirectory() + File.separator + "map-printer");
        } else {
            mapFolder = new File(mapPrinterFolderPath.get());
        }
        if (!Utils.createMapFolder(mapFolder)) {
            toggle();
            return;
        }

        if (printMaxRequirements.get()) {
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
        HashMap<Block, Integer> requiredItems = getRequiredItems(mapCorner, availableSlots.size(), map);
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

    private void calculateBuildingPath(boolean cornerSide, boolean sprintFirst) {
        //Iterate over map and skip completed lines. Player has to be able to see the complete map area
        //Fills checkpoints list
        checkpoints.clear();
        for (int x = 0; x < 128; x++) {
            boolean lineFinished = true;
            for (int z = 0; z < 128; z++) {
                BlockState blockstate = mc.world.getBlockState(mapCorner.add(x, map[x][z].getRight(), z));
                if (blockstate.isAir()) {
                    lineFinished = false;
                    break;
                }
            }
            if (lineFinished) continue;
            Vec3d cp1 = mapCorner.toCenterPos().add(x,0,-1);
            Vec3d cp2 = mapCorner.toCenterPos().add(x,map[x][127].getRight(),128);
            checkpoints.add(new Pair(cp1, new Pair("nextLine", null)));
            checkpoints.add(new Pair(cp2, new Pair("lineEnd", null)));
        }
        if (checkpoints.size() > 0 && sprintFirst) {
            //Make player sprint to the start of the map
            Pair<Vec3d, Pair<String, BlockPos>>firstPoint = checkpoints.remove(0);
            checkpoints.add(0, new Pair(firstPoint.getLeft(), new Pair("sprint", firstPoint.getRight().getRight())));
        }
    }

    private boolean arePlacementsCorrect() {
        boolean valid = true;
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) {
                BlockState blockState = mc.world.getBlockState(mapCorner.add(x , 0, z));
                if (!blockState.isAir()) {
                    if (map[x][z].getLeft() != blockState.getBlock()) {
                        int xError = x + mapCorner.getX();
                        int zError = z + mapCorner.getZ();
                        if (logErrors.get()) warning("Error at "+xError+", "+zError+". " +
                            "Is "+blockState.getBlock().getName().getString()+" - Should be "+map[x][z].getLeft().getName().getString());
                        valid = false;
                    }
                }
            }
        }
        return valid;
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof PlayerMoveC2SPacket) {
            // ToDo: Do jump stuff here
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
                blockPos = packet.getBlockHitResult().getBlockPos();
                if (blockPos.offset(packet.getBlockHitResult().getSide()).equals(mapCorner)) {
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

                    HashMap<Block, Integer> requiredItems = getRequiredItems(mapCorner, availableSlots.size(), map);
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

    private boolean prepareNextMapFile() {
        for (File file : mapFolder.listFiles()) {
            if (!startedFiles.contains(file) && file.isFile()) {
                startedFiles.add(file);
                mapFile = file;
                break;
            }
        }
        if (mapFile == null) {
            warning("No nbt files found in map-printer folder.");
            toggle();
            return false;
        }

        if (!loadNBTFile(mapFile)) {
            warning("Failed to read nbt file.");
            toggle();
            return false;
        }
        return true;
    }

    private boolean loadNBTFile(File file) {
        try {
            NbtSizeTracker sizeTracker = new NbtSizeTracker(0x20000000L, 100);
            NbtCompound nbt = NbtIo.readCompressed(file.toPath(), sizeTracker);
            //Extracting the palette
            NbtList paletteList = (NbtList) nbt.get("palette");
            blockPaletteDict = Utils.getBlockPalette(paletteList);

            NbtList blockList = (NbtList) nbt.get("blocks");
            map = generateMapArray(blockList);

            //Check if a full 128x128 map is present
            for (int x = 0; x < map.length; x++) {
                for (int z = 0; z < map[x].length; z++) {
                    if (map[x][z] == null) {
                        warning("No 128x129 (extra line on north side) map present in file: " + file.getName());
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

    private Pair<Block, Integer>[][] generateMapArray(NbtList blockList) {
        // Get the highest block of each column
        Pair<Block, Integer>[][] absoluteHeightMap = new Pair[128][129];
        for (int i = 0; i < blockList.size(); i++) {
            NbtCompound block = blockList.getCompound(i);
            int blockId = block.getInt("state");
            if (!blockPaletteDict.containsKey(blockId)) continue;
            NbtList pos = block.getList("pos", 3);
            int x = pos.getInt(0);
            int y = pos.getInt(1);
            int z = pos.getInt(2);
            if (absoluteHeightMap[x][z] == null || absoluteHeightMap[x][z].getRight() < y) {
                Block material = blockPaletteDict.get(block.getInt("state")).getLeft();
                absoluteHeightMap[x][z] = new Pair<>(material, y);
            }
        }
        // Smooth the y pos out to max 1 block difference
        Pair<Block, Integer>[][] smoothedHeightMap = new Pair[128][128];
        for (int x = 0; x < absoluteHeightMap.length; x++) {
            int totalYDiff = 0;
            for (int z = 1; z < absoluteHeightMap[0].length; z++) {
                int predecessorY = absoluteHeightMap[x][z-1].getRight();
                int currentY = absoluteHeightMap[x][z].getRight();
                totalYDiff += Math.max(-1, Math.min(currentY - predecessorY, 1));
                smoothedHeightMap[x][z-1] = new Pair<>(absoluteHeightMap[x][z].getLeft(), totalYDiff);
            }
        }
        return smoothedHeightMap;
    }

    public HashMap<Block, Integer> getRequiredItems(BlockPos mapCorner, int availableSlotsSize, Pair<Block, Integer>[][] map) {
        //Calculate the next items to restock
        //Iterate over map. Player has to be able to see the complete map area
        HashMap<Block, Integer> requiredItems = new HashMap<>();
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) {
                BlockState blockState = mc.world.getBlockState(mapCorner.add(x, 0, z));
                if (blockState.isAir() && map[x][z] != null) {
                    //ChatUtils.info("Add material for: " + mapCorner.add(x + lineBonus, 0, adjustedZ).toShortString());
                    Block material = map[x][z].getLeft();
                    if (!requiredItems.containsKey(material)) requiredItems.put(material, 0);
                    requiredItems.put(material, requiredItems.get(material) + 1);
                    //Check if the item fits into inventory. If not, undo the last increment and return
                    if (Utils.stacksRequired(requiredItems) > availableSlotsSize) {
                        requiredItems.put(material, requiredItems.get(material) - 1);
                        return requiredItems;
                    }
                }
            }
        }
        return requiredItems;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (map == null) return;
        /*BlockPos blockPos = mc.player.getBlockPos();
        for (int x = 0; x < map.length; x++) {
            for (int z = 0; z < map[0].length; z++) {
                BlockPos renderPos = blockPos.add(x, map[x][z].getRight(), z);
                event.renderer.box(renderPos, color.get(), color.get(), ShapeMode.Lines, 0);
            }
        }*/
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
