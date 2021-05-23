package by.enrollie.eversity

import by.enrollie.eversity.routes.registerPupilsRouting
import by.enrollie.eversity.routes.registerRegistrationRoutes
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun testRoot() {
        withTestApplication({
            registerPupilsRouting()
            registerRegistrationRoutes()
        }) {
            handleRequest(HttpMethod.Get, "/info/-1").apply {
                assertEquals(HttpStatusCode.NotFound, response.status())
            }
            with(handleRequest(HttpMethod.Post, "/api/login"){
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(Json.parseToJsonElement("{\"username\":\"123456789\",\"password\":\"123\"}").toString())
            }){
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
            with(handleRequest(HttpMethod.Post, "/api/login"){
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(Json.parseToJsonElement("{\"username\":\"ENROLLIE\",\"password\":\"TEST_CREDENTIALS_PLEASE_DONT_REMOVE_CONTACT_AT_GITHUB\"}").toString())
            }){
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }
    }
    fun testRegistration(){

    }
}