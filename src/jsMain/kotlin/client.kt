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
import org.w3c.notifications.GRANTED
import org.w3c.notifications.Notification
import org.w3c.notifications.NotificationPermission

const val NO_APPT_BG_COLOR: String = "#f8f8f8"

val ERROR_HOLDER = document.getElementById("infoBox")!!
val DATE_INPUT = document.getElementById("datePickerInput")!!

val curAppts: MutableMap<MVC, ApptDetails> = mutableMapOf()
var allowNotify: Boolean = false

fun getMvcBox(mvc: MVC): Element {
  return document.getElementById(mvc.cellDivId())!!
}

@ExperimentalTime
fun getCellColor(apptInstant: Instant): String {

  val days = (apptInstant - Clock.System.now()).inDays.roundToInt()
  val truncDays = max(0, min(days, 60)).toDouble()

  val red = (180 + 50 * max(0.0, truncDays / 30.0 - 1)).roundToInt()
  val green = (180 + 50 * max(0.0, 1 - truncDays / 30.0)).roundToInt()

  return red.toString(16).padEnd(2, '0') + green.toString(16).padEnd(2, '0') + "c0"
}

@ExperimentalTime
fun drawApptCell(msg: ApptDetails): HTMLElement {
  return document.create.div(classes = "innerCell") {
    h3(classes = "mvcTitle") { +msg.mvc.location }
    hr {}
    when (msg) {
      is ApptTaken -> {
        classes += "noAppt"
        this@div.style = "background: $NO_APPT_BG_COLOR;"

        p { +"Nothing." }
        p { +"Stare intently." }
      }
      is ApptAvail -> {
        classes += "hasAppt"
        val apptDt = LocalDateTime.parse(msg.isoDate)
        val apptInstant = apptDt.toInstant(TimeZone.currentSystemDefault())
        this@div.style = "background: #${getCellColor(apptInstant)}"

        with(apptDt) {
          b(classes = "apptDate") { +"${month.name} $dayOfMonth" }
          p(classes = "apptTime") { +("$hour".padStart(2, '0') + ':' + "$minute".padStart(2, '0')) }
        }
        input(type = InputType.button, classes = "myButton grabLink") {
          onClick = "location.href='${msg.url}'"
          value = "GRAB IT!"
          classes += "grabLink"
        }
      }
    }
  }
}

fun setStatusLine(status: MVCServerStatus?) {
  ERROR_HOLDER.clear()
  status?.let {
    ERROR_HOLDER.append(document.create.p(classes = "statusLine ${status.cls}") { +status.msg })
  }
}

fun alertIfNeeded() {
  curAppts.forEach { (mvc, details) ->
    when (details) {
      is ApptTaken -> return@forEach
      is ApptAvail -> {
        val cutoff = getCutoffDate()
        if (cutoff != null && cutoff < LocalDateTime.parse(details.isoDate).date) {
          return
        }
        // TODO fragile
        val isChecked =
            getMvcBox(mvc).firstElementChild!!.firstElementChild!!.asDynamic().checked as Boolean
        if (!isChecked) {
          return@forEach
        }
        Audio("apptavail.wav").play()
        if (allowNotify) {
          Notification("Appointment available at ${mvc.name} at ${details.isoDate}")
        }
        return
      }
    }
  }
}

fun getCutoffDate(): LocalDate? {
  val dateStr: String = DATE_INPUT.asDynamic().value as String
  if (dateStr == "") {
    return null
  }
  return LocalDate.parse(dateStr)
}

@ExperimentalTime
class Socket {

  private lateinit var socket: WebSocket

  init {
    initSocket()
  }

  private fun onMessage(message: MessageEvent) {
    val msg = Json.decodeFromString<MVCWsMsg>(message.data as String)
    console.log("Received ${message.data as String} from server.")
    when (msg) {
      is ApptDetails -> {
        curAppts[msg.mvc] = msg
        val outerCell = document.getElementById(msg.mvc.cellDivId())!!
        with(outerCell) {
          removeChild(children[children.length - 1]!!)
          append(drawApptCell(msg))
        }
      }
      is MVCLogLine -> {
        with(document.getElementById("logBox")!!) {
          while (childNodes.length > 10) {
            removeChild(childNodes[childNodes.length - 1]!!)
          }
          insertBefore(
              document.create.div(classes = "logLineBox") { p { +"${msg.isoDate}: ${msg.msg}" } },
              childNodes[0])
        }
      }
      is MVCServerStatus -> setStatusLine(msg)
    }
  }

  private var alreadyRecovering: Boolean = false
  private fun reconnect() {
    if (alreadyRecovering) {
      return
    }
    socket.close()
    setStatusLine(MVCServerStatus("Connection to server severed... trying to reconnect.", "error"))
    window.setTimeout(::initSocket, 5000)
    alreadyRecovering = true
  }

  private fun initSocket() {
    alreadyRecovering = false

    socket = WebSocket("ws://njmvc.enaumov.me/ws")
    // socket = WebSocket("ws://127.0.0.1:8081/ws")

    socket.onmessage = ::onMessage
    socket.onopen =
        {
          console.log("Websocket opened.")
          setStatusLine(MVCServerStatus("Server connected.", "healthy"))
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
  Notification.requestPermission {
    when (it) {
      NotificationPermission.GRANTED -> allowNotify = true
      else -> {}
    }
  }
  window.setInterval(::alertIfNeeded, 3000)
  Socket()
}
