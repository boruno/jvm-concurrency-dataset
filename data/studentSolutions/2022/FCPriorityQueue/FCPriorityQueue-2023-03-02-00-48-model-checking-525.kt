import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.loop
import java.util.*

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock = atomic(false)
    private val head = atomic<Node?>(null)
    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return doTask(TaskCategory.Poll).value.value as E?
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return doTask(TaskCategory.Peek).value.value as E?
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        doTask(TaskCategory.Add, element)
    }

    private fun createTask(task: TaskCategory, x: E?): Node {
        val newNode = Node(taskCategory = task, element = x)
        head.loop {
            newNode.next = it
            if (head.compareAndSet(it, newNode))
                return newNode
        }
    }

    private fun doTask(task: TaskCategory, x: E? = null): Node {
        val request = createTask(task, x)
        while (true) {
            if (lock.compareAndSet(expect = false, update = true)) {
                try {
                    var curTask = head.value
                    var nextTask = curTask?.next
                    while (curTask != null) {
                        if (curTask.active.value) {
                            when (curTask.taskCategory) {
                                TaskCategory.Add -> {
                                    q.add(x)
                                    curTask.active.compareAndSet(true, false)
                                }
                                TaskCategory.Peek -> {
                                    curTask.value.compareAndSet(curTask.value.value, q.peek())
                                    curTask.active.compareAndSet(true, false)
                                }
                                TaskCategory.Poll -> {
                                    curTask.value.compareAndSet(curTask.value.value, q.poll())
                                    curTask.active.compareAndSet(true, false)
                                }
                            }
                        }
                        curTask.next = null
                        curTask = nextTask
                        nextTask = curTask?.next
                    }
                } finally {
                    lock.compareAndSet(expect = true, update = false)
                }
                return request
            }
            for (i in 0..CYCLES_WAITING) {
                if (!request.active.value) {
                    return request
                }
            }
        }
    }
}

private class Node(
    val active: AtomicBoolean = atomic(true),
    var taskCategory: TaskCategory,
    val element: Any? = null,
    var next: Node? = null
) {
    val value: AtomicRef<Any?> = atomic(element)
}
private enum class TaskCategory {
    Add, Peek, Poll
}

private const val CYCLES_WAITING = 15