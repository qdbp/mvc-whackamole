import kotlin.math.log
import kotlin.time.ExperimentalTime
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.dom.clear
import kotlinx.html.*
import kotlinx.html.dom.create
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.w3c.dom.*
import org.w3c.notifications.GRANTED
import org.w3c.notifications.Notification
import org.w3c.notifications.NotificationPermission

val ERROR_HOLDER = document.getElementById(INFO_BOX_ID)!!
val DATE_INPUT = document.getElementById(DATE_PICKER_INPUT_ID)!!
val APPT_TYPE_SELECTOR = document.getElementById(APPT_TYPE_SEL_ID)!!

var allowNotify: Boolean = false

fun getMvcBox(mvc: MVC): Element {
  return document.getElementById(mvc.cellDivId())!!
}

@ExperimentalTime
object ApptState {

  private val curAppts: MutableMap<MVC, ApptDetails> = mutableMapOf()
  private val alertAudio = Audio("apptavail.wav")

  fun updateDetails(details: ApptDetails) {
    curAppts[details.mvc] = details
    render(details)
  }

  private fun render(details: ApptDetails) {
    val outerCell = document.getElementById(details.mvc.cellDivId())!!
    with(outerCell) {
      removeChild(children[children.length - 1]!!)
      append(
          document.create.div(classes = INNER_CELL_CLS) {
            h3(classes = "mvcTitle") { +details.mvc.location }
            hr {}
            when (details) {
              is ApptNoData -> {
                classes += "noData"
                p { +"No data." }
              }
              is ApptTaken -> {
                classes += "noAppt"

                p { +"Nothing." }
                p { +"Stare intently." }
              }
              is ApptAvail -> {

                classes += "hasAppt"
                val apptDt = LocalDateTime.parse(details.isoDate)

                val isOld = getCutoffDate()?.let { apptDt.date > it } ?: false
                if (isOld) style = "background: #f8f8a0;"

                with(apptDt) {
                  b(classes = "apptDate") { +"${month.name} $dayOfMonth" }
                  p(classes = "apptTime") {
                    +("$hour".padStart(2, '0') + ':' + "$minute".padStart(2, '0'))
                  }
                }
                hr {}
                input(type = InputType.button, classes = "myButton grabLink") {
                  onClick = "location.href='${details.url}'"
                  value =
                      if (isOld) {
                        classes += "grabLinkOld"
                        "GRAB IT?"
                      } else {
                        classes += "grabLinkFresh"
                        "GRAB IT!"
                      }
                }
              }
            }
          })
    }
  }

  fun redrawAll() {
    curAppts.values.forEach { render(it) }
  }

  fun invalidateAll() {
    curAppts.values.clear()
    MVC.values().forEach { render(ApptNoData(it, null)) }
  }

  fun alertIfNeeded() {
    for ((mvc, details) in curAppts) {
      details as? ApptAvail ?: continue
      val cutoff = getCutoffDate()
      if (cutoff != null && cutoff < LocalDateTime.parse(details.isoDate).date) {
        continue
      }
      // TODO fragile
      val isChecked =
          getMvcBox(mvc).firstElementChild!!.firstElementChild!!.asDynamic().checked as Boolean
      if (!isChecked) {
        continue
      }
      if (allowNotify) {
        Notification("Appointment available at ${mvc.location} at ${details.isoDate}")
      }
      alertAudio.play()
      return
    }
  }
}

fun setStatusLine(status: MVCServerStatus?) {
  ERROR_HOLDER.clear()
  status?.let {
    ERROR_HOLDER.append(document.create.p(classes = "statusLine ${status.cls}") { +status.msg })
  }
}

/** Encapsulates the "FOMO log", which displays messages from the server. */
object FomoLog {

  private const val maxLogEntries = 8

  private val logBox = document.getElementById(LOG_BOX_ID)!!

  private fun keepEntriesUpTo(numToKeep: Int = maxLogEntries) {
    with(logBox) {
      while (childNodes.length > numToKeep) {
        removeChild(childNodes[childNodes.length - 1]!!)
      }
    }
  }

  fun ensureInitEntry() {
    if (logBox.childNodes.length == 0) {
      addEntry(MVCLogLine("New and taken appointments will appear in the FOMO log.", null))
    }
  }

  fun clear() {
    keepEntriesUpTo(0)
  }

  fun addEntry(line: MVCLogLine) {
    with(logBox) {
      keepEntriesUpTo(maxLogEntries - 1)
      val ts = if (line.isoDate != null) "${line.isoDate}: " else ""
      insertBefore(
          document.create.div(classes = "logLineBox shadowed") { p { +"$ts${line.msg}" } },
          childNodes[0])
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

fun getCurApptType(): ApptType {
  val name = APPT_TYPE_SELECTOR.asDynamic().value as String
  return ApptType.valueOf(name)
}

fun updateLayoutForApptType(apptType: ApptType) {
  val curClsString = apptType.centerType.classString()
  document.getElementsByClassName(OUTER_CELL_CLS).asList().forEach {
    val classes = it.classList.asList()
    if (curClsString in classes || ALWAYS_SHOW_CLS in classes) {
      it.asDynamic().style.display = "block"
    } else {
      it.asDynamic().style.display = "none"
    }
  }
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
      is ApptDetails -> ApptState.updateDetails(msg)
      is MVCLogLine -> FomoLog.addEntry(msg)
      is MVCServerStatus -> setStatusLine(msg)
      // todo refine hierarchy to disallow this
      is MVCClientMsg -> console.error("Got client message from server -- oopsie.")
    }
  }

  private var sendBuffer: MutableList<String> = mutableListOf()

  private var currentlyRecovering: Boolean = false
  private fun reconnect() {
    if (currentlyRecovering) {
      return
    }
    socket.close()
    setStatusLine(MVCServerStatus("Connection to server severed... trying to reconnect.", "error"))
    window.setTimeout(::initSocket, 5000)
    currentlyRecovering = true
  }

  private fun initSocket() {
    currentlyRecovering = false

    socket = WebSocket("ws://njmvc.enaumov.me/ws")

    socket.onmessage = ::onMessage
    socket.onopen =
        {
          console.log("Websocket opened.")
          setStatusLine(MVCServerStatus("Server connected.", "healthy"))
          FomoLog.ensureInitEntry()
          // ready indicator "burn" message
          socket.send("")
          // always send our current apt type on reconnect to avoid staleness
          socket.send(Json.encodeToString(MVCClientMsg(getCurApptType())))
          sendBuffer.forEach { send(it) }
          sendBuffer.clear()
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

  /**
   * Sends a message on the wire if the socket is alive.
   *
   * If the socket is recovering, queues the message to be sent when it's next alive.
   */
  fun send(msg: String) {
    if (currentlyRecovering) {
      sendBuffer.add(msg)
    } else {
      socket.send(msg)
    }
  }
}

@ExperimentalTime
fun main() {

  // initialize layout
  updateLayoutForApptType(getCurApptType())

  // set up socket
  val socket = Socket()

  // install timers
  window.setInterval(ApptState::alertIfNeeded, 2500)

  // install listeners
  Notification.requestPermission {
    when (it) {
      NotificationPermission.GRANTED -> allowNotify = true
      else -> {}
    }
  }

  APPT_TYPE_SELECTOR.addEventListener(
      "change",
      callback = {
        ApptState.invalidateAll()
        val apptType = getCurApptType()
        updateLayoutForApptType(apptType)
        val msg = Json.encodeToString(MVCClientMsg(apptType))
        socket.send(msg)
        FomoLog.clear()
        FomoLog.ensureInitEntry()
      })

  DATE_INPUT.addEventListener("change", callback = { ApptState.redrawAll() })
}
