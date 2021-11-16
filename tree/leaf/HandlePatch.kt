package org.powbot.opensource.tithe.tree.leaf

import org.powbot.api.Condition
import org.powbot.api.rt4.Component
import org.powbot.api.rt4.Game
import org.powbot.api.rt4.Widgets
import org.powbot.api.script.tree.Leaf
import org.powbot.opensource.tithe.Patch
import org.powbot.opensource.tithe.TitheFarmer

class HandlePatch(script: TitheFarmer) : Leaf<TitheFarmer>(script, "Handling patch") {

    fun closeOpenHUD(): Boolean {
        val tab = Game.tab()
        if (tab == Game.Tab.NONE) {
            return true
        }
        val c: Component = Widgets.widget(601).firstOrNull { (it?.textureId() ?: -1) in tab.textures } ?: return true
        return c.click()
    }

    override fun execute() {
//        if (!waitFor(long()) { !ctx.movement.moving() }) {
//            debug("Not interacting yet because still moving")
//            return
//        }

        script.getPatchTiles()
        closeOpenHUD()
        val hasEnoughWater = script.getWaterCount() > 0
        val patches = script.patches.sortedWith(compareBy(Patch::id, Patch::index))
        if (script.lastPatch?.needsAction(true) == false)
            script.lastPatch = null
        val patch = patches.filterNot { it.tile == script.lastPatch?.tile }
            .firstOrNull {
                it.needsAction() && (hasEnoughWater || it.isDone())
            } ?: script.lastPatch ?: return
        if (patch.handle(script.patches) && Condition.wait({ !patch.needsAction(true) }, 500, 10)) {
            val nextPatchIndex = script.patches.indexOfFirst { it.tile == patch.tile } + 1
            val nextPatch = if (nextPatchIndex == script.patches.size) null
            else script.patches[nextPatchIndex]
            script.log.info("Current patcht index=$nextPatchIndex, nextPatch at tile=${nextPatch?.tile}")
            if (nextPatch != null) {
                nextPatch.walkBetween(patches)
                Condition.wait({ nextPatch.needsAction(true) }, 150, 20)
            }
        }
    }

}