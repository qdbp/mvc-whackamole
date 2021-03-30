import kotlinx.serialization.Serializable

@Serializable
data class ApptDetails(val mvc: MVC, val date: String?, val url: String?)


