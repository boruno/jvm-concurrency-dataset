//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val collision = atomicArrayOfNulls<AtomicReference<ThreadInfo<E>?>?>(ELIMINATION_ARRAY_SIZE)
    private val location = ThreadLocal<AtomicReference<ThreadInfo<E>?>>()
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
        return p.node!!.next?.x
    }

    private fun stackOp(p: ThreadInfo<E>) {
        if (tryPerformStackOp(p)) {
            lesOP(p)
        }
    }

    private fun lesOP(p: ThreadInfo<E>) {
        while (true) {
            location.set(AtomicReference(p))
            val pos = rand.nextInt(ELIMINATION_ARRAY_SIZE)
            var him :AtomicReference<ThreadInfo<E>?>? = collision[pos].value
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
            TimeUnit.NANOSECONDS.sleep(100)
            if (location.get().compareAndSet(p, null)) {
                finishCollision(p)
                return
            }
            if (tryPerformStackOp(p)) {
                return
            }
        }
    }

    private fun tryCollision(him: AtomicReference<ThreadInfo<E>?>, p: ThreadInfo<E>, q: ThreadInfo<E>): Boolean {
        if (p.op == Operation.PUSH) {
            return him.compareAndSet(q, p)
        } else {
            if (him.compareAndSet(q, null)) {
                p.node = q.node
                location.get().set(null)
                return true
            } else {
                return false
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
            if (top.compareAndSet(phead, pnext)) {
                p.node = phead
                return true
            } else {
                return false
            }
        }
    }

    private fun finishCollision(p: ThreadInfo<E>) {
        if (p.op == Operation.POP) {
            p.node = location.get().get()!!.node
            location.set(null)
        }
    }
}

private enum class Operation {
    PUSH, POP
}

private class ThreadInfo<E>(val op: Operation, var node: Node<E>?)
private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT