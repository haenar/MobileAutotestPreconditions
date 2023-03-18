import api.Credentials.FIRST_TEST_USER_PHONE_NUMBER
import api.Credentials.SECOND_TEST_USER_PHONE_NUMBER
import api.Credentials.USER_NAME_A
import api.Credentials.USER_NAME_B
import api.Payload
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import org.joda.time.DateTime
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore

val api = Api()
const val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkxldHMwcGVuQmVhcmVyIn0.ova4sP0R1QHIojoi8K4kXgZk2wUxo_WUlFYoKQFnSSY"

fun main() {
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
    keyStore.load(FileInputStream(File("resources/keystore.jks")), "letsopen".toCharArray())

    val environment = applicationEngineEnvironment {
/*        connector {
            port = 8081
        }*/
        sslConnector(
            keyStore = keyStore,
            keyAlias = "sampleAlias",
            keyStorePassword = { "".toCharArray() },
            privateKeyPassword = { "letsopen".toCharArray() }) {
            port = 4433
        }

        module(Application::configureRouting)
    }
    embeddedServer(Netty, environment) {
    }.start(wait = true)

}

fun Application.configureRouting() {
    routing {
        post("/") {
            if (call.request.header("Authorization") == null) {
                call.response.status(Unauthorized)
                call.respondText("""{"error" : "Bearer Needed"}""")
            }
            else if (call.request.header("Authorization") != "Bearer $token") {
                call.response.status(Unauthorized)
                call.respondText("""{"error" : "Not Authorized"}""")
            }
            else {
                val dateTime = DateTime.now().toString("dd.MM.YYYY HH:mm:ss")
                val remoteIP = call.request.origin.remoteHost
                call.application.environment.log.info(
                    """Webhook detected at $dateTime from $remoteIP"""
                )

                val requestPayload = call.receiveText()
                val payload = CommonUtils().parseJSON(requestPayload)
                val responsePayload = sendRequest(
                    call.request.queryParameters["action"],
                    payload
                )

                call.respondText(responsePayload)
                call.application.environment.log.info(
                    """Response sent at $dateTime to $remoteIP"""
                )
            }
        }

        get("/info") {
            call.respondText("server started")
        }
    }
}


fun sendRequest(action: String?, p: Payload): String {
    if (action == null)
        return printKeys()

    if (p.chatsCount.toInt() <= 0)
        return """{"error": "chatsCount should be positive or non zero value"}"""

    val text = """{ "ids" : [%id%] }"""
    val ids = arrayListOf<String>()

    for (i in 1..p.chatsCount.toInt()) {
        val id = chooseAction(action, p)
        ids.add(""""$id"""")
    }

    return text.replace("%id%", ids.joinToString(","))
}

fun chooseAction(action: String, p: Payload): String? {
    return when (action) {
        "createPost" -> createPost(p)
        "createComment" -> createComment(p)
        "createDM" -> dmCreation(p)
        "rejectComment" -> rejectComment(p)
        "deleteComment" -> deleteComment(p)
        "deleteDM" -> deleteDM(p)
        "createNewUser" -> createNewUser(p)
        else -> ""
    }
}

fun printKeys(): String {
    return """{
    "createPost" : "create post",
    "createComment" : "create comment",
    "createDM" : "dm creation",
    ""rejectComment" : "reject comment",
    "deleteComment" : "delete comment",
    "deleteDM" : "delete DM",
    "createNewUser" : "delete and create the new user"
}"""

}

fun createPost(p: Payload): String? {
    var globalPostTitle = p.objectValue
    if (globalPostTitle == "")
        globalPostTitle = "An autotest post №" + CommonUtils().randomValue(5)
    return api.createComment(p.userId, p.userName, globalPostTitle, null, p.env)
}

fun createComment(p: Payload): String? {
    var globalCommentTitle = p.objectValue
    if (globalCommentTitle == "")
        globalCommentTitle = "An autotest comment №" + CommonUtils().randomValue(5)
    return api.createComment(p.userId, p.userName, globalCommentTitle, p.objectId, p.env)
}

fun dmCreation(p: Payload): String? {
    var globalDMTitle = p.objectValue
    if (globalDMTitle == "")
        globalDMTitle = "An autotest DM №" + CommonUtils().randomValue(5)

    return api.createDM(globalDMTitle, p.objectId, p.userId, p.env)
}

fun rejectComment(p: Payload): String? {
    api.rejectComment(p.objectId, p.env)

    return p.objectId
}

fun deleteComment(p: Payload): String? {
    api.deleteComment(p.userId, p.objectId, p.env)

    return p.objectId
}

fun deleteDM(p: Payload): String? {
    api.deleteDM(p.userId, p.objectId, p.env)

    return p.objectId
}

fun createNewUser(p: Payload): String? {

    val userPhoneNumber = when (p.userName) {
        USER_NAME_A -> FIRST_TEST_USER_PHONE_NUMBER
        else -> SECOND_TEST_USER_PHONE_NUMBER
    }

    val userID : String? = api.newUserCreation(userPhoneNumber, p.env)
    if (userID.isNullOrEmpty())
        return null

    api.setContacts(userID, FIRST_TEST_USER_PHONE_NUMBER, SECOND_TEST_USER_PHONE_NUMBER, p.env)

    return userID
}
