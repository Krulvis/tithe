package org.powbot.opensource.tithe.tree.leaf

import org.powbot.api.Condition
import org.powbot.api.rt4.Inventory
import org.powbot.api.script.tree.Leaf
import org.powbot.opensource.tithe.TitheFarmer

class Plant(script: TitheFarmer) : Leaf<TitheFarmer>(script, "Planting") {

    override fun execute() {
        val hasEnoughWater = script.hasEnoughWater()
        val hasSeeds = script.hasSeeds()
        val patch = script.patches.firstOrNull { it.isEmpty() }
        if (!hasEnoughWater || !hasSeeds || patch == null) {
            script.planting = false
        } else {
            script.planting = true
            val seed = script.getSeed()
            if (patch.walkBetween(script.patches) && patch.plant(seed)) {
                val doneDidIt = Condition.wait({
                    !patch.isEmpty(true)
                }, 250, 20)
                script.log.info("Planted on $patch: $doneDidIt")
                if (doneDidIt) {
                    val tick = script.lastTick
                    val start = System.currentTimeMillis()

                    if (Condition.wait({ script.lastTick > tick || System.currentTimeMillis() > start + 600 }, 250, 20)
                        && patch.water()
                    ) {
                        if (patch.index < script.patchCount - 1) {
                            Inventory.stream().id(seed).findFirst().ifPresent { it.interact("Use") }
                        }
                        val watered = Condition.wait({ !patch.needsAction(true) }, 200, 20)
                        script.log.info("Watered as well.. $patch: $watered")
                    }
                }
            }
        }
    }

}