@file:Suppress("HasPlatformType")

package org.jetbrains.research.juc

import java.util.concurrent.locks.ReentrantReadWriteLock

class ReentrantReadWriteLockSetTest : AbstractSetTest(ReentrantReadWriteLockBasedSet())

internal class ReentrantReadWriteLockBasedSet : Set {
    private val set = mutableSetOf<Int>()
    private val lock = ReentrantReadWriteLock()

    override fun add(key: Int): Boolean = lock.writeLock().withLock {
        set.add(key)
    }

    override fun remove(key: Int): Boolean = lock.writeLock().withLock {
        set.remove(key)
    }

    override fun contains(key: Int): Boolean = lock.readLock().withLock {
        set.contains(key)
    }
}

private inline fun <T> ReentrantReadWriteLock.ReadLock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}

private inline fun <T> ReentrantReadWriteLock.WriteLock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}

