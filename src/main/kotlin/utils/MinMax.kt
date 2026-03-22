package org.fancryer.utils

@JvmInline
value class MinMax<T>(val minMax:Pair<T,T>)
{
    operator fun component1()=minMax.first
    operator fun component2()=minMax.second
}