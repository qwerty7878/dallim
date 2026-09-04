package com.dallim.push

/** Outcome of one FCM send attempt against a single token (docs/02-api-spec.md 10.2). */
sealed interface FcmSendResult {
    data object Success : FcmSendResult

    /** FCM reported the token as UNREGISTERED (the modern v1 API's single error code covering
     * the old "NOT_FOUND"/"InvalidRegistration"/"NotRegistered" cases too) -- caller must delete
     * the token, per 10.2. */
    data object TokenInvalid : FcmSendResult

    /** Any other failure (network, quota, auth, FCM disabled locally because no credentials file
     * was configured, ...). Logged and swallowed -- never blocks in-app notification creation. */
    data object OtherFailure : FcmSendResult
}

/**
 * Seam between [FcmPushService] and the real Firebase Admin SDK -- lets tests substitute a fake
 * without touching the network or requiring real Firebase credentials (same "fake the outer
 * client, hand-roll it as an open/interface type" style as com.dallim.common.OsrmClient in
 * com.dallim.discovery.DiscoveryServiceTest). Production wiring is [FirebaseFcmSender].
 */
interface FcmSender {
    fun send(token: String, title: String, body: String): FcmSendResult
}
