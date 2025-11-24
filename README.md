Kommand
======

A tiny, dependency‑light Command Bus for Kotlin/JVM. It helps you model application behavior as Commands handled by dedicated Handlers, with optional Middleware
to intercept, log, validate, or transform execution. Results are returned in a simple wrapper so your application code stays explicit and testable.

What this library is for

- Encapsulate actions as Command objects with a single handler each
- Centralize dispatching through a CommandBus
- Compose cross‑cutting concerns using Middleware (e.g., logging, metrics, tracing, validation)

Highlights

- Minimal surface: Command, CommandHandler, CommandBus (with SimpleCommandBus implementation), CommandMiddleware, DomainEvent, CommandResult
- Straightforward generics for typed results
- No reflection, no magic — just Kotlin

Tech stack

- Language: Kotlin (JVM)
- Build: Gradle (Wrapper configured)
- Static analysis: Detekt (with formatting rules)
- Utilities: kotlin-result (for ergonomic success/error handling)
- Toolchain: Java 21 (configured via Gradle toolchains; Foojay resolver plugin)

Getting started

1) Add dependency
   This project is currently set up as a Gradle Kotlin project. If you consume it as a module in a multi‑project build, add it as a dependency in your app
   module:

```kotlin
dependencies {
    implementation(project(":kommand"))
}
```

   If you publish it to your internal repository, the coordinates are based on the current build file:
   group: com.ps
   name: kommand
   version: 1.0-SNAPSHOT

2) Define your Command and Handler

```kotlin
// A command returning a String
data class Greet(val name: String) : Command<String>

class GreetHandler : CommandHandler<Greet, CommandResult<String>> {
    override fun handle(command: Greet): CommandResult<String> =
        CommandResult.success("Hello, ${'$'}{command.name}!")
}
```

3) Optionally add Middleware

```kotlin
class LoggingMiddleware : CommandMiddleware {
    override fun <R> invoke(
        command: Command<R>,
        next: (Command<R>) -> CommandResult<R>
    ): CommandResult<R> {
        println("Executing command: ${'$'}command")
        return next(command)
    }
}
```

4) Create the bus and execute

```kotlin
val bus = SimpleCommandBus(
    handlers = mapOf(
        Greet("unused-key") to GreetHandler() // see note below on handler lookup
    ),
    middlewares = listOf(LoggingMiddleware())
)

val message: String = bus.execute(Greet("World"))
println(message) // -> Hello, World!
```

Build

- Using the Gradle wrapper: ./gradlew build (ensure the wrapper is executable on your system)
- Kotlin/JVM toolchain is pinned to Java 21 via Gradle toolchains; the Foojay resolver plugin helps locate/install matching JDKs automatically.

Why Kommand?

- Keep business logic explicit and decoupled
- Make cross‑cutting concerns composable with middleware
- Encourage testable, intention‑revealing code
