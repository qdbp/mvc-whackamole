import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.datetime.*
import kotlinx.dom.clear
import kotlinx.html.*
import kotlinx.html.dom.create
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.w3c.dom.*

fun deltaDaysToColor(days: Int): String {
  val truncDays = max(0, min(days, 60)).toDouble()
  val red = (180 + 50 * max(0.0, truncDays / 30.0 - 1)).roundToInt()
  val green = (180 + 50 * max(0.0, 1 - truncDays / 30.0)).roundToInt()
  console.log(truncDays, red, green)
  return red.toString(16).padEnd(2, '0') + green.toString(16).padEnd(2, '0') + "c0"
}

@ExperimentalTime
fun apptCell(details: ApptDetails, isoDate: Instant?, color: String): HTMLElement {
  return with(details) {
    document.create.div(classes = "innerCell") {
      style = "background: #$color;"
      h3(classes = "mvcTitle") { +mvc.location }
      hr {}
      when (date) {
        null -> {
          classes += "noAppt"
          p { +"Nothing." }
          p { +"Stare intently." }
        }
        else -> {
          classes += "hasAppt"
          with(isoDate!!.toLocalDateTime(TimeZone.currentSystemDefault())) {
            b(classes = "apptDate") { +"${month.name} $dayOfMonth" }
            p(classes = "apptTime") {
              +("$hour".padStart(2, '0') + ':' + "$minute".padStart(2, '0'))
            }
          }
          input(type = InputType.button, classes = "myButton grabLink") {
            onClick = "location.href='$url'"
            value = "GRAB IT!"
            classes += "grabLink"
          }
        }
      }
    }
  }
}

@ExperimentalTime
class Socket {

  private lateinit var socket: WebSocket

  init {
    initSocket()
  }

  private fun clearError() {
    val errorHolder = document.getElementById("infoBox")!!
    errorHolder.clear()
  }

  private fun setLiveness(msg: String, cls: String) {
    val errorHolder = document.getElementById("infoBox")!!
    errorHolder.clear()
    errorHolder.append(document.create.p(classes = cls) { +msg })
  }

  private fun onMessage(message: MessageEvent) {
    val details = Json.decodeFromString<ApptDetails>(message.data as String)
    console.log("Received ${message.data as String} from server.")
    with(details) {
      val (color, isoDate) =
          date?.let {
            val isoDate = LocalDateTime.parse(date).toInstant(TimeZone.currentSystemDefault())
            val today = Clock.System.now()
            (isoDate - today).inDays.roundToInt().let { deltaDaysToColor(it) } to isoDate
          }
              ?: "#f8f8f8" to null

      val cellContainer = document.getElementById(mvc.cellDivId())

      cellContainer!!.let {
        it.clear()
        it.append(apptCell(details, isoDate, color))
      }
    }
  }

  private var alreadyRecovering: Boolean = false
  private fun reconnect() {
    if (alreadyRecovering) {
      return
    }
    socket.close()
    setLiveness("Connection to server severed... trying to reconnect.", "error")
    window.setTimeout(::initSocket, 5000)
    alreadyRecovering = true
  }

  private fun initSocket() {
    alreadyRecovering = false

    socket = WebSocket("ws://njmvc.enaumov.me/ws")

    socket.onmessage = ::onMessage
    socket.onopen =
        {
          clearError()
          console.log("Websocket opened.")
          setLiveness("Server connected.", "healthy")
          socket.send("")
        }
    socket.onclose =
        {
          console.error(it)
          reconnect()
        }
    socket.onerror =
        {
          console.error(it)
          reconnect()
        }
  }
}

@ExperimentalTime
fun main() {
  Socket()
}
