package com.dallim.moderation

import org.koin.dsl.module

val moderationModule = module {
    single { ReportTriageQueue(get()) }
}
