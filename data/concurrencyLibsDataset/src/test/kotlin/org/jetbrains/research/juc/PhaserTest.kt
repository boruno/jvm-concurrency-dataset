@file:Suppress("HasPlatformType")

package org.jetbrains.research.juc

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.*

class PhaserTest {
    private val phaser = Phaser(1)

    @Operation
    fun register() = phaser.register()

    @Operation
    fun arrive() = phaser.arrive()

    @Operation
    fun arriveAndDeregister() = phaser.arriveAndDeregister()

    @Operation
    fun awaitAdvance(phase: Int) = phaser.awaitAdvance(phase)

    @Operation
    fun getPhase() = phaser.phase

    @Operation
    fun getRegisteredParties() = phaser.registeredParties

    @Ignore("Blocking structures are currently unsupported")
    @Test
    fun phaserStressTest() = StressOptions().check(this::class)

    @Ignore("Blocking structures are currently unsupported")
    @Test
    fun phaserModelCheckingTest() = ModelCheckingOptions().check(this::class)
}
