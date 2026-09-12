package com.dallim.social

import org.koin.dsl.module

/** S-35 팀 채팅 Koin module. SocialSessionChatHub는 상태(커넥션 맵)를 들고 있는 싱글턴이라
 * `single { }`으로 앱 생명주기 동안 하나만 존재해야 한다. */
val socialSessionChatModule = module {
    single { SocialSessionChatHub() }
    single { SocialSessionChatRepository(get()) }
    single { SocialSessionChatService(get(), get(), get(), get()) }
}
