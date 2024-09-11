import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<E>(size)

    init {
        for (i in 0 until size) {
            a[i].value = initialValue
        }
    }

    fun get(index: Int) =
        a[index].value!!

    fun cas(index: Int, expected: E, update: E) = a[index].compareAndSet(expected, update)


    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        if (a[index1].value != expected1 || a[index2].value != expected2) return false
        else{
            if(!a[index1].compareAndSet(expected1, update1))
                return false
            else
            {
                if(!a[index2].compareAndSet(expected2, update2))
                {
                    a[index1].compareAndSet(a[index1].value, expected1)
                    return false
                }
                else
                {
                    return true
                }
            }
        }
    }
}

//abstract class Descriptor{
//    abstract fun complete()
//}
//
//class CAS2Descriptor<E>(val A: Ref<E>, val B: Ref<E>, val expectA: E, val updateA: E, val expectB: E, val updateB: E): Descriptor(){
//    override fun complete() {
//        if(A.value == expectA){
//
//            if(B.value == expectB){
//
//            }
//            else{
//
//            }
//        }
//    }
//
//}
//
//class Ref<E>(val initial: E){
//    val _value: AtomicRef<Any?> = atomic(initial)
//
//    var value: E get(){
//        _value.loop { cur ->
//            when(cur) {
//                is Descriptor -> cur.complete()
//                else -> return cur as E
//            }
//        }
//    }
//        set(upd){
//            _value.loop { cur ->
//                when(cur) {
//                    is Descriptor -> cur.complete()
//                    else -> if(_value.compareAndSet(cur,upd))
//                        return
//                }
//        }
//}