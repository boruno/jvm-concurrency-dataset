package day1

import kotlinx.atomicfu.*
import java.util.concurrent.*

/*
= Invalid execution results =
Init part:
[push(2): void, push(6): void]
Parallel part:
| pop():   6            | push(-6): void | push(-8): void [2,-,-] |
| pop():   2    [1,-,-] |                |                        |
| push(4): void [2,-,-] |                |                        |
Post part:
[pop(): -6, pop(): -8]
---
values in "[..]" brackets indicate the number of completed operations
in each of the parallel threads seen at the beginning of the current operation
---
Order: push(2), push(6) | pop()->6 @1, pop()->2 @1, push(-8) @3, push(-6)@2 | pop()->-6, pop()->-8
= The following interleaving leads to the error =
Parallel part trace:
|                                                                                                         | push(-6)                                                                                                                       |                                                                                                                          |
|                                                                                                         |   push(-6) at StackTest.push(StackTests.kt:15)                                                                                 |                                                                                                                          |
|                                                                                                         |     tryPushElimination(-6): false at TreiberStackWithElimination.push(TreiberStackWithElimination.kt:23)                       |                                                                                                                          |
|                                                                                                         |       randomCellIndex(): 0 at TreiberStackWithElimination.tryPushElimination(TreiberStackWithElimination.kt:28)                |                                                                                                                          |
|                                                                                                         |       get(0): AtomicRef@1 at TreiberStackWithElimination.tryPushElimination(TreiberStackWithElimination.kt:29)                 |                                                                                                                          |
|                                                                                                         |       compareAndSet(null,-6): true at TreiberStackWithElimination.tryPushElimination(TreiberStackWithElimination.kt:30)        |                                                                                                                          |
|                                                                                                         |       compareAndSet(Object@1,null): false at TreiberStackWithElimination.tryPushElimination(TreiberStackWithElimination.kt:34) |                                                                                                                          |
|                                                                                                         |       switch                                                                                                                   |                                                                                                                          |
| pop(): 6                                                                                                |                                                                                                                                |                                                                                                                          |
| pop(): 2                                                                                                |                                                                                                                                |                                                                                                                          |
| push(4)                                                                                                 |                                                                                                                                |                                                                                                                          |
|   push(4) at StackTest.push(StackTests.kt:15)                                                           |                                                                                                                                |                                                                                                                          |
|     tryPushElimination(4): false at TreiberStackWithElimination.push(TreiberStackWithElimination.kt:23) |                                                                                                                                |                                                                                                                          |
|     push(4) at TreiberStackWithElimination.push(TreiberStackWithElimination.kt:24)                      |                                                                                                                                |                                                                                                                          |
|       <init>(4,null) at TreiberStack.push(TreiberStack.kt:11)                                           |                                                                                                                                |                                                                                                                          |
|       getValue(): null at TreiberStack.push(TreiberStack.kt:13)                                         |                                                                                                                                |                                                                                                                          |
|       switch                                                                                            |                                                                                                                                |                                                                                                                          |
|                                                                                                         |                                                                                                                                | push(-8)                                                                                                                 |
|                                                                                                         |                                                                                                                                |   push(-8) at StackTest.push(StackTests.kt:15)                                                                           |
|                                                                                                         |                                                                                                                                |     tryPushElimination(-8): false at TreiberStackWithElimination.push(TreiberStackWithElimination.kt:23)                 |
|                                                                                                         |                                                                                                                                |       randomCellIndex(): 0 at TreiberStackWithElimination.tryPushElimination(TreiberStackWithElimination.kt:28)          |
|                                                                                                         |                                                                                                                                |       switch                                                                                                             |
|       setValue(null) at TreiberStack.push(TreiberStack.kt:14)                                           |                                                                                                                                |                                                                                                                          |
|       compareAndSet(null,Node@1): true at TreiberStack.push(TreiberStack.kt:15)                         |                                                                                                                                |                                                                                                                          |
|   result: void                                                                                          |                                                                                                                                |                                                                                                                          |
|   thread is finished                                                                                    |                                                                                                                                |                                                                                                                          |
|                                                                                                         |                                                                                                                                |       get(0): AtomicRef@1 at TreiberStackWithElimination.tryPushElimination(TreiberStackWithElimination.kt:29)           |
|                                                                                                         |                                                                                                                                |       compareAndSet(null,-8): false at TreiberStackWithElimination.tryPushElimination(TreiberStackWithElimination.kt:30) |
|                                                                                                         |                                                                                                                                |     push(-8) at TreiberStackWithElimination.push(TreiberStackWithElimination.kt:24)                                      |
|                                                                                                         |                                                                                                                                |   result: void                                                                                                           |
|                                                                                                         |                                                                                                                                |   thread is finished                                                                                                     |
|                                                                                                         |       compareAndSet(-6,null): true at TreiberStackWithElimination.tryPushElimination(TreiberStackWithElimination.kt:39)        |                                                                                                                          |
|                                                                                                         |     push(-6) at TreiberStackWithElimination.push(TreiberStackWithElimination.kt:24)                                            |                                                                                                                          |
|                                                                                                         |   result: void                                                                                                                 |                                                                                                                          |
|                                                                                                         |   thread is finished                                                                                                           |                                                                                                                          |

java.lang.IllegalStateException: Non-determinism found. Probably caused by non-deterministic code (WeakHashMap, Object.hashCode, etc).
*/
class TreiberStackWithElimination<E: Any> : Stack<E> {
    private val stack = TreiberStack<E>()

    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    override fun push(element: E) {
        if (tryPushElimination(element)) return
        stack.push(element)
    }

    private fun tryPushElimination(element: E): Boolean {
        val cellIndex = randomCellIndex()
        val eliminationCell = eliminationArray[cellIndex]
        if (!eliminationCell.compareAndSet(CELL_STATE_EMPTY, element)) {
            return false
        }
        repeat(ELIMINATION_WAIT_CYCLES) {
            if (eliminationCell.compareAndSet(CELL_STATE_RETRIEVED, null)) {
                return true
            }
        }
        // Failed?
        if (eliminationCell.compareAndSet(element, null)) {
            return false
        }
        // Em?!
        assert(eliminationCell.compareAndSet(CELL_STATE_RETRIEVED, null))
        return true
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val cellIndex = randomCellIndex()
        val eliminationCell = eliminationArray[cellIndex]
        val cellValue = eliminationCell.value
        if (cellValue == CELL_STATE_EMPTY || cellValue == CELL_STATE_RETRIEVED) {
            return null
        }
        // Good element
        @Suppress("UNCHECKED_CAST")
        val element: E = cellValue as E
        if (eliminationCell.compareAndSet(element, CELL_STATE_RETRIEVED))
            return element
        return null
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(0, eliminationArray.size)

    companion object {
        private const val ELIMINATION_ARRAY_SIZE = 2 // Do not change!
        private const val ELIMINATION_WAIT_CYCLES = 1 // Do not change!

        // Initially, all cells are in EMPTY state.
        private val CELL_STATE_EMPTY = null

        private object CellStateMarker

        // `tryPopElimination()` moves the cell state
        // to `RETRIEVED` if the cell contains element.
        private val CELL_STATE_RETRIEVED = CellStateMarker
    }
}