package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.lang.management.ThreadInfo
import java.util.concurrent.ThreadLocalRandom

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val node = Node(x, top.value)
        if (top.compareAndSet(node.next, node)) {
            return
        } else {
            val random = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)

            println("push $x")
            
            var him = eliminationArray[random % ELIMINATION_ARRAY_SIZE].value
            while (!eliminationArray[random % ELIMINATION_ARRAY_SIZE].compareAndSet(him, x)) {
                him = eliminationArray[random % ELIMINATION_ARRAY_SIZE].value
            }

            for (i in 0 until ELIMINATION_ARRAY_SIZE) {
                println((eliminationArray[i].value))
            }
            // print(top.value?.x)
            // println(him)

            if (him != null) {
                while (true) {
                    val node2 = Node(him as E, top.value)
                    if (top.compareAndSet(node2.next, node2)) {
                        break
                    }
                }
            }

            //println(eliminationArray[random].value as E)
            //println(eliminationArray[random].value)

            /*
            for (i in 0..100) {
                if (eliminationArray[random].value as E == null) {
                    return
                }
            }

            eliminationArray[random].compareAndSet(x, null)

            while (true) {
                val node2 = Node(x, top.value)
                if (top.compareAndSet(node2.next, node2)) {
                    break
                }
            }
            */
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        /*val node = top.value
        if (top.compareAndSet(node, node!!.next)) {
            return node.x
        } else {*/
            while (true) {
                val random = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)

                for (i in 0 until ELIMINATION_ARRAY_SIZE) {
                    var him = eliminationArray[(random + i) % ELIMINATION_ARRAY_SIZE].value
                    while (!eliminationArray[(random + i) % ELIMINATION_ARRAY_SIZE].compareAndSet(him, null)) {
                        him = eliminationArray[(random + i) % ELIMINATION_ARRAY_SIZE].value
                    }

                    if (him != null) {
                        // println(him as E)
                        return (him as E)
                    }
                }

                while (true) {
                    val node2 = top.value ?: return null
                    if (top.compareAndSet(node2, node2.next)) {
                        val temp = node2.x
                        // println("pop $temp")
                        return node2.x
                    }
                }

            }

       // }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT