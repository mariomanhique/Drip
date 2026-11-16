package com.mariomanhique.drip

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform