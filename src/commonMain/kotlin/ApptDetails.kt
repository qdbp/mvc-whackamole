import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable sealed class MVCWsMsg

@Serializable
@SerialName("details")
sealed class ApptDetails : MVCWsMsg() {
  abstract val mvc: MVC
  abstract val apptType: ApptType?
}

@Serializable
@SerialName("details.avail")
data class ApptAvail(
    override val mvc: MVC,
    override val apptType: ApptType,
    val isoDate: String,
    val url: String
) : ApptDetails()

@Serializable
@SerialName("details.taken")
data class ApptTaken(override val mvc: MVC, override val apptType: ApptType?) : ApptDetails()

@Serializable
@SerialName("details.nodata")
data class ApptNoData(override val mvc: MVC, override val apptType: ApptType?) : ApptDetails()

@Serializable
@SerialName("log_line")
data class MVCLogLine(val msg: String, val isoDate: String?) : MVCWsMsg()

@Serializable
@SerialName("server_status")
data class MVCServerStatus(val msg: String, val cls: String) : MVCWsMsg()

@Serializable
@SerialName("client_msg")
data class MVCClientMsg(val apptType: ApptType) : MVCWsMsg()
