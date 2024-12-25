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
        while (true) {
            val curTail = tail.value

            val node = Node(element)

            if (curTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail, node)
                curTail.removePhysicallyIfMarked()

                return
            } else {
                val next = curTail.next.value ?: continue
                tail.compareAndSet(curTail, next)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            val next = curHead.next
            val value = next.value ?: return null

            if (head.compareAndSet(curHead, value) /*&& value.markExtractedOrRemoved()*/) {
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
        check(tail.value.next.value == null) {
            "tail.next must be null"
        }
        var node = head.value
        // Traverse the linked list
        while (true) {
            if (node !== head.value && node !== tail.value) {
                check(!node.extractedOrRemoved) {
                    "Removed node with element ${node.element} found in the middle of this queue. Head: ${head.value.element}, tail: ${tail.value.element}"
                }
            }
            node = node.next.value ?: break
        }
    }

    private inner class Node(
        var element: E?
    ) {
        val next = atomic<Node?>(null)

        private val _extractedOrRemoved = atomic(false)
        val extractedOrRemoved get() = _extractedOrRemoved.value

        fun markExtractedOrRemoved(): Boolean = _extractedOrRemoved.compareAndSet(expect = false, update = true)

        fun removePhysicallyIfMarked() {
            if (!extractedOrRemoved) {
                return
            }

            val curPrev = findPrev() ?: return
            val curNext = next.value
            curPrev.next.value = curNext

            if (curPrev.extractedOrRemoved) {
                curPrev.remove()
            }

            if (curNext?.extractedOrRemoved == true) {
                curNext.remove()
            }
        }

        /**
         * Removes this node from the queue structure.
         * Returns `true` if this node was successfully
         * removed, or `false` if it has already been
         * removed by [remove] or extracted by [dequeue].
         */
        fun remove(): Boolean {
            val logicallyRemoved = markExtractedOrRemoved()

            if (next.value == null) {
                return logicallyRemoved
            }

            removePhysicallyIfMarked()

            return logicallyRemoved
        }
    }

    private fun Node.findPrev(): Node? {
        var node: Node = head.value

        while (true) {
            val next = node.next
            if (next.value == this) {
                break
            }
            node = next.value ?: return null
        }

        return node
    }
}
