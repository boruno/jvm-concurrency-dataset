//package mpp.stackWithElimination
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.rmi.server.ExportException
import java.util.Random
//author: Nikita Shemyakin

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)
    private val IT = 10
    val random = Random()
    private val T = Cell<Any?>(null, true);
    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val i = random.nextInt(ELIMINATION_ARRAY_SIZE)//(0 until ELIMINATION_ARRAY_SIZE).random()
        val c:Cell<Any?> = Cell(x, true);
        for (it in 0..IT) {
            val neibor:Int = (i + it) % ELIMINATION_ARRAY_SIZE;
            if (eliminationArray[neibor].compareAndSet(null, c)) {
                for (ii in 0..IT*10) {
                    if (eliminationArray[neibor].compareAndSet(T, null)) {
                        return
                    }
                }
                if (eliminationArray[neibor].compareAndSet(c, null)) {
                    forcePush(x)
                    return
                } else {
                    if (!eliminationArray[neibor].compareAndSet(T, null)) {
                        throw java.lang.IllegalStateException("SIGMAR CALLS");
                    }
                    return
                }
            }
        }
        forcePush(x)
    }
    private fun forcePush(x: E) {
        while (true) {
            val curTop = top.value
            val newNode = Node<E>(x, curTop)
            if (top.compareAndSet(curTop, newNode)) {
                break;
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val i = random.nextInt(ELIMINATION_ARRAY_SIZE)//(0 until ELIMINATION_ARRAY_SIZE).random()
        for (it in 0..IT) {
            val neibor:Int = (i + it) % ELIMINATION_ARRAY_SIZE;
            val c:Any? = eliminationArray[neibor].value;
            if (c != null) {
                val c1:Cell<Any?> = c as Cell<Any?>;
                if (c1.flag && eliminationArray[neibor].compareAndSet(c1, T)) {
                    return c1.x as E;
                }
            }

        }
        return forcePop()
    }
    private fun forcePop(): E?{
        while(true) {
            val curTop = top.value ?: return null
            val newTop = curTop.next
            if (top.compareAndSet(curTop, newTop)){
                return curTop.x
            }
        }
    }
}

private class Cell<E>(val x: E, val flag: Boolean)

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 20 // DO NOT CHANGE IT