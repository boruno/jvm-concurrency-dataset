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
        for (i in 0..100) {
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
        var staticIndex: Int? = null
        while (true){
            if(tryLocked()){
                if (staticIndex != null) {
                    val res = array[staticIndex].value
                    array[staticIndex].compareAndSet(res, null)
                }
                val ans = q.poll()
                helpOtherFlow()
                unLock()
                return ans
            }
            else if (staticIndex == null) {
                while(true) {
                    val index = ThreadLocalRandom.current().nextInt(99)
                    if (array[index].compareAndSet(null, Pair("poll", null))) {
                        staticIndex = index
                        break
                    }
                }
            }
            else {
                val res = array[staticIndex].value
                if (res!!.first == DONE){
                    array[staticIndex].compareAndSet(res, null)
                    return res.second
                }
            }
        }
    }


    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        var staticIndex: Int? = null
        while (true){
            if(tryLocked()){
                if (staticIndex != null) {
                    val res = array[staticIndex].value
                    array[staticIndex].compareAndSet(res, null)
                }
                val ans = q.peek()
                helpOtherFlow()
                unLock()
                return ans
            }
            else if (staticIndex == null) {
                while(true) {
                    val index = ThreadLocalRandom.current().nextInt(99)
                    if (array[index].compareAndSet(null, Pair("peek", null))) {
                        staticIndex = index
                        break
                    }
                }
            }
            else {
                val res = array[staticIndex].value
                if (res!!.first == DONE){
                    array[staticIndex].compareAndSet(res, null)
                    return res.second
                }
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        var staticIndex: Int? = null
        while (true){
            if(tryLocked()){
                if (staticIndex != null) {
                    val res = array[staticIndex].value
                    array[staticIndex].compareAndSet(res, null)
                }
                q.add(element)
                helpOtherFlow()
                unLock()
            }
            else if (staticIndex == null) {
                while(true) {
                    val index = ThreadLocalRandom.current().nextInt(99)
                    if (array[index].compareAndSet(null, Pair("peek", null))) {
                        staticIndex = index
                        break
                    }
                }
            }
            else {
                val res = array[staticIndex].value
                if (res!!.first == DONE){
                    array[staticIndex].compareAndSet(res, null)
                    return
                }
            }
        }
    }
}