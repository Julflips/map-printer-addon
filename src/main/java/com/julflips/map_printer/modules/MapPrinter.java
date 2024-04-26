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
import meteordevelopment.meteorclient.utils.misc.input.Input;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class MapPrinter extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> linesPerRun = sgGeneral.add(new IntSetting.Builder()
        .name("lines-per-run")
        .description("How many lines to place in parallel per run.")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Double> checkpointBuffer = sgGeneral.add(new DoubleSetting.Builder()
        .name("checkpoint-buffer")
        .description("The buffer area of the checkpoints. Larger means less precise walking, but might be desired at higher speeds.")
        .defaultValue(0.1)
        .min(0)
        .sliderRange(0, 1)
        .build()
    );

    private final Setting<Integer> mapFillSquareSize = sgGeneral.add(new IntSetting.Builder()
        .name("map-fill-square-size")
        .description("The radius of the square the bot fill walk to explore the map.")
        .defaultValue(10)
        .min(0)
        .sliderRange(0, 50)
        .build()
    );

    private final Setting<Integer> preRestockDelay = sgGeneral.add(new IntSetting.Builder()
        .name("pre-restock-delay")
        .description("How many ticks to wait to take items after opening the chest.")
        .defaultValue(10)
        .min(0)
        .sliderRange(0, 40)
        .build()
    );

    private final Setting<Integer> invActionDelay = sgGeneral.add(new IntSetting.Builder()
        .name("inventory-action-delay")
        .description("How many ticks to wait between each inventory action (moving a stack).")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 40)
        .build()
    );

    private final Setting<Integer> postRestockDelay = sgGeneral.add(new IntSetting.Builder()
        .name("post-restock-delay")
        .description("How many ticks to wait after restocking.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Integer> swapDelay = sgGeneral.add(new IntSetting.Builder()
        .name("swap-delay")
        .description("How many ticks to wait before swapping into hotbar.")
        .defaultValue(5)
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

    private final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("The maximum range you can place carpets around yourself.")
        .defaultValue(4)
        .min(1)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("How many ticks to wait after placing.")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Boolean> activationReset = sgGeneral.add(new BoolSetting.Builder()
        .name("activation-reset")
        .description("Disable if the bot should continue after reconnecting to the server.")
        .defaultValue(true)
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

    public MapPrinter() {
        super(Addon.CATEGORY, "map-printer", "Automatically builds 2D maps from nbt files.");
    }

    int timeoutTicks;
    int placeDelayticks;
    boolean hasRestocked;
    boolean newMap;
    boolean closeNextInvPacket;
    String state;
    String checkpointAction;
    Pair<BlockHitResult, Vec3d> reset;
    Pair<BlockHitResult, Vec3d> cartographyTable;
    Pair<BlockHitResult, Vec3d> finishedMapChest;
    ArrayList<Pair<BlockPos, Vec3d>> mapMaterialChests;
    ArrayList<Pair<BlockPos, Vec3d>> dumpChests;
    BlockPos mapCorner;
    BlockPos tempChestPos;
    InventoryS2CPacket toBeHandledInvPacket;
    HashMap<Integer, Pair<Block, Integer>> carpetDict;             //Maps palette block id to the Minecraft block and amount
    HashMap<Block, ArrayList<Pair<BlockPos, Vec3d>>> materialDict; //Maps block to the chest pos and the open position
    ArrayList<Integer> availableSlots;
    ArrayList<Integer> availableHotBarSlots;
    ArrayList<Triple<BlockPos, Vec3d, Integer>> restockList;       //ChestPos, OpenPos, Amount
    ArrayList<Vec3d> checkpoints;
    ArrayList<File> startedFiles;
    ArrayList<ClickSlotC2SPacket> invActionPackets;
    Block[][] map;
    File mapFolder;
    BlockPos currentDumpChest;

    @Override
    public void onActivate() {
        if (!activationReset.get() && checkpointAction != null) {
            return;
        }
        checkpointAction = "";
        materialDict = new HashMap<>();
        availableSlots = new ArrayList<>();
        availableHotBarSlots = new ArrayList<>();
        restockList = new ArrayList<>();
        checkpoints = new ArrayList<>();
        startedFiles = new ArrayList<>();
        invActionPackets = new ArrayList<>();
        reset = null;
        mapCorner = null;
        cartographyTable = null;
        finishedMapChest = null;
        mapMaterialChests = new ArrayList<>();
        dumpChests = new ArrayList<>();
        toBeHandledInvPacket = null;
        closeNextInvPacket = false;
        hasRestocked = false;
        newMap = false;
        currentDumpChest = null;
        timeoutTicks = 0;
        placeDelayticks = 0;
        mapFolder = new File(Utils.getMinecraftDirectory() + File.separator + "map-printer");
        if (!mapFolder.exists()) {
            boolean created = mapFolder.mkdir();
            if (created) {
                info("Created map-printer folder in Minecraft directory");
            } else {
                warning("Failed to create map-printer folder in Minecraft directory");
                toggle();
                return;
            }
        }
        File mapFile = getNextMapFile();
        if (mapFile == null) {
            warning("No nbt files found in map-printer folder.");
            toggle();
            return;
        }
        if (!loadNBTFiles(mapFile)) {
            warning("Failed to read nbt file.");
            toggle();
            return;
        }
        state = "SelectingCorner";
        info("Select the §aMap Building Area (128x128)");
    }

    private int stacksRequired(HashMap<Block, Integer> requiredItems) {
        int stacks = 0;
        for (int amount: requiredItems.values()) {
            if (amount == 0) continue;
            stacks += Math.ceil((float) amount / 64f);
        }
        return stacks;
    }

    private HashMap<Block, Integer> getRequiredItems() {
        HashMap<Block, Integer> requiredItems = new HashMap<>();
        for (Pair<Block, Integer> p : carpetDict.values()) {
            requiredItems.put(p.getLeft(), 0);
        }

        boolean isStartSide = true;
        for (int x = 0; x <= 128-linesPerRun.get(); x += linesPerRun.get()) {
            for (int z = 0; z < 128; z++) {
                for (int lineBonus = 0; lineBonus < linesPerRun.get(); lineBonus++) {
                    int adjustedZ = z;
                    if (!isStartSide) adjustedZ = 127 - z;
                    //info("x: "+ (x + lineBonus) + " z: " +  adjustedZ);
                    Block cureentBlock = mc.world.getBlockState(mapCorner.add(x + lineBonus, 0, adjustedZ)).getBlock();
                    if (!(cureentBlock instanceof CarpetBlock)) {
                        Block material = map[x+lineBonus][adjustedZ];
                        requiredItems.put(material, requiredItems.get(material) + 1);
                        if (stacksRequired(requiredItems) > availableSlots.size()) {
                            //Undo the last increment
                            requiredItems.put(material, requiredItems.get(material) - 1);
                            return requiredItems;
                        }
                    }
                }
            }
            isStartSide = !isStartSide;
        }
        return requiredItems;
    }

    private void refillInventory() {
        HashMap<Block, Integer> requiredItems = getRequiredItems();

        for (Block block: requiredItems.keySet()) {
            if (requiredItems.get(block) == 0) continue;
            Pair<BlockPos, Vec3d> bestRestockPos = getBestChest(block);
            if (bestRestockPos.getLeft() == null) {
                warning("No chest found for " + block.getName().getString());
                toggle();
                return;
            }
            int stacks = (int) Math.ceil((float) requiredItems.get(block) / 64f);
            info("Restocking §a" + stacks + " stacks " + block.getName().getString());
            restockList.add(Triple.of(bestRestockPos.getLeft(), bestRestockPos.getRight(), stacks));
        }
    }

    private void calculateBuildingPath() {
        boolean isStartSide = true;
        checkpoints.clear();
        for (int i = 0; i < 128; i+=linesPerRun.get()) {
            boolean allCarpet = true;
            for (int lineBonus = 0; lineBonus < linesPerRun.get(); lineBonus++) {
                for (int j = 0; j < 128; j++) {
                    Block block = mc.world.getBlockState(mapCorner.add(i + lineBonus, 0, j)).getBlock();
                    if (!(block instanceof CarpetBlock)) {
                        allCarpet = false;
                        break;
                    }
                }
            }
            if (allCarpet) continue;
            Vec3d cp1 = mapCorner.toCenterPos().add(i,0,0);
            Vec3d cp2 = mapCorner.toCenterPos().add(i,0,127);
            if (isStartSide) {
                checkpoints.add(cp1);
                checkpoints.add(cp2);
            } else {
                checkpoints.add(cp2);
                checkpoints.add(cp1);
            }
            isStartSide = !isStartSide;
        }
    }

    private boolean analyzeInventory() {
        boolean needsDump = false;
        for (int i = 0; i < 36; i++) {
            Block material = Registries.BLOCK.get(new Identifier(mc.player.getInventory().getStack(i).getItem().toString()));
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                availableSlots.add(i);
            }
            if (materialDict.containsKey(material)) {
                availableSlots.add(i);
                needsDump = true;
            }
        }
        for (int slot : availableSlots) {
            if (slot < 9) {
                availableHotBarSlots.add(slot);
            }
        }
        info("Inventory slots available for building: " + availableSlots);

        return needsDump;
    }

    private int getIntervalStart(int pos) {
        info("Factor: " + Math.floor((float) (pos + 64) / 128f));
        return (int) Math.floor((float) (pos + 64) / 128f) * 128 - 64;
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (state == null) return;
        if (state.equals("SelectingCorner") && event.packet instanceof PlayerInteractBlockC2SPacket packet) {
            BlockPos hitPos = packet.getBlockHitResult().getBlockPos().up();
            int adjustedX = getIntervalStart(hitPos.getX());
            int adjustedZ = getIntervalStart(hitPos.getZ());
            mapCorner = new BlockPos(adjustedX, hitPos.getY(), adjustedZ);
            state = "SelectingReset";
            info("Map Area selected. Press the §aReset Button §7used to remove the carpets");
            return;
        }

        if (state.equals("SelectingReset") && event.packet instanceof PlayerInteractBlockC2SPacket packet) {
            BlockPos blockPos = packet.getBlockHitResult().getBlockPos();
            if (mc.world.getBlockState(blockPos).getBlock() instanceof ButtonBlock) {
                reset = new Pair<>(packet.getBlockHitResult(), mc.player.getPos());
                info("Reset Button selected. Select the §aCartography Table.");
                state = "SelectingTable";
                return;
            }
        }

        if (state.equals("SelectingTable") && event.packet instanceof PlayerInteractBlockC2SPacket packet) {
            BlockPos blockPos = packet.getBlockHitResult().getBlockPos();
            if (mc.world.getBlockState(blockPos).getBlock().equals(Blocks.CARTOGRAPHY_TABLE)) {
                cartographyTable = new Pair<>(packet.getBlockHitResult(), mc.player.getPos());
                info("Cartography Table selected. Select the §aFinished Map Chest.");
                state = "SelectingFinishedMapChest";
                return;
            }
        }

        if (state.equals("SelectingFinishedMapChest") && event.packet instanceof PlayerInteractBlockC2SPacket packet) {
            BlockPos blockPos = packet.getBlockHitResult().getBlockPos();
            if (mc.world.getBlockState(blockPos).getBlock() instanceof AbstractChestBlock) {
                finishedMapChest = new Pair<>(packet.getBlockHitResult(), mc.player.getPos());
                info("Finished Map Chest selected. Select all §aMaterial-, Map- and Dump-Chests.");
                state = "SelectingChests";
                return;
            }
        }

        if (state.equals("SelectingChests") && event.packet instanceof PlayerInteractBlockC2SPacket packet) {
            BlockPos blockPos = packet.getBlockHitResult().getBlockPos();
            if (blockPos.up().equals(mapCorner)) {
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
                setWPressed(true);
                calculateBuildingPath();
                if (analyzeInventory()) {
                    Pair<BlockPos, Vec3d> bestChest = getBestChest(null);
                    checkpoints.add(0, bestChest.getRight());
                    currentDumpChest = bestChest.getLeft();
                } else {
                    refillInventory();
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
                state = "Walking";
            }
            if (mc.world.getBlockState(blockPos).getBlock().equals(Blocks.CHEST)) {
                //info("Chest selected: " + pos);
                tempChestPos = blockPos;
                state = "AwaitContent";
                return;
            }
        }
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (state == null) return;
        if (state.equals("AwaitContent") && event.packet instanceof InventoryS2CPacket packet) {
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
                        state = "SelectingChests";
                        return;
                    }
                }
            }
            if (foundItem == null) {
                info("Registered §aDumpChest");
                dumpChests = Utils.saveAdd(dumpChests, tempChestPos, mc.player.getPos());
                state = "SelectingChests";
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
            state = "SelectingChests";
        }

        List<String> allowedStates = Arrays.asList("AwaitRestockResponse", "AwaitDumpResponse", "AwaitMapChestResponse", "AwaitCartographyResponse", "AwaitFinishedMapChestResponse");
        if (allowedStates.contains(state) && event.packet instanceof InventoryS2CPacket packet) {
            if (preRestockDelay.get() == 0) {
                handleInventoryPacket(packet);
            } else {
                toBeHandledInvPacket = packet;
                timeoutTicks = preRestockDelay.get();
            }
        }
    }



    private void handleInventoryPacket(InventoryS2CPacket packet) {
        closeNextInvPacket = true;
        //info("Handling Inventory Packet");
        if (state.equals("AwaitRestockResponse")) {
            boolean foundMaterials = false;
            for (int i = 0; i < packet.getContents().size()-36; i++) {
                ItemStack stack = packet.getContents().get(i);

                if (restockList.get(0).getRight() == 0) break;
                if (!stack.isEmpty() && stack.getCount() == 64) {
                    //info("Taking Stack of " + restockList.get(0).getLeft().getName().getString());
                    foundMaterials = true;
                    int highestFreeSlot = Utils.findHighestFreeSlot(packet);
                    if (highestFreeSlot == -1) {
                        info("No free slots found in inventory.");
                        endRestocking();
                        return;
                    }
                    invActionPackets.add(new ClickSlotC2SPacket(packet.getSyncId(), 1, i, 1, SlotActionType.QUICK_MOVE, new ItemStack(Items.AIR), Int2ObjectMaps.emptyMap()));
                    Triple<BlockPos, Vec3d, Integer> oldTriple = restockList.remove(0);
                    restockList.add(0, Triple.of(oldTriple.getLeft(), oldTriple.getMiddle(), oldTriple.getRight() - 1));
                }
            }
            if (!foundMaterials) {
                warning("No materials found in chest. Please restock the chest.");
                toggle();
                return;
            }
            if (invActionDelay.get() == 0) {
                for (ClickSlotC2SPacket p: invActionPackets) {
                    mc.getNetworkHandler().sendPacket(p);
                }
                invActionPackets.clear();
                endRestocking();
            } else {
                timeoutTicks = invActionDelay.get();
            }
            return;
        }

        if (state.equals("AwaitDumpResponse")) {
            for (int slot : availableSlots) {
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
                if (checkpointAction != "mapMaterialChest") refillInventory();  //Only refill if not finished building the map
                state = "Walking";
                timeoutTicks = postRestockDelay.get();
                return;
            }

            if (invActionDelay.get() == 0) {
                for (ClickSlotC2SPacket p: invActionPackets) {
                    mc.getNetworkHandler().sendPacket(p);
                }
                invActionPackets.clear();
                if (checkpointAction != "mapMaterialChest") refillInventory();  //Only refill if not finished building the map
                state = "Walking";
                timeoutTicks = postRestockDelay.get();
            }
            return;
        }

        timeoutTicks = postRestockDelay.get();

        if (state.equals("AwaitMapChestResponse")) {
            //get map
            for (int slot = 0; slot < packet.getContents().size()-36; slot++) {
                ItemStack stack = packet.getContents().get(slot);
                if (stack.getItem() == Items.MAP) {
                    getOneItem(slot, false, packet);
                    break;
                }
            }
            for (int slot = 0; slot < packet.getContents().size()-36; slot++) {
                ItemStack stack = packet.getContents().get(slot);
                if (stack.getItem() == Items.GLASS_PANE) {
                    getOneItem(slot, true, packet);
                    break;
                }
            }
            mc.player.getInventory().selectedSlot = availableSlots.get(0);
            checkpointAction = "fillMap";
            Vec3d center = mapCorner.add(map.length/2 - 1, 0, map[0].length/2 - 1).toCenterPos();
            checkpoints.add(center);
            state = "Walking";
            return;
        }

        if (state.equals("AwaitCartographyResponse")) {
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
                    continue;
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
            checkpoints.add(finishedMapChest.getRight());
            checkpointAction = "finishedMapChest";
            state = "Walking";
            return;
        }

        if (state.equals("AwaitFinishedMapChestResponse")) {
            for (int slot = packet.getContents().size()-36; slot < packet.getContents().size(); slot++) {
                ItemStack stack = packet.getContents().get(slot);
                if (stack.getItem() == Items.FILLED_MAP) {
                    mc.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(packet.getSyncId(), 1, slot, 0, SlotActionType.QUICK_MOVE, new ItemStack(Items.AIR), Int2ObjectMaps.emptyMap()));
                    break;
                }
            }
            checkpoints.add(reset.getRight());
            checkpointAction = "reset";
            state = "Walking";
            return;
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

        if (timeoutTicks > 0) {
            timeoutTicks--;
            return;
        }

        if (invActionPackets.size() > 0) {
            mc.getNetworkHandler().sendPacket(invActionPackets.get(0));
            invActionPackets.remove(0);
            if (invActionPackets.size() == 0) {
                if (state.equals("AwaitRestockResponse")) {
                    endRestocking();
                }
                if (state.equals("AwaitDumpResponse")) {
                    if (checkpointAction != "mapMaterialChest") refillInventory();  //Only refill if not finished building the map
                    state = "Walking";
                    timeoutTicks = postRestockDelay.get();
                }
            } else {
                timeoutTicks = invActionDelay.get();
            }
            return;
        }

        if (newMap) {
            newMap = false;
            calculateBuildingPath();
            Pair<BlockPos, Vec3d> bestChest = getBestChest(null);
            currentDumpChest = bestChest.getLeft();
            checkpoints.add(0, bestChest.getRight());
            setWPressed(true);
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

        if (state.equals("Walking")) {
            setWPressed(true);
            Vec3d goal = checkpoints.get(0);
            if (restockList.size() > 0) {
                goal = restockList.get(0).getMiddle();
            }
            if (PlayerUtils.distanceTo(goal.add(0,mc.player.getY()-goal.y,0)) < checkpointBuffer.get()) {
                mc.player.setPosition(goal.getX(), mc.player.getY(), goal.getZ());
                mc.player.setVelocity(0,0,0);
                if (currentDumpChest != null) {
                    checkpoints.remove(0);
                    mc.player.setYaw((float) Rotations.getYaw(currentDumpChest.toCenterPos()));
                    mc.player.setPitch((float) Rotations.getPitch(currentDumpChest.toCenterPos()));
                    BlockHitResult hitResult = new BlockHitResult(currentDumpChest.toCenterPos(), Direction.UP, currentDumpChest, false);
                    BlockUtils.interact(hitResult, Hand.MAIN_HAND, true);
                    currentDumpChest = null;
                    state = "AwaitDumpResponse";
                    setWPressed(false);
                    return;
                }
                switch (checkpointAction) {
                    case "mapMaterialChest":
                        checkpoints.remove(0);
                        BlockPos mapMaterialChest = getBestChest(Blocks.CARTOGRAPHY_TABLE).getLeft();
                        mc.player.setYaw((float) Rotations.getYaw(mapMaterialChest.toCenterPos()));
                        mc.player.setPitch((float) Rotations.getPitch(mapMaterialChest.toCenterPos()));
                        BlockHitResult hitResult = new BlockHitResult(mapMaterialChest.toCenterPos(), Direction.UP, mapMaterialChest, false);
                        BlockUtils.interact(hitResult, Hand.MAIN_HAND, true);
                        checkpointAction = "";
                        state = "AwaitMapChestResponse";
                        setWPressed(false);
                        return;
                    case "fillMap":
                        Vec3d center = checkpoints.remove(0);
                        mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, Utils.getNextInteractID()));
                        if (mapFillSquareSize.get() == 0) {
                            checkpointAction = "cartographyTable";
                            checkpoints.add(0, cartographyTable.getRight());
                        } else {
                            checkpointAction = "checkSquareComplete";
                            checkpoints.add(center.add(-mapFillSquareSize.get(), 0, mapFillSquareSize.get()));
                            checkpoints.add(center.add(mapFillSquareSize.get(), 0, mapFillSquareSize.get()));
                            checkpoints.add(center.add(mapFillSquareSize.get(), 0, -mapFillSquareSize.get()));
                            checkpoints.add(center.add(-mapFillSquareSize.get(), 0, -mapFillSquareSize.get()));
                        }
                        return;
                    case "cartographyTable":
                        checkpoints.remove(0);
                        mc.player.setYaw((float) Rotations.getYaw(cartographyTable.getLeft().getBlockPos().toCenterPos()));
                        mc.player.setPitch((float) Rotations.getPitch(cartographyTable.getLeft().getBlockPos().toCenterPos()));
                        BlockUtils.interact(cartographyTable.getLeft(), Hand.MAIN_HAND, true);
                        checkpointAction = "";
                        state = "AwaitCartographyResponse";
                        setWPressed(false);
                        return;
                    case "finishedMapChest":
                        checkpoints.remove(0);
                        mc.player.setYaw((float) Rotations.getYaw(finishedMapChest.getLeft().getBlockPos().toCenterPos()));
                        mc.player.setPitch((float) Rotations.getPitch(finishedMapChest.getLeft().getBlockPos().toCenterPos()));
                        BlockUtils.interact(finishedMapChest.getLeft(), Hand.MAIN_HAND, true);
                        checkpointAction = "";
                        state = "AwaitFinishedMapChestResponse";
                        setWPressed(false);
                        return;
                    case "reset":
                        info("Resetting..");
                        setWPressed(false);
                        checkpoints.remove(0);
                        mc.player.setYaw((float) Rotations.getYaw(reset.getLeft().getBlockPos().toCenterPos()));
                        mc.player.setPitch((float) Rotations.getPitch(reset.getLeft().getBlockPos().toCenterPos()));
                        BlockUtils.interact(reset.getLeft(), Hand.MAIN_HAND, true);
                        checkpointAction = "";
                        setWPressed(false);
                        timeoutTicks = resetDelay.get();
                        newMap = true;
                        File mapFile = getNextMapFile();
                        if (mapFile == null) {
                            info("All nbt files finished");
                            toggle();
                            return;
                        }
                        if (!loadNBTFiles(mapFile)) {
                            warning("Failed to read schematic file.");
                            toggle();
                            return;
                        }
                        return;
                    case "checkSquareComplete":
                        if (checkpoints.size() == 1) {
                            checkpointAction = "cartographyTable";
                            checkpoints.add(cartographyTable.getRight());
                        }
                }
                if (restockList.size() > 0) {
                    //Taking Items from Chest
                    BlockPos chestPos = restockList.get(0).getLeft();
                    mc.player.setYaw((float) Rotations.getYaw(chestPos.toCenterPos()));
                    mc.player.setPitch((float) Rotations.getPitch(chestPos.toCenterPos()));
                    BlockHitResult hitResult = new BlockHitResult(chestPos.toCenterPos(), Direction.UP, chestPos, false);
                    BlockUtils.interact(hitResult, Hand.MAIN_HAND, true);
                    state = "AwaitRestockResponse";
                    setWPressed(false);
                    return;
                } else {
                    checkpoints.remove(0);
                    if (checkpoints.size() != 0) {
                        goal = checkpoints.get(0);
                    } else {
                        info("Finished building map");
                        Pair<BlockPos, Vec3d> bestChest = getBestChest(Blocks.CARTOGRAPHY_TABLE);
                        checkpointAction = "mapMaterialChest";
                        checkpoints.add(0, bestChest.getRight());

                        bestChest = getBestChest(null);
                        currentDumpChest = bestChest.getLeft();
                        checkpoints.add(0, bestChest.getRight());
                    }
                }
            }
            mc.player.setYaw((float) Rotations.getYaw(goal));

            if (restockList.size() > 0) return;
            if (currentDumpChest != null) return;
            if (mc.player.isSprinting()) mc.player.setSprinting(false);
            if (mc.currentScreen != null && hasRestocked) {
                hasRestocked = false;
                mc.player.closeHandledScreen();
            }
            if (placeDelayticks > 0) {
                placeDelayticks--;
                return;
            }

            //BlockPos closestPos = findFreeBlockPos((int) Math.ceil(placeRange.get()) + 1,0, goal);

            AtomicReference<BlockPos> closestPos = new AtomicReference<>();
            final Vec3d currentGoal = goal;
            Utils.iterateBlocks(mc.player.getBlockPos(), (int) Math.ceil(placeRange.get()) + 1, 0,((blockPos, blockState) -> {
                Double posDistance = PlayerUtils.distanceTo(blockPos);
                if ((!(blockState.getBlock() instanceof CarpetBlock)) && posDistance <= placeRange.get() && posDistance > 0.8 && blockPos.getX() <= currentGoal.getX() + linesPerRun.get()-1 && isWithingMap(blockPos)) {
                    if (closestPos.get() == null || posDistance < PlayerUtils.distanceTo(closestPos.get())) {
                        closestPos.set(new BlockPos(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
                    }
                }
            }));

            if (closestPos.get() != null) {
                placeDelayticks = placeDelay.get();
                //info("Closest pos: " + closestPos.get().toShortString());
                tryPlacingBlock(closestPos.get());
            }
        }
    }

    private void tryPlacingBlock(BlockPos pos) {
        BlockPos relativePos = pos.subtract(mapCorner);
        Block material = map[relativePos.getX()][relativePos.getZ()];
        //info("Placing " + material.getName().getString() + " at: " + relativePos.toShortString());
        //Check hot-bar slots
        for (int slot : availableHotBarSlots) {
            if (mc.player.getInventory().getStack(slot).isEmpty()) continue;
            Block foundMaterial = Registries.BLOCK.get(new Identifier(mc.player.getInventory().getStack(slot).getItem().toString()));
            if (foundMaterial.equals(material)) {
                BlockUtils.place(pos, Hand.MAIN_HAND, slot, true,50, true, true, false);
                return;
            }
        }
        for (int slot : availableSlots) {
            if (mc.player.getInventory().getStack(slot).isEmpty() || availableHotBarSlots.contains(slot)) continue;
            Block foundMaterial = Registries.BLOCK.get(new Identifier(mc.player.getInventory().getStack(slot).getItem().toString()));
            if (foundMaterial.equals(material)) {
                Utils.swapIntoHotbar(slot, availableHotBarSlots);
                //BlockUtils.place(pos, Hand.MAIN_HAND, resultSlot, true,50, true, true, false);
                setWPressed(false);
                mc.player.setVelocity(0,0,0);
                timeoutTicks = swapDelay.get();
                return;
            }
        }
        info("No "+ material.getName().getString() + " found in inventory. Resetting...");
        Pair<BlockPos, Vec3d> bestChest = getBestChest(null);
        checkpoints.add(0, mc.player.getPos());
        checkpoints.add(0, bestChest.getRight());
        currentDumpChest = bestChest.getLeft();
    }

    private void endRestocking() {
        restockList.remove(0);
        timeoutTicks = postRestockDelay.get();
        hasRestocked = true;
        state = "Walking";
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
        }
        for (Pair<BlockPos, Vec3d> p : list) {
            if (bestPos == null || PlayerUtils.distanceTo(p.getRight()) < PlayerUtils.distanceTo(bestPos)) {
                bestPos = p.getRight();
                bestChestPos = p.getLeft();
            }
        }
        return new Pair(bestChestPos, bestPos);
    }

    private boolean isWithingMap(BlockPos pos) {
        return pos.getX() >= mapCorner.getX() && pos.getX() < mapCorner.getX() + 128 && pos.getZ() >= mapCorner.getZ() && pos.getZ() < mapCorner.getZ() + 128;
    }

    private File getNextMapFile() {
        for (File file : mapFolder.listFiles()) {
            if (!startedFiles.contains(file)) {
                startedFiles.add(file);
                return file;
            }
        }
        return null;
    }

    private boolean loadNBTFiles(File mapFile) {
        info("Building: §a" + mapFile.getName());
        try {
            NbtSizeTracker sizeTracker = new NbtSizeTracker(0x20000000L, 100);
            NbtCompound nbt = NbtIo.readCompressed(mapFile.toPath(), sizeTracker);
            //Extracting the palette
            NbtList paletteList  = (NbtList) nbt.get("palette");
            carpetDict = new HashMap<>();
            for (int i = 0; i < paletteList.size(); i++) {
                NbtCompound block = paletteList.getCompound(i);
                String blockName = block.getString("Name");
                Block material = Registries.BLOCK.get(new Identifier(blockName));
                if (material instanceof CarpetBlock) {
                    carpetDict.put(i, new Pair(Registries.BLOCK.get(new Identifier(blockName)), 0));
                }
            }

            //Counting required carpets and calculating the map offset
            NbtList blockList  = (NbtList) nbt.get("blocks");
            int maxHeight = Integer.MIN_VALUE;
            int minX = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            for (int i = 0; i < blockList.size(); i++) {
                NbtCompound block = blockList.getCompound(i);
                int blockId = block.getInt("state");
                if (!carpetDict.containsKey(blockId)) continue;
                carpetDict.put(blockId, new Pair(carpetDict.get(blockId).getLeft(), carpetDict.get(blockId).getRight() + 1));
                NbtList pos = block.getList("pos", 3);
                if (pos.getInt(1) > maxHeight) maxHeight = pos.getInt(1);
                if (pos.getInt(0) < minX) minX = pos.getInt(0);
                if (pos.getInt(2) < minZ) minZ = pos.getInt(2);
            }
            info("Requirements: ");
            for (Pair<Block, Integer> p: carpetDict.values()) {
                info(p.getLeft().getName().getString() + ": " + p.getRight());
            }

            //Extracting the carpet positions
            map = new Block[128][128];
            for (int i = 0; i < blockList.size(); i++) {
                NbtCompound block = blockList.getCompound(i);
                if (!carpetDict.containsKey(block.getInt("state"))) continue;
                NbtList pos = block.getList("pos", 3);
                int x = pos.getInt(0) - minX;
                int y = pos.getInt(1);
                int z = pos.getInt(2) - minZ;
                if (y == maxHeight && x < map.length && z < map.length & x >= 0 && z >= 0) {
                    map[x][z] = carpetDict.get(block.getInt("state")).getLeft();
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
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void setWPressed(boolean pressed) {
        mc.options.forwardKey.setPressed(pressed);
        Input.setKeyState(mc.options.forwardKey, pressed);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if(mapCorner == null || !render.get()) return;
        event.renderer.box(mapCorner, color.get(), color.get(), ShapeMode.Lines, 0);
        event.renderer.box(mapCorner.getX(), mapCorner.getY(), mapCorner.getZ(), mapCorner.getX()+128, mapCorner.getY(), mapCorner.getZ()+128, color.get(), color.get(), ShapeMode.Lines, 0);
        if (renderChestPositions.get()) {
            for (ArrayList<Pair<BlockPos, Vec3d>> list: materialDict.values()) {
                for (Pair<BlockPos, Vec3d> p : list) {
                    event.renderer.box(p.getLeft(), color.get(), color.get(), ShapeMode.Lines, 0);
                }
            }
            for (Pair<BlockPos, Vec3d> chest: mapMaterialChests) {
                event.renderer.box(chest.getLeft(), color.get(), color.get(), ShapeMode.Lines, 0);
            }
            for (Pair<BlockPos, Vec3d> chest: dumpChests) {
                event.renderer.box(chest.getLeft(), color.get(), color.get(), ShapeMode.Lines, 0);
            }
        }
        if (renderOpenPositions.get()) {
            for (ArrayList<Pair<BlockPos, Vec3d>> list: materialDict.values()) {
                for (Pair<BlockPos, Vec3d> p : list) {
                    event.renderer.box(p.getRight().x-indicatorSize.get(), p.getRight().y-indicatorSize.get(), p.getRight().z-indicatorSize.get(), p.getRight().getX()+indicatorSize.get(), p.getRight().getY()+indicatorSize.get(), p.getRight().getZ()+indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
                }
            }
            for (Pair<BlockPos, Vec3d> chest: mapMaterialChests) {
                event.renderer.box(chest.getRight().x-indicatorSize.get(), chest.getRight().y-indicatorSize.get(), chest.getRight().z-indicatorSize.get(), chest.getRight().getX()+indicatorSize.get(), chest.getRight().getY()+indicatorSize.get(), chest.getRight().getZ()+indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
            for (Pair<BlockPos, Vec3d> chest: dumpChests) {
                event.renderer.box(chest.getRight().x-indicatorSize.get(), chest.getRight().y-indicatorSize.get(), chest.getRight().z-indicatorSize.get(), chest.getRight().getX()+indicatorSize.get(), chest.getRight().getY()+indicatorSize.get(), chest.getRight().getZ()+indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
        }
        if (renderCheckpoints.get()) {
            for (Vec3d cp: checkpoints) {
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
}
