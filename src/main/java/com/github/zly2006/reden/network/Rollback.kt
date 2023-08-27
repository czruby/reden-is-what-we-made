package com.github.zly2006.reden.network

import com.github.zly2006.reden.access.PlayerData
import com.github.zly2006.reden.access.PlayerData.Companion.data
import com.github.zly2006.reden.mixinhelper.UpdateMonitorHelper
import com.github.zly2006.reden.utils.debugLogger
import com.github.zly2006.reden.utils.isClient
import com.github.zly2006.reden.utils.setBlockNoPP
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.FabricPacket
import net.fabricmc.fabric.api.networking.v1.PacketType
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.block.Block
import net.minecraft.entity.SpawnReason
import net.minecraft.nbt.NbtHelper
import net.minecraft.network.PacketByteBuf
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos

private val pType = PacketType.create(ROLLBACK) {
    Rollback(it.readVarInt())
}
class Rollback(
    val status: Int = 0
): FabricPacket {
    override fun getType(): PacketType<*> = pType
    override fun write(buf: PacketByteBuf) {
        buf.writeVarInt(status)
    }

    companion object {
        fun register() {
            ServerPlayNetworking.registerGlobalReceiver(pType) { packet, player, res ->
                val view = player.data()
                synchronized(UpdateMonitorHelper) {
                    fun sendStatus(status: Int) {
                        res.sendPacket(Rollback(status))
                    }
                    if (!view.canRecord) {
                        sendStatus(16)
                        return@registerGlobalReceiver
                    }
                    UpdateMonitorHelper.playerStopRecording(player)
                    fun operate(record: PlayerData.UndoRedoRecord, redoRecord: PlayerData.RedoRecord?) {
                        record.data.forEach { (pos, entry) ->
                            val state = NbtHelper.toBlockState(Registries.BLOCK.readOnlyWrapper, entry.blockState)
                            debugLogger("undo ${BlockPos.fromLong(pos)}, $state")
                            player.world.setBlockNoPP(
                                BlockPos.fromLong(pos),
                                state,
                                Block.NOTIFY_LISTENERS
                            )
                            entry.blockEntity?.let { be ->
                                player.world.getBlockEntity(BlockPos.fromLong(pos))?.readNbt(be)
                            }
                        }
                        record.entities.forEach {
                            if (it.value != null) {
                                val entry = it.value!!
                                val entity = player.serverWorld.getEntity(it.key)
                                if (entity != null) {
                                    entity.readNbt(entry.nbt)
                                } else {
                                    entry.entity.spawn(player.serverWorld, entry.nbt, null, entry.pos, SpawnReason.COMMAND, false, false)
                                    redoRecord?.entities?.put(it.key, null) // add entity info to redo record
                                }
                            }
                            else {
                                player.serverWorld.getEntity(it.key)?.discard()
                            }
                        }
                    }
                    fun <T: PlayerData.UndoRedoRecord> MutableList<T>.lastValid(): T? {
                        while (this.isNotEmpty()) {
                            val last = this.last()
                            if (last.data.isNotEmpty() || last.entities.isNotEmpty()) {
                                return last
                            }
                            UpdateMonitorHelper.removeRecord(last.id)
                            this.removeLast()
                        }
                        return null
                    }
                    when (packet.status) {
                        0 -> view.undo.lastValid()?.let { undoRecord ->
                            view.undo.removeLast()
                            UpdateMonitorHelper.removeRecord(undoRecord.id) // no longer monitoring rollbacked record
                            view.redo.add(
                                PlayerData.RedoRecord(
                                    id = undoRecord.id,
                                    lastChangedTick = -1,
                                    undoRecord = undoRecord
                                ).apply {
                                    data.putAll(undoRecord.data.keys.associateWith { posLong ->
                                        this.fromWorld( // add entity info to this redo record
                                            player.world,
                                            BlockPos.fromLong(posLong)
                                        )
                                    })
                                }
                            )
                            operate(undoRecord, view.redo.last())
                            sendStatus(0)
                        } ?: sendStatus(2)

                        1 -> view.redo.lastValid()?.let {
                            view.redo.removeLast()
                            operate(it, null)
                            view.undo.add(it.undoRecord)
                            sendStatus(1)
                        } ?: sendStatus(2)

                        else -> sendStatus(65536)
                    }
                }
            }
            if (isClient) {
                ClientPlayNetworking.registerGlobalReceiver(pType) { packet, player, res ->
                    player.sendMessage(
                        when (packet.status) {
                            0 -> Text.literal("[Reden/Undo] Rollback success")
                            1 -> Text.literal("[Reden/Undo] Restore success")
                            2 -> Text.literal("[Reden/Undo] No blocks info")
                            16 -> Text.literal("[Reden/Undo] No permission")
                            32 -> Text.literal("[Reden/Undo] Not recording")
                            65536 -> Text.literal("[Reden/Undo] Unknown error")
                            else -> Text.literal("[Reden/Undo] Unknown status")
                        }
                    )
                }
            }
        }
    }
}
