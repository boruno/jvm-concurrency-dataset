import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

interface Stack<E> {
    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E)

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E?
}

class TreiberStackBase<E> : Stack<E> {
    private class Node<E>(val x: E, val next: Node<E>?)

    private val top = atomic<Node<E>?>(null)

    override fun push(x: E) {
        while (true) {
            val curTop = top.value
            val newTop = Node(x, curTop)
            if (top.compareAndSet(curTop, newTop))
                return
        }
    }

    override fun pop(): E? {
        while (true) {
            val curTop = top.value ?: return null
            val nextTop = curTop.next

            if (top.compareAndSet(curTop, nextTop))
                return curTop.x
        }
    }
}

/**
 * Decorator for a stack to optimize high loads
 */
class StackEliminationArray<E>(private val base: Stack<E>) : Stack<E> {
    private val slots = atomicArrayOfNulls<E>(ELIMINATION_ARRAY_SIZE)
    private val slotStatuses = AtomicIntArray(ELIMINATION_ARRAY_SIZE) // EMPTY by default

    override fun push(x: E) {
        val slotIndex = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        val slot = slots[slotIndex]
        val status = slotStatuses[slotIndex]

        if (!status.compareAndSet(EMPTY, USED_PUSH))
            return base.push(x)

        slot.value = x
        status.value = WAIT

        repeat(LIVE_LOCK_ITERATIONS) {
            if (status.compareAndSet(FINISHED, EMPTY))
                return
        }

        if (status.compareAndSet(WAIT, USED_PUSH)) {
            slot.value = null
            status.value = EMPTY
            base.push(x)
        } else {
            while (!status.compareAndSet(FINISHED, EMPTY));
        }
    }

    override fun pop(): E? {
        val slotIndex = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        val slot = slots[slotIndex]
        val status = slotStatuses[slotIndex]

        if (!status.compareAndSet(WAIT, USED_POP))
            return base.pop()

        val value = slot.value
        slot.value = null
        status.value = FINISHED
        return value
    }

    companion object {
        private const val LIVE_LOCK_ITERATIONS = 100

        private const val EMPTY = 0
        private const val USED_PUSH = 1
        private const val WAIT = 2
        private const val USED_POP = 3
        private const val FINISHED = 4
    }
}
class TreiberStack<E> : Stack<E> by StackEliminationArray(TreiberStackBase())

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT