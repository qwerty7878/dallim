package com.dallim.plugins

import com.dallim.auth.authModule
import com.dallim.common.HttpClientFactory
import com.dallim.dallimbook.dallimbookModule
import com.dallim.route.routeModule
import com.dallim.run.runModule
import com.dallim.user.userModule
import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import javax.sql.DataSource

/**
 * Wires the shared core beans (config/DataSource/Exposed Database/Redis/HttpClient) plus each
 * domain's Koin module. Domain modules are defined next to their entities
 * (com.dallim.auth.authModule, com.dallim.user.userModule, ...) so backend-dev adds
 * services/repositories there rather than growing this file.
 */
fun Application.configureDependencyInjection(config: DallimConfig, dataSource: DataSource, database: Database) {
    val coreModule = module {
        single { config }
        single { dataSource }
        single { database }
        single { RedisFactory.client(config.redis) }
        single { RedisFactory.connection(get()) }
        single { HttpClientFactory.create() }
    }

    install(Koin) {
        slf4jLogger()
        modules(coreModule, authModule, userModule, routeModule, runModule, dallimbookModule)
    }
}
