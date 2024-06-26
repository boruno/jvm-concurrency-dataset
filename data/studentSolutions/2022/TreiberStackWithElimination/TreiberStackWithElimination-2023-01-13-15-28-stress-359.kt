package mpp.stackWithElimination

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Task<E>>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        var idx : Int? = null
        var k = 0
        while (k < 100) {
            //println(x)
            if (eliminationArray[0].value == null) {
                if (eliminationArray[0].compareAndSet(null, Task("PUSH",x))) {
                    idx = 0
                    break
                }
            }
            if (eliminationArray[1].value == null) {
                if (eliminationArray[1].compareAndSet(null, Task("PUSH",x))) {
                    idx = 1
                    break
                }
            }
            k++
        }
        var ch = 0
        while (ch < 100 && idx != null) {
            if (eliminationArray[idx].value!!.status.value == Status.DONE) {
                eliminationArray[idx].value = null
                return
            }
            ch++
        }
        while (true) {
            val head = top.value
            val newHead = Node(x, head)
            if (top.compareAndSet(head, newHead)) {
                if (idx != null) {
                    eliminationArray[idx].value = null
                }
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
        if (eliminationArray[0].value != null) {
            val answer = eliminationArray[0].value!!.value.value
            if (eliminationArray[0].value!!.status.compareAndSet(Status.TODO, Status.DONE)) {
                return answer
            }
        }
        if (eliminationArray[1].value != null) {
            val answer = eliminationArray[1].value!!.value.value
            if (eliminationArray[1].value!!.status.compareAndSet(Status.TODO, Status.DONE)) {
                return answer
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

class Task<E>(command: String, number: E?) {
    val status : AtomicRef<Status> = atomic(Status.TODO)
    val name: AtomicRef<String> = atomic(command)
    val value: AtomicRef<E?> = atomic(number)
}

enum class Status {
    TODO,
    DONE,
    WAITING_FOR_DELETE
}

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT