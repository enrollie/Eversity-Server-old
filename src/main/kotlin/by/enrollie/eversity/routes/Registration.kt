package by.enrollie.eversity.routes

import by.enrollie.eversity.data_classes.APIUserType
import by.enrollie.eversity.database.EversityDatabase
import by.enrollie.eversity.schools_by.SchoolsAPIClient
import by.enrollie.eversity.schools_by.SchoolsWebWrapper
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import java.security.MessageDigest
import kotlin.text.Charsets.UTF_8

val registeredUsersStorage = mutableListOf<String>()
fun md5(str: String): ByteArray = MessageDigest.getInstance("MD5").digest(str.toByteArray(UTF_8))
fun ByteArray.toHex() = joinToString(separator = "") { byte -> "%02x".format(byte) }

fun Route.registrationRoutes() {
    route("/api") {
        post("/login") {
            val loginJSON: JsonObject
            try {
                val req = call.receive<String>()
                loginJSON = Json.parseToJsonElement(req).jsonObject
            } catch (e: ContentTransformationException) {
                application.log.debug("Deserialization failed. ${e.message}", e)
                return@post call.respond(HttpStatusCode.BadRequest, "Deserialization failed: ${e.localizedMessage}")
            } catch (e: SerializationException) {
                application.log.debug("Deserialization failed", e)
                return@post call.respond(HttpStatusCode.BadRequest, "Malformed JSON")
            } catch (e: IllegalArgumentException) {
                application.log.debug("Deserialization failed", e)
                return@post call.respond(HttpStatusCode.BadRequest, "Malformed JSON")
            }
            if (!loginJSON.containsKey("username") || !loginJSON.containsKey("password")) {
                return@post call.respondText(
                    "JSON does not contain username or password.",
                    contentType = ContentType.Text.Plain,
                    status = HttpStatusCode.BadRequest
                )
            }
            val schoolsAPIClient = SchoolsAPIClient()
            val username = loginJSON["username"]!!.jsonPrimitive.content
            val password = loginJSON["password"]!!.jsonPrimitive.content
            val token = schoolsAPIClient.getAPIToken(username, password)
            if (token == null) {
                call.respond(HttpStatusCode.Unauthorized, "Schools.by did not accept this credentials.")
                this.application.log.debug("Schools.by did not accept credentials from ${call.request.local.host}")
                return@post
            }
            val userData = schoolsAPIClient.getCurrentUserData()

            val userID = userData["id"].toString().toInt()
            if (EversityDatabase.doesUserExist(userID)){
                this.application.log.debug("User with $userID is already registered. Issuing new token...")
                val issuedToken = EversityDatabase.issueToken(userID)
                call.respond(HttpStatusCode.Found, )
            }
            val type = when (userData["type"].toString()) {
                "Pupil" -> {
                    APIUserType.Pupil
                }
                "Parent" -> {
                    APIUserType.Parent
                }
                "Teacher" -> {
                    APIUserType.Teacher
                }
                else -> {
                    APIUserType.Pupil
                }
            }
            val webWrapper = SchoolsWebWrapper("https://191minsk.schools.by/")
            val cookies = webWrapper.login(username, password)
            return@post call.respond(HttpStatusCode.OK, cookies.toString())
        }
    }
}

fun Application.registerRegistrationRoutes() {
    routing {
        registrationRoutes()
    }
}