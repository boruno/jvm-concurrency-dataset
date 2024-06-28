package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.atomic.AtomicReference

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val collision = atomicArrayOfNulls<AtomicReference<ThreadInfo<E>?>?>(ELIMINATION_ARRAY_SIZE)
    private val location = object : ThreadLocal<AtomicReference<ThreadInfo<E>?>>() {
        override fun initialValue(): AtomicReference<ThreadInfo<E>?> {
            return AtomicReference(null)
        }
    }
    private val rand = Random()

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        if (!tryPerformPush(x)) {
            lesPush(x)
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val result = tryPerformPop()
        return if (!result.success) {
            lesPop()
        } else {
            result.x
        }
    }

    private fun lesPush(x: E) {
        val p = ThreadInfo(Operation.PUSH, x)
        while (true) {
            val myAndHim = getMyPidAndHim()
            val mypid = myAndHim.first
            mypid.set(p)
            val him = myAndHim.second
            if (him != null) {
                val q = him.get()
                if (q != null && q.op == Operation.POP) {
                    if (mypid.compareAndSet(p, null)) {
                        if (tryCollisionPush(him, p, q)) {
                            return
                        } else {
                            if (tryPerformPush(x)) {
                                return
                            } else {
                                continue
                            }
                        }
                    } else {
                        return
                    }
                }
            }
            if (mypid.compareAndSet(p, null)) {
                return
            }
            if (tryPerformPush(x)) {
                return
            }
        }
    }

    private fun lesPop(): E? {
        val p: ThreadInfo<E> = ThreadInfo(Operation.POP, null)
        while (true) {
            val myAndHim = getMyPidAndHim()
            val mypid = myAndHim.first
            mypid.set(p)
            val him = myAndHim.second
            if (him != null) {
                val q = him.get()
                if (q != null && q.op == Operation.PUSH) {
                    return if (mypid.compareAndSet(p, null)) {
                        val collisionResult = tryCollisionPop(him, q)
                        if (collisionResult.success) {
                            collisionResult.x
                        } else {
                            val performResult = tryPerformPop()
                            if (performResult.success) {
                                performResult.x
                            } else {
                                continue
                            }
                        }
                    } else {
                        finishCollisionPop()
                    }
                }
            }
            if (mypid.compareAndSet(p, null)) {
                return finishCollisionPop()
            }
            val performResult = tryPerformPop()
            if (performResult.success) {
                return performResult.x
            }
        }
    }

    private fun getMyPidAndHim(): Pair<AtomicReference<ThreadInfo<E>?>, AtomicReference<ThreadInfo<E>?>?> {
        val mypid = location.get()
        val pos = rand.nextInt(ELIMINATION_ARRAY_SIZE)
        var him = collision[pos].value
        while (!collision[pos].compareAndSet(him, mypid)) {
            him = collision[pos].value
        }
        return Pair(mypid, him)
    }

    private fun tryCollisionPush(him: AtomicReference<ThreadInfo<E>?>, q: ThreadInfo<E>, p: ThreadInfo<E>): Boolean {
        return him.compareAndSet(q, p)
    }

    private fun tryCollisionPop(him: AtomicReference<ThreadInfo<E>?>, q: ThreadInfo<E>): SuccessfulPop<E> {
        return if (him.compareAndSet(q, null)) {
            location.get().set(null)
            SuccessfulPop(q.x, true)
        } else {
            SuccessfulPop(null, false)
        }
    }

    private fun tryPerformPush(x: E): Boolean {
        val curTop = top.value
        val newTop = Node(x, curTop)
        return top.compareAndSet(curTop, newTop)
    }

    private fun tryPerformPop(): SuccessfulPop<E> {
        val curTop = top.value ?: return SuccessfulPop(null, true)
        val newTop = curTop.next
        return if (top.compareAndSet(curTop, newTop)) {
            SuccessfulPop(curTop.x, true)
        } else {
            SuccessfulPop(null, false)
        }
    }

    private fun finishCollisionPop(): E? {
        val result = location.get().get()?.x
        location.get().set(null)
        return result
    }
}

private enum class Operation {
    PUSH, POP
}

private class SuccessfulPop<E>(val x: E?, val success: Boolean)
private class ThreadInfo<E>(val op: Operation, val x: E?)
private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT