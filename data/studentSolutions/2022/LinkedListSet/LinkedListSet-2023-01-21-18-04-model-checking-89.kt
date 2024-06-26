package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(prev = null, element = null, next = null)
    private val last = Node<E>(prev = first, element = null, next = null)
    init {
        first.setNext(last)
    }

    private val head = atomic(first)

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean {
        var curNode = head.value
        var curPrev = curNode.prev
        var curNext = curNode.next
        if (!contains(element)) {
            while (true) {
                if (curNext == null) {
                    if (curPrev == null) {
                        throw NullPointerException("PEPE")
                    }
                    curNode.casNext(curNode, Node(curNode, element, null)) // check it
                    return true
                } else {
                    curPrev = curNode
                    curNode = curNext
                    curNext = curNext.next
                    continue
                }
            }
        }
        return false
    }

    /**
     * Removes the specified element from this set
     * if it is present.
     *
     * Returns `true` if this set contained
     * the specified element.
     */
    fun remove(element: E): Boolean {
        //cur.value < key <= next.value
        if (contains(element)) {
            val curNode = findNode(element)
            val curPrev = curNode.prev // Need add update if value is dead
            val curNext = curNode.next // Need add update if value is dead
            curNode._alive.compareAndSet(expect = true, update = false)
            while (true) {
                if (curPrev != null && curPrev._alive.value) {
                    if (curPrev.casNext(curNode, curNext)) {
                        if (curNext != null && curNext._alive.value) {
                            if (curNext.casPrev(curNode, curPrev)) {
                                return true
                            }
                        } else {
                            if (curNext == null) {
                                throw NullPointerException("NP[Next] Remove")
                            }
                            else {
                                curNext.casNext(curNext, curNext.next)
                                continue
                            }
                        }
                    }
                }
                else {
                    if (curPrev == null) {
                        throw NullPointerException("NP[Prev] Remove")
                    }
                    else {
                        curPrev.casPrev(curPrev, curPrev.prev)
                        continue
                    }
                }
            }
        }
       return false
    }

    private fun findNode(element: E): Node<E> {
        var curHead = head.value
        var curNext = curHead.next
        val curLast = last
        while (curNext != null && curHead != curLast) {
            if (curHead.element == element) {
                return curHead
            } else {
                curHead = curNext
                curNext = curHead.next
                continue
            }
        }
        throw Exception("Invalid element!")
    }
    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        var curHead = head.value
        var curNext = curHead.next
        val curLast = last
        while (curNext != null && curHead != curLast) {
            if (curHead.element != null && curHead.element == element) {
                return true
            } else {
                curHead = curNext
                curNext = curHead.next
            }
        }
        return false
    }
}

private class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element
    val _alive = atomic(true)

    fun setDead(state: Boolean) {
        _alive.getAndSet(state)
    }

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