package com.dallim.social

import org.koin.dsl.module

/** S-38 평가 / S-39 Running Mate Koin module. */
val socialSessionFeedbackModule = module {
    single { SocialSessionFeedbackRepository(get()) }
    single { RunningMateRepository(get()) }
    single { SocialSessionFeedbackService(get(), get(), get(), get(), get()) }
    single { RunningMateService(get(), get()) }
}
