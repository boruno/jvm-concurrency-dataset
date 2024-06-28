package day4

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Op>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) = request {
        queue.addLast(element)
    }

    override fun dequeue(): E? = request {
        queue.removeFirstOrNull()
    }

    private fun assist() {
        for (i in 0 until tasksForCombiner.size) {
            val op = tasksForCombiner[i].value
            if (op is OpRequest) {
                tasksForCombiner[i].value = op.execute()
            }
        }
    }

    private inline fun locking(f: () -> Unit) {
        if (!combinerLock.compareAndSet(false, true))
            return
        try {
            f()
        } finally {
            assist()
            combinerLock.value = false
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)

    private interface Op

    private interface OpResult : Op {
        val value: Any?
    }

    private abstract class OpResultBase<R> : OpResult {
        abstract override val value: R
    }

    private interface OpRequest : Op {
        fun execute(): OpResult
    }

    private abstract inner class OpRequestBase<R> : OpRequest {
        abstract override fun execute(): OpResultBase<R>

        fun run(): R {
            while (true) {
                locking { return this.execute().value as R }
                val index = randomCellIndex()
                if (!tasksForCombiner[index].compareAndSet(null, this)) {
                    continue
                }
                while (true) {
                    val v = tasksForCombiner[index].value
                    if (v is OpResult) {
                        tasksForCombiner[index].value = null
                        @Suppress("UNCHECKED_CAST")
                        return v.value as R
                    }
                    locking {
                        if (tasksForCombiner[index].compareAndSet(this, null)) {
                            return this.execute().value as R
                        }
                        val v = tasksForCombiner[index].value
                        require(v is OpResult) {
                            "Unexpected op at $index: $v"
                        }
                        tasksForCombiner[index].value = null
                        @Suppress("UNCHECKED_CAST")
                        return v.value as R
                    }
                }
            }
        }
    }

    private inner class OpRequestF<R>(private val f : () -> R) : OpRequestBase<R>() {
        override fun execute(): OpResultBase<R> = object : OpResultBase<R>() {
            override val value: R
                get() = f()

        }
    }

    private fun <R> request(f: () -> R): R {
        return OpRequestF(f).run()
    }
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!