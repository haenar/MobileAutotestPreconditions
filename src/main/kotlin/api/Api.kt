import api.Credentials
import api.Credentials.SUPERVISOR_ID
import api.Credentials.USER_NAME_A
import api.Credentials.USER_NAME_B
import api.JsonCreation
import api.ServerValues
import api.ServerValues.NOTES_NONE
import api.ServerValues.REJECTION_REASON
import enums.MessageTypes
import enums.ModerationDecisionType
import enums.ModerationRejectionSeverity
import enums.PostTag
import org.json.JSONArray
import ru.talenttech.xqa.oknetwork.OkNetwork
import ru.talenttech.xqa.oknetwork.request.ContentType
import ru.talenttech.xqa.oknetwork.response.Response
import java.lang.Thread.sleep

class Api {

    private val jsonCreation = JsonCreation()
    private val client = OkNetwork.rpcClient()
    private var baseUrl = "https://api.letsopen.%env%/json-rpc"
    private var adminUrl = "https://api.admin.letsopen.%env%/json-rpc"
    private var tokenUrl = "https://api.letsopen.%env%/debug/get-token"

    fun getJwtToken(userId: String, env: String): String {

        val body = jsonCreation.getJwtTokenRequestBody(userId)

        return client.post(
            tokenUrl.replace("%env%", env),
            contentType = ContentType.JSON,
            body = body
        ).body("idToken")
    }

    fun createComment(userId: String, userName: String, text: String, postId: String?, env: String): String{
        val userToken = getJwtToken(userId, env)

        val body = jsonCreation.createBodyString(
            ServerValues.METHOD_CREATE_COMMENT,
            mapOf(
                ServerValues.FIELD_JWT to userToken,
                ServerValues.FIELD_AUTHOR_NAME to userName,
                ServerValues.FIELD_TEXT to text,
                ServerValues.FIELD_CHAT_ID to postId
            )
        )

        val commentID: String = client.post(
            baseUrl.replace("%env%", env),
            contentType = ContentType.JSON,
            body = body
        ).body("result.data.id")

        //val token = getJwtToken(SUPERVISOR_ID, env)
        //approveContent(token, commentID, MessageTypes.COMMENT, ModerationDecisionType.APPROVED, NOTES_NONE, env)

        return commentID
    }

    fun rejectComment(commentID: String?, env: String): String? {
        val token = getJwtToken(SUPERVISOR_ID, env)
        if (commentID != null) {
            rejectContent(token, commentID, MessageTypes.COMMENT, ModerationDecisionType.REJECTED, NOTES_NONE, REJECTION_REASON, ModerationRejectionSeverity.LOW, env)
        }

        return commentID
    }

    fun approveContent(
        token: String,
        contentId: String,
        contentType: MessageTypes,
        decisionType: ModerationDecisionType,
        notes: String,
        env: String
    ) = moderateContent(
        token,
        contentId,
        contentType,
        decisionType,
        notes,
        null,
        null,
        env
    )

    fun rejectContent(
        token: String,
        contentId: String,
        contentType: MessageTypes,
        decisionType: ModerationDecisionType,
        notes: String,
        rejectionReason: String,
        rejectionSeverity: ModerationRejectionSeverity,
        env: String
    ) = moderateContent(
        token,
        contentId,
        contentType,
        decisionType,
        notes,
        rejectionReason,
        rejectionSeverity,
        env
    )

    private fun moderateContent(
        token: String,
        contentId: String,
        contentType: MessageTypes,
        decisionType: ModerationDecisionType,
        notes: String,
        rejectionReason: String? = null,
        rejectionSeverity: ModerationRejectionSeverity? = null,
        env: String
    ): Response {

        sleep(5000)
        setPostTag(token, contentId, PostTag.SELF_DISCLOSURE, env)
        sleep(5000)

        val body = jsonCreation.createBodyString(
            ServerValues.METHOD_MODERATE_CONTENT,
            mapOf(
                ServerValues.FIELD_JWT to token,
                ServerValues.FIELD_CONTENT_ID to contentId,
                ServerValues.FIELD_CONTENT_TYPE to getServerContentTypeValue(contentType),
                ServerValues.FIELD_DECISION to getServerDecisionTypeValue(decisionType),
                ServerValues.FIELD_NOTES to notes,
                ServerValues.FIELD_REJECTION_REASON to rejectionReason,
                ServerValues.FIELD_REJECTION_SEVERITY to rejectionSeverity?.let { getServerRejectionSeverityValue(it) },
            )
        )

        return client.post(
            adminUrl.replace("%env%", env),
            contentType = ContentType.JSON,
            body = body
        )
    }

    fun setPostTag(token: String, postId: String, tag: PostTag, env: String): Response {

        val body = jsonCreation.createBodyString(
            ServerValues.METHOD_SET_POST_TAG,
            mapOf(
                ServerValues.FIELD_JWT to token,
                ServerValues.FIELD_POST_ID to postId,
                ServerValues.FIELD_TAG to getServerPostTagValue(tag)
            )
        )

        return client.post(
            adminUrl.replace("%env%", env),
            contentType = ContentType.JSON,
            body = body
        )
    }


    private fun getServerContentTypeValue(contentType: MessageTypes) = when (contentType) {
        MessageTypes.COMMENT -> ServerValues.MESSAGE_TYPE_COMMENT
        MessageTypes.DIRECT_MESSAGE -> ServerValues.MESSAGE_TYPE_DIRECT_MESSAGE
    }

    private fun getServerDecisionTypeValue(decision: ModerationDecisionType) = when (decision) {
        ModerationDecisionType.APPROVED -> ServerValues.MODERATION_DECISION_TYPE_APPROVED
        ModerationDecisionType.REJECTED -> ServerValues.MODERATION_DECISION_TYPE_REJECTED
    }

    private fun getServerRejectionSeverityValue(severity: ModerationRejectionSeverity) = when (severity) {
        ModerationRejectionSeverity.LOW -> ServerValues.MODERATION_REJECTION_SEVERITY_LOW
        ModerationRejectionSeverity.MIDDLE -> ServerValues.MODERATION_REJECTION_SEVERITY_MIDDLE
        ModerationRejectionSeverity.HIGH -> ServerValues.MODERATION_REJECTION_SEVERITY_HIGH
    }

    private fun getServerPostTagValue(postTag: PostTag) = when (postTag) {
        PostTag.SELF_DISCLOSURE -> ServerValues.POST_TAG_SELF_DISCLOSURE
        PostTag.QUESTION_PROMPT -> ServerValues.POST_TAG_QUESTION_PROMPT
        PostTag.OTHER_HIGH -> ServerValues.POST_TAG_OTHER_HIGH
        PostTag.GREETING -> ServerValues.POST_TAG_GREETING
        PostTag.APP_QUESTION -> ServerValues.POST_TAG_APP_QUESTION
        PostTag.OTHER_LOW -> ServerValues.POST_TAG_OTHER_LOW
        PostTag.SEX_CHAT -> ServerValues.POST_TAG_SEX_CHAT
    }

    fun createDM(
        text: String,
        postId: String? = null,
        userId: String,
        env: String
    ): String {
        val userToken = getJwtToken(userId, env)
        val dmPostID = "${postId}_${USER_NAME_B}_${USER_NAME_A}"

        getSuggestedChats(userToken, 40, null, env)
        followSuggestedChat(userToken, postId!!,  env)

        val body = jsonCreation.createBodyString(
            ServerValues.METHOD_CREATE_DIRECT_MESSAGE,
            mapOf(
                ServerValues.FIELD_JWT to userToken,
                ServerValues.FIELD_TEXT to text,
                ServerValues.FIELD_AUTHOR_NAME to USER_NAME_B,
                ServerValues.FIELD_PEER_NAME to USER_NAME_A,
                ServerValues.FIELD_PARENT_CHAT_ID to postId,
                ServerValues.FIELD_CHAT_ID to dmPostID
            )
        )

        val id: String = client.post(
            baseUrl.replace("%env%", env),
            contentType = ContentType.JSON,
            body = body
        ).body("result.data.id")

        //val supervisorToken = getJwtToken(SUPERVISOR_ID, env)
        //approveContent(supervisorToken, id, MessageTypes.DIRECT_MESSAGE, ModerationDecisionType.APPROVED, "For unfollowing suggested chat", env)

        return dmPostID
    }



    fun createComplaint(token: String, cause: String, itemId: String, itemType: MessageTypes, text: String, env: String): Response {

        val body = jsonCreation.createBodyString(
            ServerValues.METHOD_CREATE_COMPLAINT,
            mapOf(
                ServerValues.FIELD_JWT to token,
                ServerValues.FIELD_CAUSE to cause,
                ServerValues.FIELD_ITEM_ID to itemId,
                ServerValues.FIELD_ITEM_TYPE to getServerMessageType(itemType),
                ServerValues.FIELD_TEXT to text
            )
        )

      return client.post(
            baseUrl.replace("%env%", env),
            contentType = ContentType.JSON,
            body = body
        )
    }

    fun acknowledgeCommentsRejection(supervisorToken: String, commentID: String, env: String): Response {

        val comments = JSONArray()
        comments.put(commentID)

        val body = jsonCreation.createBodyString(
            ServerValues.METHOD_ACKNOWLEDGE_COMMENT_REJECTION,
            mapOf(
                ServerValues.FIELD_JWT to supervisorToken,
                ServerValues.FIELD_COMMENT_IDS to comments,
            )
        )

        return client.post(
            baseUrl.replace("%env%", env),
            contentType = ContentType.JSON,
            body = body
        )
    }

    fun acknowledgeDMRejection(supervisorToken: String, directMessageID: String, env: String): Response {

        val directMessageIds = JSONArray()
        directMessageIds.put(directMessageID)

        val body = jsonCreation.createBodyString(
            ServerValues.METHOD_ACKNOWLEDGE_DM_REJECTION,
            mapOf(
                ServerValues.FIELD_JWT to supervisorToken,
                ServerValues.FIELD_DIRECT_MESSAGE_IDS to directMessageIds,
            )
        )

        return client.post(
            baseUrl.replace("%env%", env),
            contentType = ContentType.JSON,
            body = body
        )
    }


    fun deleteComment(userId: String, commentId: String, env: String): Response {
        val userToken = getJwtToken(userId, env)
        val body = jsonCreation.createBodyString(
            ServerValues.METHOD_DELETE_COMMENT,
            mapOf(
                ServerValues.FIELD_JWT to userToken,
                ServerValues.FIELD_COMMENT_ID to commentId
            )
        )

        return client.post(
            baseUrl.replace("%env%", env),
            contentType = ContentType.JSON,
            body = body
        )
    }



    fun deleteDM(userId: String, messageId: String, env: String): Response {
        val userToken = getJwtToken(userId, env)
        val body = jsonCreation.createBodyString(
            ServerValues.METHOD_DELETE_DIRECT_MESSAGE,
            mapOf(
                ServerValues.FIELD_JWT to userToken,
                ServerValues.FIELD_MESSAGE_ID to messageId
            )
        )

        return client.post(
            baseUrl.replace("%env%", env),
            contentType = ContentType.JSON,
            body = body
        )
    }



    private fun getServerMessageType(messageType: MessageTypes) = when (messageType) {
        MessageTypes.COMMENT -> ServerValues.MESSAGE_TYPE_COMMENT
        MessageTypes.DIRECT_MESSAGE -> ServerValues.MESSAGE_TYPE_DIRECT_MESSAGE
    }


    fun getSuggestedChats(userToken: String, pageSize: Int, pageCursor: String? = null, env: String): Response {

        val body = jsonCreation.createBodyString(
            ServerValues.METHOD_GET_SUGGESTED_CHATS,
            mapOf(
                ServerValues.FIELD_JWT to userToken,
                ServerValues.FIELD_PAGE_SIZE to pageSize,
                ServerValues.FIELD_PAGE_CURSOR to pageCursor
            )
        )

        return client.post(
            baseUrl.replace("%env%", env),
            contentType = ContentType.JSON,
            body = body
        )
    }

    fun followSuggestedChat(userToken: String, chatId: String, env: String): Response {

        val body = jsonCreation.createBodyString(
            ServerValues.METHOD_FOLLOW_SUGGESTED_CHAT,
            mapOf(
                ServerValues.FIELD_JWT to userToken,
                ServerValues.FIELD_CHAT_ID to chatId
            )
        )

        return client.post(
            baseUrl.replace("%env%", env),
            contentType = ContentType.JSON,
            body = body
        )
    }

    fun newUserCreation (phoneNumber: String, env: String): String? {
        val createTestUserResponse = createTestUser(phoneNumber, env)
        val firstUserId: String = createTestUserResponse.body("result.data.uid")

        setUserAge(firstUserId, env)

        sleep(10000)
        val supervisorJwtToken = getJwtToken(SUPERVISOR_ID, env)
        val responseForGettingUserIdByPhone = getUserUIDByPlainPhone(supervisorJwtToken,  phoneNumber, env)
        val userIdGettingByPhone: String = responseForGettingUserIdByPhone.body("result.data.uid")

        return when (firstUserId) {
            userIdGettingByPhone -> firstUserId
            else -> null
        }
    }

    private fun getUserUIDByPlainPhone(token: String, phoneNumber: String, env: String): Response {

        val body = jsonCreation.createBodyString(
            ServerValues.METHOD_GET_UID_BY_PLAIN_PHONE,
            mapOf(
                ServerValues.FIELD_JWT to token,
                ServerValues.FIELD_PHONE_NUMBER to phoneNumber
            )
        )

        return client.post(
            adminUrl.replace("%env%", env),
            contentType = ContentType.JSON,
            body = body
        )
    }

    private fun createTestUser(phoneNumber: String, env: String): Response {
        val body = jsonCreation.createBodyString(
            ServerValues.METHOD_CREATE_TEST_USER,
            mapOf(
                ServerValues.FIELD_TEST_USER_PHONE_NUMBER to phoneNumber,
                ServerValues.FIELD_API_KEY_FOR_USER_CREATION to Credentials.USER_CREATION_API_KEY,
            )
        )

        return client.post(
            baseUrl.replace("%env%", env),
            contentType = ContentType.JSON,
            body = body
        )
    }

    fun setUserAge(userId: String, env: String): Response {
        val token = getJwtToken(userId, env)

        val body = jsonCreation.createBodyString(
            ServerValues.METHOD_SET_USER_AGE,
            mapOf(
                ServerValues.FIELD_JWT to token,
                ServerValues.FIELD_AGE to 25,
            )
        )

        return client.post(
            baseUrl.replace("%env%", env),
            contentType = ContentType.JSON,
            body = body
        )
    }

    fun setContacts(userId: String, phone1: String, phone2: String, env: String): Response {

        val phones = JSONArray()
        phones.put(phone1)
        phones.put(phone2)

        val token = getJwtToken(userId, env)

        val body = jsonCreation.createBodyString(
            ServerValues.METHOD_SET_CONTACTS,
            mapOf(
                ServerValues.FIELD_JWT to token,
                ServerValues.FIELD_CONTACTS to phones
            )
        )

        return client.post(
            baseUrl.replace("%env%", env),
            contentType = ContentType.JSON,
            body = body
        )
    }

}