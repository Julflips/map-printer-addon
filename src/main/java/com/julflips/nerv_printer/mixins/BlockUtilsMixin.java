package com.julflips.nerv_printer.mixins;

import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CartographyTableBlock;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static meteordevelopment.meteorclient.utils.world.BlockUtils.isClickable;

@Mixin(value = BlockUtils.class, remap = false)
public class BlockUtilsMixin {

    @Inject(method = "isClickable", at = @At("HEAD"), cancellable = true)
    private static void injectedIsClickavle(Block block, CallbackInfoReturnable<Boolean> cir) {
        if (block instanceof CartographyTableBlock) {
            cir.setReturnValue(true);
        }
    }

    //Fixing meteors garbo code
    @Inject(method = "getPlaceSide", at = @At("HEAD"), cancellable = true)
    private static void injectedGetPlaceSide(BlockPos blockPos, CallbackInfoReturnable<Direction> cir ) {
        ArrayList<Direction> placeableDirections = new ArrayList<>();
        for (Direction side : Direction.values()) {
            BlockPos neighbor = blockPos.offset(side);
            BlockState state = mc.world.getBlockState(neighbor);

            // Check if neighbour isn't empty
            if (state.isAir() || isClickable(state.getBlock())) continue;

            // Check if neighbour is a fluid
            if (!state.getFluidState().isEmpty()) continue;
            placeableDirections.add(side);
        }

        //Get the direction the player is looking at
        Vec3d lookVec = blockPos.toCenterPos().subtract(mc.player.getEyePos());
        //List of direction and their significance (a larger score means the player is looking more in that direction)
        List<Pair<Direction, Double>> directionSignificance = Arrays.asList(
                new Pair<>(Direction.WEST, -lookVec.getX()),
                new Pair<>(Direction.EAST, lookVec.getX()),
                new Pair<>(Direction.DOWN, -lookVec.getY()),
                new Pair<>(Direction.UP, lookVec.getY()),
                new Pair<>(Direction.NORTH, -lookVec.getZ()),
                new Pair<>(Direction.SOUTH, lookVec.getZ())
        );

        // Sort the list descending based on the significance of the direction
        Collections.sort(directionSignificance, (pair1, pair2) -> Double.compare(pair2.getRight(), pair1.getRight()));

        //Return the direction the player is looking at the most and has a placeable neighbour
        for (Pair<Direction, Double> pair : directionSignificance) {
            if (placeableDirections.contains(pair.getLeft())) {
                cir.setReturnValue(pair.getLeft());
                return;
            }
        }

        cir.setReturnValue(null);
    }
}
