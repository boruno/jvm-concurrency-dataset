import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.loop
import java.util.*

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock = atomic(false)
    private val head = atomic<Node<E>?>(null)
    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return doTask(TaskCategory.Poll).value
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return doTask(TaskCategory.Peek).value
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        doTask(TaskCategory.Add, element)
    }

    private fun createTask(task: TaskCategory, x: E?): Node<E> {
        val newNode = Node(taskCategory = task, value = x)
        head.loop {
            newNode.next = it
            if (head.compareAndSet(it, newNode))
                return newNode
        }
    }

    private fun doTask(task: TaskCategory, x: E? = null): Node<E> {
        val request = createTask(task, x)
        while (true) {
            if (lock.compareAndSet(expect = false, update = true)) {
                var curTask = head.value
                var nextTask = curTask?.next
                while (curTask != null) {
                    if (curTask.active) {
                        when (curTask.taskCategory) {
                            TaskCategory.Add -> {
                                q.add(x)
                                curTask.active = false
                            }
                            TaskCategory.Peek -> {
                                curTask.value = q.peek()
                                curTask.active = false
                            }
                            TaskCategory.Poll -> {
                                curTask.value = q.poll()
                                curTask.active = false
                            }
                        }
                    }
                    curTask.next = null
                    curTask = nextTask
                    nextTask = curTask?.next
                }
                lock.compareAndSet(expect = true, update = false)
                return request
            }
            for (i in 0..CYCLES_WAITING) {
                if (!request.active) {
                    return request
                }
            }
        }
    }
}

private class Node<E> (
    var active: Boolean = true,
    var taskCategory: TaskCategory,
    var value: E? = null,
    var next: Node<E>? = null
)
private enum class TaskCategory {
    Add, Peek, Poll
}

private const val CYCLES_WAITING = 15