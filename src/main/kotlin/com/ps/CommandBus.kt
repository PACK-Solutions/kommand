package com.ps

interface CommandBus {
    fun <R> execute(command: Command<R>): R
}
