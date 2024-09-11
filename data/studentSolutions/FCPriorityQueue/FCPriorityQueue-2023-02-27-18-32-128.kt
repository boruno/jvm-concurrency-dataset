import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean

var locked = AtomicBoolean(false)
var DONE = "Done"
class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val array = atomicArrayOfNulls<Pair<String, E?>>(100)

    private fun tryLocked(): Boolean{
        return locked.compareAndSet(false, true)
    }
    private fun unLock(){
        locked = AtomicBoolean(false)
    }

    private fun helpOtherFlow() {
        for (i in 0..99) {
            if (array[i].value != null) {
                if (array[i].value!!.first == "poll") {
                    array[i].value = Pair(DONE, q.poll())
                }
                if (array[i].value!!.first == "peek") {
                    array[i].value = Pair(DONE, q.peek())
                }
                if (array[i].value!!.first == "add") {
                    q.add(array[i].value!!.second)
                    array[i].value = Pair(DONE, null)
                }
            }
        }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        while (true){
            if(tryLocked()){
                val ans = q.poll()
                helpOtherFlow()
                unLock()
                return ans
            }
            else {
                var staticIndex: Int? = null
                for (i in 0..99)  {
                    val index = ThreadLocalRandom.current().nextInt(100)
                    if (array[index].compareAndSet(null, Pair("poll", null))) {
                        for (j in 0..99) {
                            if(array[index].value!!.first == DONE){
                                val res = array[index].value
                                array[index].compareAndSet(res, null)
                                return res!!.second
                            }
                        }
                        staticIndex = index
                    }
                }
                val res = array[staticIndex!!].value
                array[staticIndex].compareAndSet(res, null)
            }
        }
    }


    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        while (true){
            if(tryLocked()){
                val ans = q.peek()
                helpOtherFlow()
                unLock()
                return ans
            }
            else {
                var staticIndex: Int? = null
                for (i in 0..99)  {
                    val index = ThreadLocalRandom.current().nextInt(100)
                    if (array[index].compareAndSet(null, Pair("peek", null))) {
                        for (j in 0..99) {
                            if(array[index].value!!.first == DONE){
                                val res = array[index].value
                                array[index].compareAndSet(res, null)
                                return res!!.second
                            }
                        }
                        staticIndex = index
                    }
                }
                val res = array[staticIndex!!].value
                array[staticIndex].compareAndSet(res, null)
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        while (true){
            if(tryLocked()){
                q.add(element)
                helpOtherFlow()
                unLock()
            }
            else {
                var staticIndex: Int? = null
                for (i in 0..99)  {
                    val index = ThreadLocalRandom.current().nextInt(100)
                    if (array[index].compareAndSet(null, Pair("peek", null))) {
                        for (j in 0..99) {
                            if(array[index].value!!.first == DONE){
                                val res = array[index].value
                                array[index].compareAndSet(res, null)
                            }
                        }
                        staticIndex = index
                    }
                }
                val res = array[staticIndex!!].value
                array[staticIndex].compareAndSet(res, null)
            }
        }
    }
}