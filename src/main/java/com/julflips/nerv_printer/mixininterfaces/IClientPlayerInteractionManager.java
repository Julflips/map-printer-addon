package com.julflips.nerv_printer.mixininterfaces;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;

public interface IClientPlayerInteractionManager {
    void setBlockBreakingCooldown(int cooldown);

    float getCurrentBreakingProgress();
    void clickSlot(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player);
}
