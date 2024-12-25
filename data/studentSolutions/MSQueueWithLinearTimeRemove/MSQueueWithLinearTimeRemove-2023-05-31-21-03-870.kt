@file:Suppress("FoldInitializerAndIfToElvis", "DuplicatedCode")

//package day3

import kotlinx.atomicfu.*

class MSQueueWithLinearTimeRemove<E> : QueueWithRemove<E> {
    private val head: AtomicRef<Node>
    private val tail: AtomicRef<Node>

    init {
        val dummy = Node(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        val newNode = Node(element)
        while (true) {
            val node = tail.value
            if (node.extractedOrRemoved) {
                val prev = node.findPrev()
                if (prev != null) {
                    tail.value = prev
                    prev.next.value = null
                    continue
                }
            }
            if (node.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(node, newNode)
                return
            } else {
                tail.compareAndSet(node, node.next.value!!)
            }
        }

    }

    override fun dequeue(): E? {
        while (true) {
            val dummy = head.value
            val value = dummy.next.value ?: return null
            if (head.compareAndSet(dummy, value) && value.markExtractedOrRemoved()) {
                return value.element
            }
        }
    }

    override fun remove(element: E): Boolean {
        // Traverse the linked list, searching the specified
        // element. Try to remove the corresponding node if found.
        // DO NOT CHANGE THIS CODE.
        var node = head.value
        while (true) {
            val next = node.next.value
            if (next == null) return false
            node = next
            if (node.element == element && node.remove()) return true
        }
    }

    /**
     * This is an internal function for tests.
     * DO NOT CHANGE THIS CODE.
     */
    override fun checkNoRemovedElements() {
        var node = head.value
        // Traverse the linked list
        while (true) {
            if (node !== head.value && node !== tail.value) {
                check(!node.extractedOrRemoved) {
                    "Removed node with element ${node.element} found in the middle of this queue"
                }
            }
            node = node.next.value ?: break
        }
    }

    /**
     * This is a string representation of the data structure,
     * you see it in Lincheck tests when they fail.
     * DO NOT CHANGE THIS CODE.
     */
    override fun toString(): String {
        // Choose the leftmost node.
        var node = head.value
        if (tail.value.next.value === node) {
            node = tail.value
        }
        // Traverse the linked list.
        val nodes = arrayListOf<String>()
        while (true) {
            nodes += (if (head.value === node) "HEAD = " else "") +
                    (if (tail.value === node) "TAIL = " else "") +
                    "<${node.element}" +
                    (if (node.extractedOrRemoved) ", extractedOrRemoved" else "") +
                    ">"
            // Process the next node.
            node = node.next.value ?: break
        }
        return nodes.joinToString(", ")
    }

    private inner class Node(
        var element: E?
    ) {
        val next = atomic<Node?>(null)

        private val _extractedOrRemoved = atomic(false)
        val extractedOrRemoved
            get() = _extractedOrRemoved.value

        fun markExtractedOrRemoved(): Boolean = _extractedOrRemoved.compareAndSet(expect = false, update = true)

        fun remove(): Boolean {
            val result = markExtractedOrRemoved()

            val curNext = next.value
            if (curNext == null) {
                return result
            }

            var curPrev = findPrev()
            if (curPrev == null) {
                return result
            }

            curPrev.next.value = curNext

            if (curPrev.extractedOrRemoved) curPrev.remove()
            if (curNext.extractedOrRemoved) curNext.remove()

            return result
        }

        fun findPrev(): Node? {
            var curPrev = head.value
            while (true) {
                val n = curPrev.next.value
                if (n == this) {
                    break
                }
                if (n == null) {
                    return null
                }
                curPrev = n
            }
            return curPrev
        }
    }
}
