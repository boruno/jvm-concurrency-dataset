@file:Suppress("DuplicatedCode", "FoldInitializerAndIfToElvis")

//package day2

import kotlinx.atomicfu.*

class MSQueueWithConstantTimeRemove<E> : QueueWithRemove<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(element = null, prev = null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            val currentTail = tail.value
            val newNode = Node(element, currentTail)

            if (currentTail.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(currentTail, newNode)
                if (currentTail.extractedOrRemoved) currentTail.removePhysically()
                return
            } else {
                tail.compareAndSet(currentTail, currentTail.next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        // TODO: After moving the `head` pointer forward,
        // TODO: mark the node that contains the extracting
        // TODO: element as "extracted or removed", restarting
        // TODO: the operation if this node has already been removed.
        while (true) {
            val currentHead = head.value
            val currentNext = currentHead.next.value ?: return null

            currentNext.prev.value = null

            if (head.compareAndSet(currentHead, currentNext)) {
                //currentNext.prev.value = null
                //head.value.prev.value = null
                if (currentNext.markExtractedOrRemoved()) return currentNext.element
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
    override fun validate() {
        check(head.value.prev.value == null) {
            "`head.prev` must be null"
        }
        check(tail.value.next.value == null) {
            "tail.next must be null"
        }
        // Traverse the linked list
        var node = head.value
        while (true) {
            if (node !== head.value && node !== tail.value) {
                check(!node.extractedOrRemoved) {
                    "Removed node with element ${node.element} found in the middle of the queue"
                }
            }
            val nodeNext = node.next.value
            // Is this the end of the linked list?
            if (nodeNext == null) break
            // Is next.prev points to the current node?
            val nodeNextPrev = nodeNext.prev.value
            check(nodeNextPrev != null) {
                "The `prev` pointer of node with element ${nodeNext.element} is `null`, while the node is in the middle of the queue"
            }
            check(nodeNextPrev == node) {
                "node.next.prev != node; `node` contains ${node.element}, `node.next` contains ${nodeNext.element}"
            }
            // Process the next node.
            node = nodeNext
        }
    }

    private class Node<E>(
        var element: E?,
        prev: Node<E>?
    ) {
        val next = atomic<Node<E>?>(null)
        val prev = atomic(prev)

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
            val isMarked = markExtractedOrRemoved()

            removePhysically()

            return isMarked
        }


        fun removePhysically() {
            //while (true) {
            val curNext = next.value
            val curPrev = prev.value

            if (curNext == null) return
            if (curPrev == null) return // not null

//            this.prev.value = null
//            this.next.value = null

//            if (curNext.prev.compareAndSet(this, curPrev)) {
//                curPrev.next.value = curNext
//
////
//
//            } else {
//                continue
//            }


//                curNext.prev.value = curPrev
//                curPrev.next.value = curNext

            if (curNext.extractedOrRemoved) {
                curNext.remove()
            }
            if (curPrev.extractedOrRemoved) {
                curPrev.remove()
            }


        }
    }
}