package com.ps

interface CommandMiddleware {
    fun <R> invoke(command: Command<R>, next: (Command<R>) -> CommandResult<R>): CommandResult<R>
}
