package com.dallim.plugins

import com.dallim.route.SavedRouteTable
import com.dallim.route.SketchRouteTable
import com.dallim.run.GpsPointTable
import com.dallim.run.RunRecordTable
import com.dallim.user.UserTable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import javax.sql.DataSource

/**
 * HikariCP DataSource -> Flyway migrate -> Exposed Database.connect wiring.
 *
 * Table creation is owned by Flyway migrations (src/main/resources/db/migration), NOT by
 * Exposed's SchemaUtils.create — the migration SQL is the source of truth because it also
 * creates the PostGIS extension and the geography columns that Exposed can't express
 * (see com.dallim.common.PostGis). SchemaUtils is only used here in a dev-time sanity check,
 * disabled by default.
 */
object Databases {

    fun dataSource(config: DallimConfig.DatabaseSettings): DataSource {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.user
            password = config.password
            maximumPoolSize = config.maxPoolSize
            driverClassName = "org.postgresql.Driver"
        }
        return HikariDataSource(hikariConfig)
    }

    fun migrate(dataSource: DataSource) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    fun connect(dataSource: DataSource): Database = Database.connect(dataSource)

    /** All Exposed table objects — used only for local dev sanity checks, not for real DDL. */
    val allTables = arrayOf(
        UserTable,
        SketchRouteTable,
        SavedRouteTable,
        RunRecordTable,
        GpsPointTable,
    )

    fun devSanityCheck(database: Database) {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(*allTables)
        }
    }
}
