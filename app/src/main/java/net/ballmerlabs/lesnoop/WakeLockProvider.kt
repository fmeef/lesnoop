package net.ballmerlabs.lesnoop

interface WakeLockProvider {
    fun hold(): Int
    fun release(): Int
    fun releaseAll()
}