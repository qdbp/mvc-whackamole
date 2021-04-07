import io.ktor.application.*
import io.ktor.features.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.ExperimentalTime
import kotlin.time.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.receiveOrNull
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.html.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val NJTZ = TimeZone.of("US/Eastern")
private val LOG_FORMAT = DateTimeFormatter.ofPattern("MMMM dd, hh:mm:ss a")

val motds =
    listOf(
        "SCHEDULE FIRST ASK QUESTIONS LATER!",
        "GRAB IT!",
        "THANKS, MURPHY!",
        "SALEM IS LOVELY!",
        "STILL BETTER THAN NJ TRANSIT!",
    )

fun HTML.index() {

  head {
    link {
      rel = "stylesheet"
      href = "main.css"
    }
  }
  body {
    // root div
    div {
      id = "root"
      // header elements
      div {
        id = "titleHolder"
        img(classes = "bestFlag", src = "/nj.png")
        div {
          h1(classes = "title") {
            id = "title"
            +"NJ MVC"
            select {
              id = APPT_TYPE_SEL_ID
              ApptType.values().forEach {
                option {
                  value = it.name
                  if (it == DEFAULT_APPT_TYPE) {
                    selected = true
                  }
                  +it.fullName.toUpperCase()
                }
              }
            }
            +"WHACKAMOLE"
          }
          p(classes = "subtitle") { +motds[Random.nextInt(motds.size)] }
          br {}
          p(classes = "info") {
            +("Appointment slots show automatically -- do not refresh the page. " +
                "Earliest slot shown. " +
                "Links usually appear within 3 seconds of slot availability.")
          }
          p(classes = "info") { +"Sound will play when a desired appointment opens." }
        }
        img(classes = "bestFlag", src = "/nj.png")
      }
      // server status elements
      div { id = INFO_BOX_ID }
      // card elements
      div {
        id = "cellHolder"
        div(classes = "outerCell controlBox $ALWAYS_SHOW_CLS") {
          h3 { +"Alert date." }
          hr {}
          input(type = InputType.date) { id = DATE_PICKER_INPUT_ID }
          p(classes = "info") { +"Only appointments on or before this date will alert." }
        }
        MVC.values().forEach {
          div(classes = "$OUTER_CELL_CLS ${it.centerType.classString()}") {
            id = it.cellDivId()
            // initialize them as hidden to debounce -- client controls this logic.
            style =
                if (it.centerType == DEFAULT_APPT_TYPE.centerType) "display: block;"
                else "display: none;"
            div(classes = "checkboxHolder controlBox") {
              input(type = InputType.checkBox) { checked = true }
              p { +"Alert if available." }
            }
            div(classes = "$INNER_CELL_CLS noData") { p { +"No Data" } }
          }
        }
      }
      // footer elements
      div {
        id = "payBox"
        form(action = "https://www.paypal.com/donate", method = FormMethod.post) {
          target = "_blank"
          input(type = InputType.hidden, name = "business") { value = "NQA8Y4HF65LQN" }
          input(type = InputType.hidden, name = "currency_code") { value = "USD" }
          input(type = InputType.submit, classes = "myButton payLink", name = "submit") {
            value = "PAY ME!"
          }
        }
      }
      div {
        id = "logBoxHolder"
        h2 { +"FOMO LOG" }
        div { id = LOG_BOX_ID }
      }
    }
    script(src = "output.js") {}
  }
}

@ExperimentalCoroutinesApi
@ExperimentalTime
fun main() {

  val sendDelay = System.getenv("REFRESH_DELAY_MS")?.toLong() ?: 50

  // TODO can pause fetchers when no clients of its type are connected
  // TODO can throttle/speed up delay based on appointment scarcity
  val fetchers = ApptType.values().associateWith { MVCFetcher(it).apply { start() } }
  var curApptType = DEFAULT_APPT_TYPE

  mvcLogger.info { "Starting servers with refresh delay = $sendDelay ms" }

  embeddedServer(Netty, port = 8080, host = "127.0.0.1") {
        install(Compression) { gzip() }
        routing {
          get("/") { call.respondHtml(HttpStatusCode.OK, HTML::index) }
          static("/") { resources() }
        }
      }
      .start()

  embeddedServer(Netty, port = 8081, host = "127.0.0.1") {
        install(WebSockets) { pingPeriodMillis = 5000 }
        routing {
          webSocket("/ws") {
            val host = this.call.request.origin.remoteHost
            mvcLogger.info { "New connection from $host" }

            // burn one incoming frame to signify the client is ready
            incoming.receive()

            val lastSentTime = MVC.values().associateWith { Instant.DISTANT_PAST } as MutableMap
            var lastLogSentTime: Instant = Instant.DISTANT_PAST
            var hasWarned = false

            while (true) {
              // update current appointment type if client wants to
              incoming.receiveOrNull()?.let { frame ->
                when (frame) {
                  is Frame.Text -> {
                    val msg = Json.decodeFromString<MVCClientMsg>(frame.readText())
                    // NB. we should NOT check if the appt type has changed, since
                    // the client might be relying on us to resend the state when the
                    // frame is updated. We comply to avoid fragility.
                    // As Alexander of Macedon once said, "there is nothing to fear
                    // except getting stuck in an invalid state."
                    curApptType = msg.apptType
                    lastLogSentTime = Instant.DISTANT_PAST
                    lastSentTime.replaceAll { _, _ -> Instant.DISTANT_PAST }
                    mvcLogger.info { "$host has requested info for $curApptType" }
                  }
                  else -> mvcLogger.error { "Received bad frame type $frame" }
                }
              }

              val fetcher = fetchers[curApptType]!!

              // send appointment slots
              curApptType.centers.forEach { mvc ->
                fetcher.lastUpdated[mvc]?.let {
                  if (it > lastSentTime[mvc]!!) {
                    val now = Clock.System.now()
                    lastSentTime[mvc] = now
                    mvcLogger.debug { "Sending new for $mvc to $host" }
                    send(Json.encodeToString(fetcher.getForMVC(mvc)))
                  }
                }
                delay(sendDelay)
              }

              // send recent logs
              fetcher.updateLog.entries.forEach { (logTime, msg) ->
                if (logTime > lastLogSentTime) {
                  lastLogSentTime = logTime
                  send(
                      Json.encodeToString<MVCWsMsg>(
                          MVCLogLine(msg, logTime.toLocalDateTime(NJTZ).format(LOG_FORMAT))))
                }
              }
              delay(sendDelay)

              // send warnings
              val lastSuccAgo = Clock.System.now() - fetcher.lastSuccess
              if (lastSuccAgo > 5.seconds) {
                hasWarned = true
                send(
                    Json.encodeToString<MVCWsMsg>(
                        MVCServerStatus(
                            "No updates for ${lastSuccAgo.inSeconds.roundToInt()} seconds",
                            "warning")))
              } else if (hasWarned) {
                hasWarned = false
                send(Json.encodeToString<MVCWsMsg>(MVCServerStatus("Server connected.", "healthy")))
              }
              delay(sendDelay)
            }
          }
        }
      }
      .start(wait = true)
}
