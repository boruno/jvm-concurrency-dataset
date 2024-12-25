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
            val curTail = tail.value
            val newItem = Node(element, curTail)
            // add the new item to the queue
            if (curTail.next.compareAndSet(null, newItem)) {
                // make the tail point to the new item
                tail.compareAndSet(curTail, newItem)
                // remove physically if needed
                if (curTail.extractedOrRemoved) curTail.removePhysically()
                return
            } else {
                // help the other threads to progress
                // tail should not have next item, so it is ok to fix it
                val curTailNext = curTail.next.value
                if (curTailNext != null) {
                    tail.compareAndSet(curTail, curTailNext)
                }
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            // head is always dummy
            val dummy = head.value
            // it is the actual head
            val actualHead = dummy.next.value ?: return null
            // the actual head becomes dummy
            if (head.compareAndSet(dummy, actualHead)) {
                actualHead.prev.value = null
                if (actualHead.markExtractedOrRemoved()) {
                    return actualHead.element
                }
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
            if (!markExtractedOrRemoved()) return false

            removePhysically()

            return true
        }

        fun removePhysically() {
            val curNext = next.value
            // it is the tail
            if (curNext == null) return

            val curPrev = prev.value
            // already dequed
            if (curPrev == null) return

            curNext.prev.value = curPrev//.compareAndSet(this, curPrev)
            curPrev.next.value = curNext//compareAndSet(this, curNext)
            /*curPrev.next.set(curNext)
            curNext.prev.setIfNotNull(curPrev)*/
            if (curNext.extractedOrRemoved) curNext.removePhysically()
            if (curPrev.extractedOrRemoved) curPrev.removePhysically()
        }
    }
}