@file:Suppress("FoldInitializerAndIfToElvis", "DuplicatedCode")

package day2

import java.util.concurrent.atomic.*

class MSQueueWithLinearTimeNonParallelRemove<E> : QueueWithRemove<E> {
    private val head: AtomicReference<Node>
    private val tail: AtomicReference<Node>

    init {
        val dummy = Node(null)
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.get()
            val newTail = Node(element)
            if (curTail.extractedOrRemoved) {
//                curTail.element = element
//                if (curTail.unMarkExtractedOrRemoved()) {
//                    return
//                }

//                //curTail.markExtractedOrRemoved()
//                val prev = curTail.findPrev()
//                if (prev != null) {
//                    tail.compareAndSet(curTail, prev)
//                }
//                continue

//                val prev = curTail.findPrev()
//                if (prev != null) {
//                    if (prev.next.compareAndSet(curTail, newTail)) {
//                        tail.compareAndSet(curTail, newTail)
//                        return
//                    }
//                }

            }
            if (curTail.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(curTail, newTail)
                return
            } else {
                tail.compareAndSet(curTail, curTail.next.get())
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head.get()
            val newHead = curHead.next.get()
            if (newHead == null) {
                return null
            }
            if (head.compareAndSet(curHead, newHead) && newHead.markExtractedOrRemoved()) {
                val result = newHead.element
                newHead.element = null
                return result
            }
        }
    }

    override fun remove(element: E): Boolean {
        // Traverse the linked list, searching the specified
        // element. Try to remove the corresponding node if found.
        // DO NOT CHANGE THIS CODE.
        var node = head.get()
        while (true) {
            val next = node.next.get()
            if (next == null) return false
            node = next
            if (node.element == element && node.remove()) return true
        }
    }

    /**
     * This is an internal function for tests.
     * DO NOT CHANGE THIS CODE.
     */
    override fun validate() {
        check(tail.get().next.get() == null) {
            "tail.next must be null"
        }
        var node = head.get()
        // Traverse the linked list
        while (true) {
            if (node !== head.get() && node !== tail.get()) {
                check(!node.extractedOrRemoved) {
                    "Removed node with element ${node.element} found in the middle of this queue"
                }
            }
            node = node.next.get() ?: break
        }
    }

    // TODO: Node is an inner class for accessing `head` in `remove()`
    private inner class Node(
        var element: E?
    ) {
        val next = AtomicReference<Node?>(null)

        /**
         * TODO: Both [dequeue] and [remove] should mark
         * TODO: nodes as "extracted or removed".
         */
        private val _extractedOrRemoved = AtomicBoolean(false)
        val extractedOrRemoved
            get() =
                _extractedOrRemoved.get()

        fun markExtractedOrRemoved(): Boolean =
            _extractedOrRemoved.compareAndSet(false, true)

        fun unMarkExtractedOrRemoved(): Boolean =
            _extractedOrRemoved.compareAndSet(true, false)

        fun findPrev(): Node? {
            var elem = head.get()
            var prev: Node? = null
            while (elem != null) {
                val prevNext = elem.next.get()
                if (prevNext == this) {
                    prev = elem
                    break
                }
                elem = prevNext
            }
            return prev
        }

        /**
         * Removes this node from the queue structure.
         * Returns `true` if this node was successfully
         * removed, or `false` if it has already been
         * removed by [remove] or extracted by [dequeue].
         */
        fun remove(): Boolean {
            if (markExtractedOrRemoved()) {
                val next = next.get()
                if (next == null) {
                    return true
                }
                val prev = findPrev()
                if (prev != null) {
                    prev.next.compareAndSet(this, next)
                }
                return true
            }
            return false

            // TODO: The removal procedure is split into two phases.
            // TODO: First, you need to mark the node as "extracted or removed".
            // TODO: On success, this node is logically removed, and the
            // TODO: operation should return `true` at the end.
            // TODO: In the second phase, the node should be removed
            // TODO: physically, updating the `next` field of the previous
            // TODO: node to `this.next.value`.
            // TODO: Do not remove `head` and `tail` physically to make
            // TODO: the algorithm simpler. In case a tail node is logically removed,
            // TODO: it will be removed physically by `enqueue(..)`.
        }
    }
}
