@file:Suppress("FoldInitializerAndIfToElvis", "DuplicatedCode")

//package day2

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
        // TODO: When adding a new node, check whether
        // TODO: the previous tail is logically removed.
        // TODO: If so, remove it physically from the linked list.
        // TODO("Implement me!")

        while (true) {
            val newElement = Node(element)
            val next = tail.value.next
            val currentTail = tail

            if (next.compareAndSet(null, newElement)) {
                tail.compareAndSet(currentTail.value, newElement)
                if (currentTail.value.extractedOrRemoved) {
//                    tail.value.remove()

                    val beforeTail = findPrev(tail.value)
                    beforeTail.next.value = null
//                    if (tail.compareAndSet(this, beforeTail)) {
//                        tail.value.next.value = null
//                    }
                }
                return
            } else {
                tail.compareAndSet(currentTail.value, next.value!!)
            }
        }

    }

    override fun dequeue(): E? {
        // TODO: After moving the `head` pointer forward,
        // TODO: mark the node that contains the extracting
        // TODO: element as "extracted or removed", restarting
        // TODO: the operation if this node has already been removed.
//        TODO("Implement me!")
        while (true) {
            val currentHead = head.value
            val currentHeadNext = currentHead.next.value
            //todo ?
            if (currentHeadNext == null) return null
            if (head.compareAndSet(currentHead, currentHeadNext) && currentHeadNext.markExtractedOrRemoved()) {
                return currentHeadNext.element
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

    // TODO: Node is an inner class for accessing `head` in `remove()`
    inner class Node(
        var element: E?
    ) {
        val next = atomic<Node?>(null)

        /**
         * TODO: Both [dequeue] and [remove] should mark
         * TODO: nodes as "extracted or removed".
         */
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
//            TODO("Implement me!")

            val removed = markExtractedOrRemoved()

            val prev = findPrev(this)
            val prevNext = prev.next
//            assert(prevNext.value == this)

//            if (tail.compareAndSet(this, prev)) {
//                tail.value.next.value = null
//                return removed
//            }

//            prev.next.value = this.next.value
            prev.next.compareAndSet(prevNext.value, this.next.value)
            return removed
        }

    }
    fun findPrev(node: Node): Node {
        var current: Node? = head.value
        while (current != null) {
            if (current.next.value == node) return current

            if (current.next.value == tail.value) return current

            current = current.next.value
        }

        return head.value
    }

}
