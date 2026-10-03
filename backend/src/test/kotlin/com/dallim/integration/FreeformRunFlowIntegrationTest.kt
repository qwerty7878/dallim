package com.dallim.integration

import com.dallim.common.ApiErrorBody
import com.dallim.route.RouteDetailResponse
import com.dallim.run.FeedbackTagsRequest
import com.dallim.run.FinishRunRequest
import com.dallim.run.GpsBatchRequest
import com.dallim.run.GpsPointRequest
import com.dallim.run.RegisterRouteRequest
import com.dallim.run.RegisterRouteResponse
import com.dallim.run.RunDetailResponse
import com.dallim.run.RunFinishResponse
import com.dallim.run.RunStartResponse
import com.dallim.run.RunStatus
import com.dallim.user.UserMeResponse
import com.dallim.testsupport.ApiTestSupport.authGet
import com.dallim.testsupport.ApiTestSupport.jsonClient
import com.dallim.testsupport.ApiTestSupport.signupNewUser
import com.dallim.testsupport.GpsFixtures
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 자유 러닝(routeId == null, 2026-09-26) 전 구간 통합 테스트 — 시작, 판정(judgeFreeform), 결과 조회,
 * "코스로 등록"(register-as-route)과 그 에러 코드들. 실제 로컬 Postgres/Redis 대상(mock 없음).
 */
class FreeformRunFlowIntegrationTest {

    @Serializable
    private data class StartEnv(val success: Boolean, val data: RunStartResponse? = null, val error: ApiErrorBody? = null)

    @Serializable
    private data class FinishEnv(val success: Boolean, val data: RunFinishResponse? = null, val error: ApiErrorBody? = null)

    @Serializable
    private data class DetailEnv(val success: Boolean, val data: RunDetailResponse? = null, val error: ApiErrorBody? = null)

    @Serializable
    private data class RegisterEnv(val success: Boolean, val data: RegisterRouteResponse? = null, val error: ApiErrorBody? = null)

    @Serializable
    private data class RouteEnv(val success: Boolean, val data: RouteDetailResponse? = null, val error: ApiErrorBody? = null)

    @Serializable
    private data class MeEnv(val success: Boolean, val data: UserMeResponse? = null, val error: ApiErrorBody? = null)

    @Serializable
    private data class ErrEnv(val success: Boolean, val error: ApiErrorBody? = null)

    private suspend fun start(client: HttpClient, token: String, routeId: String?): String {
        val routePart = if (routeId == null) "" else """"routeId":"$routeId","""
        val response = client.post("/v1/runs") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{$routePart"mode":"FREE","startedAt":"2026-08-30T00:00:00Z"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return requireNotNull(response.body<StartEnv>().data).runId
    }

    private suspend fun upload(client: HttpClient, token: String, runId: String, fixtureName: String) {
        val points = GpsFixtures.load(fixtureName).actual
        val response = client.post("/v1/runs/$runId/gps-batch") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(GpsBatchRequest(points.map { GpsPointRequest(it.lat, it.lng, it.timestamp) }))
        }
        assertEquals(HttpStatusCode.Accepted, response.status)
    }

    private suspend fun finish(client: HttpClient, token: String, runId: String): FinishEnv =
        client.post("/v1/runs/$runId/finish") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(FinishRunRequest(finishedAt = "2026-08-30T01:00:00Z"))
        }.body()

    private suspend fun register(client: HttpClient, token: String, runId: String, name: String, emoji: String) =
        client.post("/v1/runs/$runId/register-as-route") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(RegisterRouteRequest(name = name, emoji = emoji))
        }

    private suspend fun errorCode(response: io.ktor.client.statement.HttpResponse): String? =
        response.body<ErrEnv>().error?.code

    @Test
    fun `freeform run completes without a planned route and exposes null route fields`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val runId = start(client, token, null)
        // Even the "aborted" trace is COMPLETED in freeform: there is no target to fall short of.
        upload(client, token, runId, "aborted_run")
        val finished = finish(client, token, runId)
        assertEquals(RunStatus.COMPLETED, finished.data!!.status)
        assertEquals(0, finished.data.routeCompletionPercent)
        assertEquals(0, finished.data.sketchMatchPercent)

        val detail: DetailEnv = client.authGet("/v1/runs/$runId", token).body()
        assertNull(detail.data!!.routeId)
        assertNull(detail.data.routeName)
        assertNull(detail.data.plannedGeoJson)
        assertTrue(detail.data.actualGeoJson.coordinates.isNotEmpty())
        assertNull(detail.data.registeredRouteId)
    }

    @Test
    fun `freeform run with abnormal speed goes to UNDER_REVIEW and cannot be registered`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val runId = start(client, token, null)
        upload(client, token, runId, "under_review_run")
        assertEquals(RunStatus.UNDER_REVIEW, finish(client, token, runId).data!!.status)

        val response = register(client, token, runId, "코스", "🐳")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("RUN_NOT_COMPLETED", errorCode(response))
    }

    @Test
    fun `register-as-route creates a public route from the actual path and blocks duplicates`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val runId = start(client, token, null)
        upload(client, token, runId, "completed_run")
        finish(client, token, runId)

        val response = register(client, token, runId, "내 고래", "🐳")
        assertEquals(HttpStatusCode.Created, response.status)
        val newRouteId = requireNotNull(response.body<RegisterEnv>().data).routeId

        val route: RouteEnv = client.authGet("/v1/routes/$newRouteId", token).body()
        assertEquals("내 고래", route.data!!.name)
        assertTrue(route.data.geoJson.coordinates.isNotEmpty())

        val detail: DetailEnv = client.authGet("/v1/runs/$runId", token).body()
        assertEquals(newRouteId, detail.data!!.registeredRouteId)

        val dup = register(client, token, runId, "또 등록", "🐳")
        assertEquals(HttpStatusCode.Conflict, dup.status)
        assertEquals("ROUTE_ALREADY_REGISTERED", errorCode(dup))
    }

    @Test
    fun `register-as-route validates name and emoji`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val runId = start(client, token, null)
        upload(client, token, runId, "completed_run")
        finish(client, token, runId)

        val blankName = register(client, token, runId, "  ", "🐳")
        assertEquals(HttpStatusCode.BadRequest, blankName.status)
        assertEquals("VALIDATION_ERROR", errorCode(blankName))

        val longName = register(client, token, runId, "가".repeat(51), "🐳")
        assertEquals(HttpStatusCode.BadRequest, longName.status)

        val noEmoji = register(client, token, runId, "이름", "")
        assertEquals(HttpStatusCode.BadRequest, noEmoji.status)
        assertEquals("VALIDATION_ERROR", errorCode(noEmoji))
    }

    @Test
    fun `register-as-route rejects a route-targeted run, another user's run, and feedback tags on freeform`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()
        val (_, otherToken) = client.signupNewUser()

        val targeted = start(client, token, "rt_001")
        upload(client, token, targeted, "completed_run")
        finish(client, token, targeted)
        val notFreeform = register(client, token, targeted, "코스", "🐳")
        assertEquals(HttpStatusCode.BadRequest, notFreeform.status)
        assertEquals("RUN_NOT_FREEFORM", errorCode(notFreeform))

        val freeform = start(client, token, null)
        upload(client, token, freeform, "completed_run")
        finish(client, token, freeform)
        val foreign = register(client, otherToken, freeform, "코스", "🐳")
        assertEquals(HttpStatusCode.NotFound, foreign.status)
        assertEquals("RUN_NOT_FOUND", errorCode(foreign))

        val tags = client.post("/v1/runs/$freeform/feedback-tags") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(FeedbackTagsRequest(tags = listOf("평지예요")))
        }
        assertEquals(HttpStatusCode.BadRequest, tags.status)
        assertNotNull(errorCode(tags))
    }

    /** 마이 화면 "총 러닝 횟수/총 거리"는 users.total_* 컬럼(갱신되지 않던)이 아니라 COMPLETED 기록 집계여야 한다. */
    @Test
    fun `users me totals count only COMPLETED runs`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val before: MeEnv = client.authGet("/v1/users/me", token).body()
        assertEquals(0, before.data!!.totalRuns)

        val reviewRun = start(client, token, null)
        upload(client, token, reviewRun, "under_review_run")
        assertEquals(RunStatus.UNDER_REVIEW, finish(client, token, reviewRun).data!!.status)

        val completedRun = start(client, token, null)
        upload(client, token, completedRun, "completed_run")
        val finished = finish(client, token, completedRun)
        assertEquals(RunStatus.COMPLETED, finished.data!!.status)

        val after: MeEnv = client.authGet("/v1/users/me", token).body()
        assertEquals(1, after.data!!.totalRuns, "UNDER_REVIEW run must not be counted")
        assertTrue(after.data.totalDistanceKm > 0.0)
    }
}
