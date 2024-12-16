@file:Suppress("HasPlatformType")

package org.jetbrains.research.juc

import java.util.concurrent.locks.StampedLock

class StampedLockSetTest : AbstractSetTest(StampedLockBasedSet())

internal class StampedLockBasedSet : Set {
    private val set = mutableSetOf<Int>()
    private val lock = StampedLock()

    override fun add(key: Int): Boolean {
        val stamp = lock.writeLock()
        return try {
            set.add(key)
        } finally {
            lock.unlockWrite(stamp)
        }
    }

    override fun remove(key: Int): Boolean {
        val stamp = lock.writeLock()
        return try {
            set.remove(key)
        } finally {
            lock.unlockWrite(stamp)
        }
    }

    override fun contains(key: Int): Boolean {
        val stamp = lock.readLock()
        return try {
            set.contains(key)
        } finally {
            lock.unlockRead(stamp)
        }
    }
}