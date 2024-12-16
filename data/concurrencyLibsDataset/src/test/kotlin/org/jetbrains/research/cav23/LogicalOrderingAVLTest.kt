package org.jetbrains.research.cav23

import org.jetbrains.research.cav23.logicalOrderingAVL.LogicalOrderingAVL

class LogicalOrderingAVLTest : IntIntAbstractConcurrentMapTest<LogicalOrderingAVL<Int, Int>>(LogicalOrderingAVL())
