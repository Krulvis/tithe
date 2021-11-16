package org.powbot.opensource.tithe.tree.leaf

import org.powbot.api.Condition
import org.powbot.api.Notifications
import org.powbot.api.rt4.*
import org.powbot.api.script.tree.Leaf
import org.powbot.krulvis.api.ATContext.interact
import org.powbot.opensource.tithe.TitheFarmer
import org.powbot.mobile.script.ScriptManager

class Start(script: TitheFarmer) : Leaf<TitheFarmer>(script, "Starting") {

    override fun execute() {
        if (script.lastRound) {
            Notifications.showNotification("Stopped because it was last round")
            script.log.info("Stopped because it was last round")
            ScriptManager.stop()
            return
        }
        if (!script.hasSeeds()) {
            script.log.info("Getting seeds")
            if (!chatting()) {
                val table = Objects.stream(25).name("Seed table").findFirst()
                script.log.info("Interacting with seed table=${table.isPresent}")
                table.ifPresent {
                    if (interact(it, "Search", useMenu = false)) {
                        Condition.wait({ chatting() }, 250, 10)
                    }
                }
            }
            val seed = getSeedInput()
            script.log.info("Selecting seed: $seed")
            if (chatting() && Chat.continueChat(seed)) {
                Condition.wait({ script.hasSeeds() }, 100, 20)
            }
        } else {
            script.log.info("Interacting with door")
            Objects.stream(25).name("Farm door").findFirst().ifPresent {
                if (interact(it, "Open", useMenu = false)) {
                    Condition.wait({ script.getPoints() >= 0 }, 250, 40)
                }
            }
        }
    }

    fun chatting() = Widgets.component(Constants.CHAT_WIDGET, 0).visible()

    fun getSeedInput(): String {
        val farming = Skills.level(Constants.SKILLS_FARMING)
        return if (farming >= 74) {
            "Logavano"
        } else if (farming >= 54) {
            "Bologano"
        } else {
            "Golovanova"
        }
    }
}