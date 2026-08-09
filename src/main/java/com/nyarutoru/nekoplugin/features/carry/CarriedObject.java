package com.nyarutoru.nekoplugin.features.carry;

import org.bukkit.block.BlockState;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;

sealed interface CarriedObject permits CarriedObject.Block, CarriedObject.Mob {
    Entity passenger();

    record Block(BlockState state, BlockDisplay passenger) implements CarriedObject {
    }

    record Mob(Entity passenger) implements CarriedObject {
    }
}
