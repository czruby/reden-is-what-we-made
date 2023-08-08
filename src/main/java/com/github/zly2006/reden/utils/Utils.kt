package com.github.zly2006.reden.utils

import net.fabricmc.api.EnvType
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

lateinit var server: MinecraftServer

fun Vec3d.toBlockPos(): BlockPos {
    return BlockPos.ofFloored(this)
}

fun PlayerEntity.sendMessage(s: String) {
    sendMessage(Text.literal(s))
}

fun <E> MutableList<E>.removeAtOrNull(index: Int): E? {
    val i = if (index < 0) size + index else index
    return if (i in indices) removeAt(i) else null
}

fun World.setBlockNoPP(pos: BlockPos, state: BlockState, flags: Int) {
    if (isClient) {

    }
    getChunk(pos).run {
        getSection(getSectionIndex(pos.y))
    }.setBlockState(pos.x and 15, pos.y and 15, pos.z and 15, state, false)
    if (this is ServerWorld) {
        chunkManager.markForUpdate(pos)
    }
    if (flags and Block.NOTIFY_LISTENERS != 0) {
        updateListeners(pos, getBlockState(pos), state, flags)
    }
}

val isClient: Boolean get() = FabricLoader.getInstance().environmentType == EnvType.CLIENT

object ResourceLoader {
    fun loadBytes(path: String): ByteArray {
        val stream = this::class.java.getResourceAsStream(path)
        if (stream != null) {
            return stream.readAllBytes()
        }
        else {
            throw RuntimeException("The specified resource $path was not found!")
        }
    }

    fun loadString(path: String): String {
        return loadBytes(path).decodeToString()
    }

    fun loadTexture(path: String): Identifier {
        return Identifier("reden", path)
    }
}

fun buttonWidget(x: Int, y: Int, width: Int, height: Int, message: Text, onPress: ButtonWidget.PressAction) =
    ButtonWidget(x, y, width, height, message, onPress) { it.get() }