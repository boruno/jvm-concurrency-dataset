package mpp.stackWithElimination

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Task<E>?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        var idx : Int? = null
        var k = 0
        val task = Task(x, "PUSH")
        while (k < 10) {
            if (eliminationArray[0].value == null) {
                if (eliminationArray[0].compareAndSet(null, task)) {
                    idx = 0
                    break
                }
            }
            if (eliminationArray[1].value == null) {
                if (eliminationArray[1].compareAndSet(null, task)) {
                    idx = 1
                    break
                }
            }
            k++
        }
        var ch = 0
        while (ch < 10 && idx != null) {
            if (eliminationArray[idx].value!!.status.value == "READED") {
                if (eliminationArray[idx].compareAndSet(task, null)) {
                    return
                }
            }
            ch++
        }
        if (idx != null) {
            eliminationArray[idx].compareAndSet(task, null)
            if (task.status.value == "READED") {
                    return
            }
        }
        while (true) {
            val head = top.value
            val newHead = Node(x, head)
            if (top.compareAndSet(head, newHead)) {
                return
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val task = eliminationArray[0].value
        if (task != null) {
            if (task.status.compareAndSet("PUSH", "READED")) {
                return task._value.value
            }
        }
        val task1 = eliminationArray[1].value
        if (task1 != null) {
            if (task1.status.compareAndSet("PUSH", "READED")) {
                return task1._value.value
            }
        }
        while(true) {
            val head = top.value
            if (head == null) {
                return null
            }
            val next = head.next
            if (top.compareAndSet(head, next)) {
                return head.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

class Task<E>(number: E, command: String) {
    val _value: AtomicRef<E> = atomic(number)
    val status: AtomicRef<String> = atomic(command)
}

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT