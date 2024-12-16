package org.jetbrains.research.cav23

import org.jctools.maps.NonBlockingHashMapLong

class NonBlockingHashMapLongTest : AbstractConcurrentMapTest<NonBlockingHashMapLong<Int>>(NonBlockingHashMapLong())
