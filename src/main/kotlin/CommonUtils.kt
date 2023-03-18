import api.Payload
import org.json.JSONObject
import java.util.*
import kotlin.math.pow


class CommonUtils {

    fun randomValue(exp: Int): Int {
        val d = 10.0
        val r = Random()
        return r.nextInt(d.pow(exp).toInt())
    }

    fun parseJSON(request: String): Payload {
        val p = Payload()
        val root = JSONObject(request)
        p.objectValue = root.getString("objectValue")
        if (root.isNull("objectId"))
            p.objectId = null.toString()
        else
            p.objectId = root.getString("objectId")
        p.userId = root.getString("userId")
        p.userName = root.getString("userName")
        p.chatsCount = root.get("chatsCount") as Integer
        p.env = root.getString("env")

        return p
    }

}

