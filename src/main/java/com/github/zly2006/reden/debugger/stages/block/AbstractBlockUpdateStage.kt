package com.github.zly2006.reden.debugger.stages.block

import com.github.zly2006.reden.access.UpdaterData.Companion.updaterData
import com.github.zly2006.reden.debugger.TickStage
import com.github.zly2006.reden.debugger.TickStageWithWorld
import com.github.zly2006.reden.debugger.storage.BlocksResetStorage
import net.minecraft.util.math.BlockPos
import net.minecraft.world.block.ChainRestrictedNeighborUpdater
import net.minecraft.world.block.NeighborUpdater
import net.minecraft.world.block.ChainRestrictedNeighborUpdater as Updater119

abstract class AbstractBlockUpdateStage<T: Updater119.Entry>(
    name: String,
    parent: TickStage
) : TickStage(name, parent) {
    abstract val entry: T
    val resetStorage = BlocksResetStorage()

    fun checkBreakpoints() {
    }

    override fun tick() {
        checkBreakpoints()
    }

    override fun reset() {
        parent as TickStageWithWorld
        resetStorage.apply(parent.world)
    }

    abstract val sourcePos: BlockPos
    abstract val targetPos: BlockPos

    companion object {
        @JvmStatic
        fun <T : ChainRestrictedNeighborUpdater.Entry> createStage(updater: NeighborUpdater, entry: T): AbstractBlockUpdateStage<T> {
            val data = updater.updaterData()
            val parent = data.currentParentTickStage!!
            @Suppress("UNCHECKED_CAST")
            return when (entry) {
                is Updater119.StateReplacementEntry -> StageBlockPPUpdate(parent, entry)
                is Updater119.SixWayEntry -> StageBlockNCUpdateSixWay(parent, entry)
                is Updater119.StatefulEntry -> StageBlockNCUpdateWithSource(parent, entry)
                is Updater119.SimpleEntry -> StageBlockNCUpdate(parent, entry)
                else -> throw IllegalArgumentException("Unknown updater entry type: ${entry.javaClass}")
            } as AbstractBlockUpdateStage<T> // unchecked, but we know it's right
        }
    }
}