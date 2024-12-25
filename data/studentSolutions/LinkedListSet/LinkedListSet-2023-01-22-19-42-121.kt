//package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(prev = null, element = Int.MIN_VALUE as E, next = null)
    private val last = Node<E>(prev = first, element = Int.MAX_VALUE as E, next = null)
    init {
        first.setNext(last)
    }

    private val head = atomic(first)

    private fun findPredecessorOrEqPrev(element: E) : Pair<Node<E>,Node<E>> {
        while (true) {
            var prev = head.value
            var cur = prev.next
            while (true) {
                val newNext = cur!!.next ?: return Pair<Node<E>,Node<E>>(prev, cur)
                if (newNext is Removed<*>) {
                    val removedNext = newNext.next
                    if (prev.next!!.casNext(cur, removedNext)) {
                        cur = removedNext
                        continue
                    } else break
                }
                if (cur.element >= element){
                    return Pair(prev, cur)
                }
                prev = cur
                cur = newNext
            }
        }
    }

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean {
        while (true) {
            val (prev, cur) = findPredecessorOrEqPrev(element)
            if (cur.element == element) return false
            val newNode = Node(prev, element, cur)
            // идем всегда слева направо проблем быть не должно
            if (prev.casNext(cur, newNode) && cur.casPrev(prev, newNode)) return true
        }
    }

    /**
     * Removes the specified element from this set
     * if it is present.
     *
     * Returns `true` if this set contained
     * the specified element.
     */
    fun remove(element: E): Boolean {
        //
        // не вижу смысла делать remove с prev, если нам дают только элемент
        // но мы конечно сделаем вид что его учиываем, но вообще как будто кажется что надо использовать cas2
        while (true) {
            val (prev, cur) = findPredecessorOrEqPrev(element)
            if (cur.element !== element) return false
            val node = cur.next
            if (node !is Removed<*>) {
                val removeNode = Removed(cur)
                if (cur.casNext(node, removeNode)) {
                    prev.casNext(cur, node)
                    return true
                }
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        return findPredecessorOrEqPrev(element).second.element == element
    }
}

private open class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    private val _prev = atomic(prev)
    val prev get() = _prev.value
    fun setPrev(value: Node<E>?) {
        _prev.value = value
    }
    fun casPrev(expected: Node<E>?, update: Node<E>?) =
        _prev.compareAndSet(expected, update)

    private val _next = atomic(next)
    val next get() = _next.value
    fun setNext(value: Node<E>?) {
        _next.value = value
    }
    fun casNext(expected: Node<E>?, update: Node<E>?) =
        _next.compareAndSet(expected, update)
}

private class Removed<E : Comparable<E>>(node: Node<E>) : Node<E>(node.prev,node.element, node.next)