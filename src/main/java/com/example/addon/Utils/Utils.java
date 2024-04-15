package com.example.addon.Utils;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import com.example.addon.mixininterfaces.IClientPlayerInteractionManager;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;
import java.util.function.BiConsumer;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class Utils {

    private static int nextInteractID = 2;



    @EventHandler
    public void onGameLeft(GameLeftEvent event) {
        nextInteractID = 2;
    }

    @EventHandler(priority = EventPriority.HIGHEST - 1)
    private void onReceivePacket(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerInteractItemC2SPacket packet) {
            nextInteractID = packet.getSequence() + 1;
        }

        if (event.packet instanceof PlayerInteractBlockC2SPacket packet) {
            nextInteractID = packet.getSequence() + 1;
        }
    }

    public static int getNextInteractID() {return nextInteractID;}

    public static ArrayList<Pair<BlockPos, Vec3d>> saveAdd(ArrayList<Pair<BlockPos, Vec3d>> list, BlockPos blockPos, Vec3d openPos) {
        for (Pair<BlockPos, Vec3d> pair : list) {
            if (pair.getLeft().equals(blockPos)) {
                list.remove(pair);
                break;
            }
        }
        list.add(new Pair(blockPos, openPos));
        return list;
    }

    public static String getMinecraftDirectory() {
        String os = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");

        // Check operating system to determine Minecraft directory location
        if (os.contains("win")) {
            return userHome + "\\AppData\\Roaming\\.minecraft";
        } else if (os.contains("mac")) {
            return userHome + "/Library/Application Support/minecraft";
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            return userHome + "/.minecraft";
        } else {
            // Unsupported OS, handle accordingly
            throw new IllegalStateException("Unsupported operating system: " + os);
        }
    }

    public static int findHighestFreeSlot(InventoryS2CPacket packet) {
        for (int i = packet.getContents().size()-1; i > packet.getContents().size()-1-36; i--) {
            ItemStack stack = packet.getContents().get(i);
            if (stack.isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public static int swapIntoHotbar(int slot , ArrayList<Integer> hotBarSlots) {
        HashMap<Item, Integer> itemFrenquency = new HashMap<>();
        HashMap<Item, Integer> itemSlot = new HashMap<>();
        int targetSlot = hotBarSlots.get(0);

        //Search the most frequent item in the hotbar
        for (int i : hotBarSlots) {
            if (!mc.player.getInventory().getStack(i).isEmpty()) {
                Item item = mc.player.getInventory().getStack(i).getItem();
                if (!itemFrenquency.keySet().contains(item)) {
                    itemFrenquency.put(item, 1);
                    itemSlot.put(item, i);
                } else {
                    itemFrenquency.put(item, itemFrenquency.get(item) + 1);
                }
            }
        }
        int topFrequency = 0;
        ArrayList<Item> topFrequencyItems = new ArrayList<>();
        for (Item item : itemFrenquency.keySet()) {
            if (itemFrenquency.get(item) > topFrequency) {
                topFrequency = itemFrenquency.get(item);
                topFrequencyItems = new ArrayList<>(Arrays.asList(item));
            } else if (itemFrenquency.get(item) == topFrequency) {
                topFrequencyItems.add(item);
            }
        }
        if (topFrequencyItems.size() > 0) {
            Random random = new Random();
            Item item = topFrequencyItems.get(random.nextInt(topFrequencyItems.size()));
            targetSlot = itemSlot.get(item);
        }

        //Prefer emtpy slots
        for (int i : hotBarSlots) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                targetSlot = i;
            }
        }

        //info("Swapping " + slot + " into " + targetSlot);
        mc.player.getInventory().selectedSlot = targetSlot;

        IClientPlayerInteractionManager cim = (IClientPlayerInteractionManager) mc.interactionManager;
        cim.clickSlot(mc.player.currentScreenHandler.syncId, slot, targetSlot, SlotActionType.SWAP, mc.player);
        //mc.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(0, slot, targetSlot, 0, SlotActionType.SWAP, new ItemStack(Items.AIR), Int2ObjectMaps.emptyMap()));
        return targetSlot;
    }

    public static void iterateBlocks(BlockPos startingPos, int horizontalRadius, int verticalRadius, BiConsumer<BlockPos, BlockState> function) {
        int px = startingPos.getX();
        int py = startingPos.getY();
        int pz = startingPos.getZ();

        BlockPos.Mutable blockPos = new BlockPos.Mutable();

        int hRadius = Math.max(0, horizontalRadius);
        int vRadius = Math.max(0, verticalRadius);

        for (int x = px - hRadius; x <= px + hRadius; x++) {
            for (int z = pz - hRadius; z <= pz + hRadius; z++) {
                for (int y = py - vRadius; y <= py + vRadius; y++) {

                    blockPos.set(x, y, z);
                    BlockState blockState = mc.world.getBlockState(blockPos);

                    int dx = Math.abs(x - px);
                    int dy = Math.abs(y - py);
                    int dz = Math.abs(z - pz);

                    function.accept(blockPos, blockState);
                }
            }
        }

    }
}
