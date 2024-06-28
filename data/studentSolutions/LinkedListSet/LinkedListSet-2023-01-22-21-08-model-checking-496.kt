package mpp.linkedlistset

import kotlinx.atomicfu.atomic

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(element = Int.MIN_VALUE as E, next = null)
    private val last = Node<E>(element = Int.MAX_VALUE as E, next = null)

    private val head = atomic(first)

    init {
        first.setNext(last)
    }

    fun test() {
    }

//    private fun findWindow(x: Int): Window<E> {
//        retry@ while (true) {
//            var cur = head
//            var next = head.value.next.value // head is not deleted
//            println(123)
//            while (true) {
//                //val an = next!!.next.value
//                if (next!!.element == Int.MAX_VALUE) {
//                    cur.next.compareAndSet(next, an)
//                    continue@retry
//                } else {
//                    val afterNext = an
//                    if (cur.element!! < x && x <= next.element!!) {
//                        return Window(cur, next, afterNext)
//                    }
//                    cur = next
//                    next = afterNext
//                }
//            }
//        }
//    }
//
//
//    private fun checkKey(x: Int) {
//        require(!(x == Int.MIN_VALUE || x == Int.MAX_VALUE))
//    }

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean {
        while (true) {
            var current = first
            var next = current.next

            while (next != last && element > next!!.element) {
                current = current.next!!
                next = current.next
            }

            if (current is Removed|| next is Removed) continue

            if (next.element == element) {
                return false
            }

            val newNode = Node(element, next)
            if (current.casNext(next, newNode)) {
                return true
            }
        }

    }

    /**
     * Removes the specified element from this set
     * if it is present.
     *
     * Returns `true` if this set contained
     * the specified element.
     */
    fun remove(element: Int): Boolean {
       return true
    }


    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: Int): Boolean {
        return true
    }
}

private open class Node<E: Comparable<E>>(element: E?, next: Node<E>?) {
    val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    // val killed = atomic(false)

    val _next = atomic(next)
    val next get() = _next.value
    fun setNext(value: Node<E>?) {
        _next.value = value
    }

    fun casNext(expected: Node<E>?, update: Node<E>?) =
        _next.compareAndSet(expected, update)
}

private class Removed<E : Comparable<E>>(node: Node<E>) : Node<E>(node.element, node.next!!) {

}
