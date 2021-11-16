package org.powbot.opensource.tithe.tree.leaf

import org.powbot.api.Condition
import org.powbot.api.rt4.GameObject
import org.powbot.api.rt4.Inventory
import org.powbot.api.rt4.Objects
import org.powbot.api.script.tree.Leaf
import org.powbot.krulvis.api.ATContext.interact
import org.powbot.opensource.tithe.Data
import org.powbot.opensource.tithe.TitheFarmer
import java.util.*

class Deposit(script: TitheFarmer) : Leaf<TitheFarmer>(script, "Depositing") {

    fun getSack(): Optional<GameObject> =
        Objects.stream(25).name("Sack").nearest().findFirst()

    override fun execute() {
        val sack = getSack()
        println("Sack: $sack")
        sack.ifPresent {
            if (interact(it, "Deposit", useMenu = false)) {
                Condition.wait({ Inventory.stream().id(*Data.HARVEST).isEmpty() }, 150, 20)
            }
        }
    }
}