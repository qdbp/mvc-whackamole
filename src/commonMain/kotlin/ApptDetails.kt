import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable sealed class MVCWsMsg

@Serializable
@SerialName("details")
sealed class ApptDetails : MVCWsMsg() {
  abstract val mvc: MVC
}

@Serializable
@SerialName("details.avail")
data class ApptAvail(override val mvc: MVC, val isoDate: String, val url: String) : ApptDetails()

@Serializable
@SerialName("details.taken")
data class ApptTaken(override val mvc: MVC) : ApptDetails()

@Serializable
@SerialName("log_line")
data class MVCLogLine(val msg: String, val isoDate: String) : MVCWsMsg()

@Serializable
@SerialName("server_status")
data class MVCServerStatus(val msg: String, val cls: String) : MVCWsMsg()
