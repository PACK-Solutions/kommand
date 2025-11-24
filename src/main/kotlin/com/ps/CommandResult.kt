package com.ps

import com.github.michaelbull.result.Result

data class CommandResult<R>(val result: Result<R, CommandError>, val events: List<DomainEvent> = emptyList())
