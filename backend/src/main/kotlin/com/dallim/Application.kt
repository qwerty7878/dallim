package com.dallim

import com.dallim.plugins.Databases
import com.dallim.plugins.configureCors
import com.dallim.plugins.configureDependencyInjection
import com.dallim.plugins.configureRouting
import com.dallim.plugins.configureSecurity
import com.dallim.plugins.configureSerialization
import com.dallim.plugins.configureStatusPages
import com.dallim.plugins.loadDallimConfig
import com.dallim.run.FinisherCountSync
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.netty.EngineMain
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.ktor.ext.get

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val config = loadDallimConfig()

    val dataSource = Databases.dataSource(config.database)
    Databases.migrate(dataSource)
    val database = Databases.connect(dataSource)

    configureSerialization()
    configureStatusPages()
    configureCors()
    configureSecurity(config)
    configureDependencyInjection(config, dataSource, database)
    configureRouting()

    startFinisherCountSyncJob()
}

/**
 * Periodic background flush of Route.finisherCount (docs/01-feature-spec.md 2.2.D Redis INCR ->
 * batch DB sync, and 2.3's note that Ktor has no Spring `@Scheduled` — a plain coroutine loop is
 * the documented approach). Runs on the Application's own coroutine scope, so it's cancelled
 * automatically on shutdown.
 */
private fun Application.startFinisherCountSyncJob(intervalMillis: Long = 30_000) {
    val finisherCountSync = get<FinisherCountSync>()
    launch {
        while (isActive) {
            delay(intervalMillis)
            runCatching { finisherCountSync.flushToDatabase() }
                .onFailure { log.error("finisher count sync failed", it) }
        }
    }
}
