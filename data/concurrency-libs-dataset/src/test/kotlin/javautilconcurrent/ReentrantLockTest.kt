@file:Suppress("HasPlatformType")

package javautilconcurrent

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ReentrantLockSetTest : AbstractSetTest(ReentrantLockBasedSet())

internal class ReentrantLockBasedSet : Set {
    private val set = mutableSetOf<Int>()
    private val lock = ReentrantLock()

    override fun add(key: Int): Boolean = lock.withLock {
        set.add(key)
    }

    override fun remove(key: Int): Boolean = lock.withLock {
        set.remove(key)
    }

    override fun contains(key: Int): Boolean = lock.withLock {
        set.contains(key)
    }
}

