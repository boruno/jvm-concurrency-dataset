package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(elem = null, next = null)
    private val last = Node<E>(elem = null, next = null)
    init {
        first.next.value = last
    }

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */

    private fun abstract (element1: E) : Node<E> {
        var f   = first
        var nval   = f.next.value
        while(nval != last && element1 > nval!!.elem!!){
            f = f.next.value!!
            nval = f.next.value
        }
        return f
    }

    fun add(element1: E): Boolean {
        while(true) {
            val f  = abstract(element1)
            val nval  = f.next.value
            if(nval == null) return true
            if(nval.elem!! == element1) return false
            if (f.next.compareAndSet(nval, Node(element1, nval))) return true
        }
    }

    /**
     * Removes the specified element from this set
     * if it is present.
     *
     * Returns `true` if this set contained
     * the specified element.
     */
    fun remove(element1: E): Boolean {
        while(true) {
            val f = abstract(element1)
            val nval = f.next.value
            if(nval == null) return true
            if(nval.elem!! != element1) return false
            if (f.next.compareAndSet(nval, nval.next.value)) return true
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element1: E): Boolean {
        val f = abstract(element1)
        return f.next.value!!.elem!! == element1
    }
}

private open class Node<E : Comparable<E>>(val elem: E?, next: Node<E>?) {
    val next = atomic(next)

}