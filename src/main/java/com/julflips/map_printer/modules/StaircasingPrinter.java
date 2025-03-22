package com.julflips.map_printer.modules;

import com.julflips.map_printer.Addon;
import com.julflips.map_printer.utils.Utils;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.gui.utils.StarscriptTextBoxRenderer;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.util.Pair;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.tuple.Triple;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class StaircasingPrinter extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgError = settings.createGroup("Error Handling");
    private final SettingGroup sgRender = settings.createGroup("Render");

    //General

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

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (map == null) return;
        BlockPos blockPos = mc.player.getBlockPos();
        for (int x = 0; x < map.length; x++) {
            for (int z = 0; z < map[0].length; z++) {
                BlockPos renderPos = blockPos.add(x, map[x][z].getRight(), z);
                event.renderer.box(renderPos, color.get(), color.get(), ShapeMode.Lines, 0);
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
