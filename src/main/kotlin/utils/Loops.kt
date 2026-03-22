package org.fancryer.utils

fun loop2(ar:IntRange,br:IntRange,loop:(Int,Int)->Unit)
{
    for(a in ar) for(b in br) loop(a,b)
}

fun loop3(ar:IntRange,br:IntRange,cr:IntRange,loop:(Int,Int,Int)->Unit)
{
    for(a in ar) loop2(br,cr,{b,c-> loop(a,b,c)})
}