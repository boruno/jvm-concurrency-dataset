package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.TimeUnit
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
        val p = ThreadInfo(Operation.PUSH, Node(x, null))
        stackOp(p)
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val p = ThreadInfo<E>(Operation.POP, null)
        stackOp(p)
        return p.node?.x
    }

    private fun stackOp(p: ThreadInfo<E>) {
        if (!tryPerformStackOp(p)) {
            lesOP(p)
        }
    }

    private fun lesOP(p: ThreadInfo<E>) {
        while (true) {
            location.get().set(p)
            val pos = rand.nextInt(ELIMINATION_ARRAY_SIZE)
            var him = collision[pos].value
            while (!collision[pos].compareAndSet(him, location.get())) {
                him = collision[pos].value
            }
            if (him != null) {
                val q = him.get()
                if (q != null && q.op != p.op) {
                    if (location.get().compareAndSet(p, null)) {
                        if (tryCollision(him, p, q)) {
                            return
                        } else {
                            if (tryPerformStackOp(p)) {
                                return
                            }
                            continue
                        }
                    } else {
                        finishCollision(p)
                        return
                    }
                }
            }
            repeat(1000) {
                if (location.get().get() != p) {
                    finishCollision(p)
                    return
                }
            }
            if (!location.get().compareAndSet(p, null)) {
                finishCollision(p)
                return
            }
            if (tryPerformStackOp(p)) {
                return
            }
        }
    }

    private fun tryCollision(him: AtomicReference<ThreadInfo<E>?>, p: ThreadInfo<E>, q: ThreadInfo<E>): Boolean {
        return if (p.op == Operation.PUSH) {
            him.compareAndSet(q, p)
        } else {
            if (him.compareAndSet(q, null)) {
                p.node = q.node
                location.get().set(null)
                true
            } else {
                false
            }
        }
    }

    private fun tryPerformStackOp(p: ThreadInfo<E>): Boolean {
        if (p.op == Operation.PUSH) {
            val phead = top.value
            p.node = Node(p.node!!.x, phead)
            return top.compareAndSet(phead, p.node)
        } else {
            val phead = top.value
            if (phead == null) {
                p.node = null
                return true
            }
            val pnext = phead.next
            return if (top.compareAndSet(phead, pnext)) {
                p.node = phead
                true
            } else {
                p.node = null
                false
            }
        }
    }

    private fun finishCollision(p: ThreadInfo<E>) {
        if (p.op == Operation.POP) {
            p.node = location.get().get()?.node
            location.get().set(null)
        }
    }
}

private enum class Operation {
    PUSH, POP
}

private class ThreadInfo<E>(val op: Operation, var node: Node<E>?)
private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT