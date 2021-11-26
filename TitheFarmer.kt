package org.powbot.opensource.tithe

import com.google.common.eventbus.Subscribe
import org.powbot.api.*
import org.powbot.api.event.*
import org.powbot.api.rt4.*
import org.powbot.api.rt4.walking.local.LocalPathFinder
import org.powbot.api.rt4.walking.model.Skill
import org.powbot.api.script.OptionType
import org.powbot.api.script.ScriptCategory
import org.powbot.api.script.ScriptConfiguration
import org.powbot.api.script.ScriptManifest
import org.powbot.api.script.paint.PaintBuilder
import org.powbot.api.script.paint.PaintFormatters
import org.powbot.api.script.tree.TreeComponent
import org.powbot.api.script.tree.TreeScript
import org.powbot.krulvis.api.utils.Timer
import org.powbot.mobile.script.ScriptManager
import org.powbot.opensource.tithe.Data.NAMES
import org.powbot.opensource.tithe.Patch.Companion.isPatch
import org.powbot.opensource.tithe.tree.branch.ShouldStart
import kotlin.math.min

@ScriptManifest(
    name = "krul Tithe",
    description = "Tithe farming mini-game",
    author = "Krulvis",
    version = "1.0.8",
    scriptId = "97078671-3780-4a44-b488-36ef241686dd",
    markdownFileName = "Tithe.md",
    category = ScriptCategory.Farming
)
@ScriptConfiguration.List(
    [
        ScriptConfiguration(
            name = "Patches",
            description = "How many patches do you want to use? Max 20",
            optionType = OptionType.INTEGER,
            defaultValue = "16"
        )
    ]
)
class TitheFarmer : TreeScript() {

    override val rootComponent: TreeComponent<*> = ShouldStart(this)

    val patchCount by lazy { min(getOption<Int>("Patches")?.toInt() ?: 20, 20) }
    var lastPatch: Patch? = null
    var lastRound = false
    var startPoints = -1
    var gainedPoints = 0
    var patches = listOf<Patch>()
    val chillTimer = Timer(5000)
    var planting = false

    override fun onStart() {
        super.onStart()
        addPaint(PaintBuilder()
            .addString("Last leaf:") { lastLeaf.name }
            .addString("Gained points:") {
                "${gainedPoints}, (${
                    PaintFormatters.perHour(
                        gainedPoints,
                        ScriptManager.getRuntime(true)
                    )
                }/hr)"
            }
            .trackSkill(Skill.Farming)
            .addCheckbox("Last round:", "lastRound", false)
            .build())
    }

    fun getCornerPatchTile(): Tile {
        val allPatches = Objects.stream(30).filtered { it.isPatch() }.list()
        val maxX = allPatches.minOf { it.tile().x() }
        val maxY = allPatches.maxOf { it.tile().y() }
        return Tile(maxX, maxY, 0)
    }

    fun getPatchTiles(): List<Tile> {
        val tiles = mutableListOf(getCornerPatchTile())
        val columns = mutableListOf<Tile>()
        tiles.add(Tile(tiles[0].x(), tiles[0].y() - 15))
        tiles.forEach {
            for (y in 0..9 step 3) {
                columns.add(Tile(it.x(), it.y() - y))
                columns.add(Tile(it.x() + 5, it.y() - y))
            }
        }

        val lastPatch = Tile(tiles[0].x() + 10, tiles[0].y() - 9)
        for (y in 0..9 step 3) {
            columns.add(Tile(lastPatch.x(), lastPatch.y() + y))
        }

        return columns.toList().subList(0, patchCount)
    }

    fun refreshPatches() {
        val tiles = getPatchTiles()
        val onorderedPatches =
            Objects.stream(35).filtered { it.name().isNotEmpty() && it.name() != "null" && it.tile() in tiles }
                .list()
        patches = tiles.mapIndexed { index, tile ->
            Patch(onorderedPatches.firstOrNull { it.tile() == tile } ?: GameObject.Nil, tile, index)
        }

    }

    fun hasSeeds(): Boolean = getSeed() > 0

    fun getSeed(): Int {
        val seed = Inventory.stream().id(*Data.SEEDS).findFirst()
        return if (seed.isPresent) seed.get().id() else -1
    }

    fun getPoints(): Int {
        val widget =
            Widgets.widget(241).components().firstOrNull { it.text().contains("Points:") } ?: return -1
        return widget.text().substring(8).toInt()
    }

    fun getWaterCount(): Int = Inventory.stream().id(*Data.WATER_CANS).list().sumOf { it.id() - 5332 }

    fun hasEnoughWater(): Boolean = getWaterCount() >= patchCount * 3

    @Subscribe
    fun onGameActionEvent(evt: GameActionEvent) {
        if (evt.rawOpcode == 4 || evt.opcode() == GameActionOpcode.InteractObject) {
            val tile = Tile(evt.var0 + 1, evt.widgetId + 1, 0).globalTile()
            if (NAMES.any { evt.rawEntityName.contains(it, true) }) {
                lastPatch = patches.first { it.tile == tile }
                log.info("Interacted ${evt.interaction} on $lastPatch")
            }
        }
    }

    @com.google.common.eventbus.Subscribe
    fun onCheckBoxEvent(e: PaintCheckboxChangedEvent) {
        if (e.checkboxId == "lastRound") {
            lastRound = e.checked
        }
    }

    @com.google.common.eventbus.Subscribe
    fun onMsg(e: MessageEvent) {
        log.info("MSG: \n Type=${e.type}, msg=${e.message}")
    }

    var lastTick = -1L

    @Subscribe
    fun onGameTick(e: TickEvent) {
        lastTick = System.currentTimeMillis()
    }

    private fun Tile.globalTile(): Tile {
        val a = Game.mapOffset()
        return this.derive(+a.x(), +a.y())
    }

    fun interact(
        target: Interactive?,
        action: String,
        selectItem: Int = -1,
        useMenu: Boolean = true
    ): Boolean {
        val t = target ?: return false
        val name = (t as Nameable).name()
        val pos = (t as Locatable).tile()
        val destination = Movement.destination()
        turnRunOn()
        if (!t.inViewport()
            || (destination != pos && pos.distanceTo(if (destination == Tile.Nil) Players.local() else destination) > 12)
        ) {
            log.info("Walking before interacting... in viewport: ${t.inViewport()}")
            if (pos.matrix().onMap()) {
                Movement.step(pos)
            } else {
                LocalPathFinder.findWalkablePath(pos).traverse()
            }
        }
        val selectedId = Inventory.selectedItem().id()
        if (selectedId != selectItem) {
            Game.tab(Game.Tab.INVENTORY)
            if (selectItem > -1) {
                Inventory.stream().id(selectItem).firstOrNull()?.interact("Use")
            } else {
                Inventory.stream().id(selectedId).firstOrNull()?.click()
            }

        }
        val interactBool =
            if (name == null || name == "null" || name.isEmpty()) t.interact(action, useMenu) else t.interact(
                action,
                name,
                useMenu
            )
        return Condition.wait {
            Inventory.selectedItemIndex() == -1 || Inventory.selectedItem().id() == selectItem
        } && interactBool
    }

    fun turnRunOn(): Boolean {
        if (Movement.running()) {
            return true
        }
        if (Movement.energyLevel() >= Random.nextInt(1, 5)) {
            return Widgets.widget(Constants.MOVEMENT_MAP).component(Constants.MOVEMENT_RUN_ENERGY - 1).click()
        }
        return false
    }


}

fun main() {
    TitheFarmer().startScript(true)
}