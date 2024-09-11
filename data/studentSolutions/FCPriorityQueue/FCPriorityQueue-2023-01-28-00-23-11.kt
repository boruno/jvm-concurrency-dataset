import java.util.*
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.atomic
import kotlin.random.Random

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val loc = atomic(false)
    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    private val sz = 15
    private val ar = atomicArrayOfNulls<TOD<E>>(sz)
    class TOD<E> (val todo:() -> E?){
        var flag = true
        var ans : E? = null
    }

    fun poll(): E? {
        val pol = TOD {q.poll()}
        helpic(pol)
        return pol.ans
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        val pek = TOD {q.peek()}
        helpic(pek)
        return pek.ans
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        helpic(TOD {q.add(element); null})
    }

    fun makeLoc() : Boolean {
        return (!loc.value && loc.compareAndSet(false, true))
    }

    fun helpic(todo: TOD<E>): E? {
        var idx = Random.nextInt(0, sz)
        while(!ar[idx].compareAndSet(null, todo))
            idx = (idx + 1) % sz
        while(true) {
            if (makeLoc()) {
                (0 until sz)
                    .forEach { j ->
                        run {
                            val req = ar[j].value
                            if (req != null) {
                                req.ans= req.todo.invoke()
                                req.flag = true
                                ar[j].compareAndSet(req, null)
                            }
                        }
                    }
                loc.value = false
                return todo.ans
            }
            else if (todo.flag) return todo.ans
        }
    }
}