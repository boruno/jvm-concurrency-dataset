@file:Suppress("FoldInitializerAndIfToElvis", "DuplicatedCode")

package day3

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
        val node = Node(element)
        while (true) {
            val curTail = tail.value
            if (curTail.extractedOrRemoved) {
                curTail.removePhysically()
            }
            if (curTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail, node)
                return
            }
            tail.compareAndSet(curTail, curTail.next.value!!)
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            val curHeadNext = curHead.next.value ?: return null
            if (head.compareAndSet(curHead, curHeadNext) && curHeadNext.markExtractedOrRemoved()) {
                return curHeadNext.element
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
        check(tail.value.next.value == null) {
            "tail.next must be null"
        }
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

    private fun findPrevious(node: Node): Node? {
        var curr = head.value
        while (true) {
            val next = curr.next.value
            if (next == null) {
                return null
            } else if (next == node) {
                return curr
            } else {
                curr = next
            }
        }
    }

    private inner class Node(
        var element: E?
    ) {
        val next = atomic<Node?>(null)

        private val _extractedOrRemoved = atomic(false)
        val extractedOrRemoved get() = _extractedOrRemoved.value

        fun markExtractedOrRemoved(): Boolean = _extractedOrRemoved.compareAndSet(false, true)

        /**
         * Removes this node from the queue structure.
         * Returns `true` if this node was successfully
         * removed, or `false` if it has already been
         * removed by [remove] or extracted by [dequeue].
         */
        fun remove(): Boolean {
            if (!markExtractedOrRemoved()) {
                return false
            }
            return removePhysically()
        }

        fun removePhysically(): Boolean {
            val curPrev = findPrevious(this)
            val curNext = next.value

            if (curPrev != null) {
                val curPrevNext = curPrev.next.value
                curPrev.next.compareAndSet(curPrevNext, curNext)

                if (curPrev.extractedOrRemoved) {
                    curPrev.removePhysically()
                }
            }

            if (curNext != null && curNext.extractedOrRemoved) {
                curNext.removePhysically()
            }

            return true
        }
    }
}
