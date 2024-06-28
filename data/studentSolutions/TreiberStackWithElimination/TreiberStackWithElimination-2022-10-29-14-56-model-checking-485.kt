package mpp.stackWithElimination

import kotlinx.atomicfu.atomicArrayOfNulls
import java.lang.Thread.currentThread
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val stack = TreiberStack<E>()
    private val eliminationArray = atomicArrayOfNulls<Record<E>?>(ELIMINATION_ARRAY_SIZE)

    private data class Record<E>(val threadId: Long, val value: E)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val threadId = currentThread().id
        val index = Random.nextInt(0, ELIMINATION_ARRAY_SIZE)
        if (index == -1) {
            println("asdasdasdasdasdasdasd")
        }
//        for (i in 0..10) {
//            if (eliminationArray[index].compareAndSet(null, Record(threadId, x))) {
//                break
//            }
//            if (i == 10) {
//                stack.push(x)
//                return
//            }
//        }
        val record = Record(threadId, x)
        while (true) {
            if (eliminationArray[index].compareAndSet(null, record)) {
                break
            }
        }

        for (i in 0..10) {
            if (eliminationArray[index].value == null || eliminationArray[index].value != record) {
                return
            }
        }

        if (eliminationArray[index].compareAndSet(record, null)) {
            stack.push(x)
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
//        val threadId = currentThread().id
        val index = Random.nextInt(0, ELIMINATION_ARRAY_SIZE)

        var him = eliminationArray[index].value
        for (i in 0..10) {
            if (him != null) {
                if (eliminationArray[index].compareAndSet(him, null)) {
                    return him.value
                } else {
                    him = eliminationArray[index].value
                }
            }
        }

        return stack.pop()
    }
}

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT