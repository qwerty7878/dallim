package com.dallim.social

import org.koin.dsl.module

val socialSessionCheckinModule = module {
    single { SocialSessionCheckinRepository(get()) }
    single { SocialSessionCheckinService(get(), get()) }
}
