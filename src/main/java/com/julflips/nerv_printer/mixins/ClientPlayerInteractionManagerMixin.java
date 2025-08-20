package com.julflips.nerv_printer.mixins;

import com.julflips.nerv_printer.mixininterfaces.IClientPlayerInteractionManager;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = ClientPlayerInteractionManager.class, priority = 1002)
public abstract class ClientPlayerInteractionManagerMixin implements IClientPlayerInteractionManager {
    @Shadow
    public abstract void clickSlot(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player);

}
