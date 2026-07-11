package com.damoim.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DamoimServerApplication

fun main(args: Array<String>) {
    runApplication<DamoimServerApplication>(*args)
}
