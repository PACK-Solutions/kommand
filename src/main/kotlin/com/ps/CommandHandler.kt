package com.ps

interface CommandHandler<C : Command<R>, R> {
    fun handle(command: C): CommandResult<R>
}
