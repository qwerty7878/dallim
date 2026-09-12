package com.dallim

import com.dallim.plugins.Databases
import com.dallim.plugins.configureCors
import com.dallim.plugins.configureDependencyInjection
import com.dallim.plugins.configureRouting
import com.dallim.plugins.configureSecurity
import com.dallim.plugins.configureSerialization
import com.dallim.plugins.configureStatusPages
import com.dallim.plugins.configureWebSockets
import com.dallim.plugins.loadDallimConfig
import com.dallim.route.RouteStatusUpdateJob
import com.dallim.route.untilNext3AmMillis
import com.dallim.run.FinisherCountSync
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.log
import io.ktor.server.netty.EngineMain
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.ktor.ext.get
import java.time.Duration

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
    configureWebSockets()
    configureSecurity(config)
    configureDependencyInjection(config, dataSource, database)
    configureRouting()

    // Release the HikariCP pool (and Redis client) on shutdown — previously missing, which is
    // harmless for a single long-lived prod process but leaks Postgres connections across every
    // `testApplication { }` block in the integration test suite (each one runs this same
    // module()). With enough integration tests in one JVM run, the leaked connections eventually
    // exceed Postgres's max_connections and unrelated later tests start failing with
    // HikariPool$PoolInitializationException — see src/test/resources/application-test.conf's
    // long-standing NOTE about this exact gap. Also applies in prod for a graceful redeploy.
    environment.monitor.subscribe(ApplicationStopping) {
        runCatching { (dataSource as? java.io.Closeable)?.close() }
            .onFailure { log.warn("failed to close dataSource on shutdown", it) }
    }

    startFinisherCountSyncJob()
    startRouteStatusUpdateJob()
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

/**
 * Daily DISCOVERY->VERIFIED->POPULAR route status batch (docs/01-feature-spec.md 2.3
 * RouteStatusUpdateJob, "매일 03:00"). [initialDelayMillis] defaults to the real wait until the
 * next 03:00 (see [untilNext3AmMillis]) but is an overridable parameter precisely so a local run
 * or a future test harness can pass 0 (or any short delay) to trigger the first pass immediately
 * instead of waiting for the clock — [RouteStatusUpdateJob.runOnce] itself has no timing logic at
 * all, so it can also just be called directly, bypassing this loop entirely.
 */
private fun Application.startRouteStatusUpdateJob(
    initialDelayMillis: Long = untilNext3AmMillis(),
    intervalMillis: Long = Duration.ofHours(24).toMillis(),
) {
    val routeStatusUpdateJob = get<RouteStatusUpdateJob>()
    launch {
        delay(initialDelayMillis)
        while (isActive) {
            runCatching { routeStatusUpdateJob.runOnce() }
                .onFailure { log.error("route status update job failed", it) }
            delay(intervalMillis)
        }
    }
}
