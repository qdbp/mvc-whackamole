import io.ktor.application.*
import io.ktor.features.*
import io.ktor.html.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.*
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.ExperimentalTime
import kotlin.time.seconds
import kotlinx.coroutines.delay
import kotlinx.datetime.*
import kotlinx.html.*
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
    div {
      id = "root"
      div {
        id = "titleHolder"
        img(classes = "bestFlag", src = "/nj.png")
        div {
          h1(classes = "title") { +"NJ MVC LICENCE RENEWAL WHACKAMOLE" }
          p(classes = "title subtitle") { +motds[Random.nextInt(motds.size)] }
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
      div { id = "infoBox" }
      div {
        id = "cellHolder"
        div(classes = "outerCell controlBox") {
          id = "datePicker"
          h3 { +"Alert date." }
          hr {}
          input(type = InputType.date) { id = "datePickerInput" }
          p(classes = "info") { +"Only appointments on or before this date will alert." }
        }
        MVC.values().forEach {
          div(classes = "outerCell") {
            id = it.cellDivId()
            div(classes = "checkboxHolder controlBox") {
              input(type = InputType.checkBox) { checked = true }
              p { +"Alert if available." }
            }
            div(classes = "innerCell noData") { p { +"No Data" } }
          }
        }
      }
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
      div { id = "logBox" }
    }
    script(src = "output.js") {}
  }
}

@ExperimentalTime
fun main() {

  MVCFetcher.start()

  val sendDelay = System.getenv("REFRESH_DELAY_MS")?.toLong() ?: 50

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

            // burn one incoming frame to signify the client is ready
            incoming.receive()
            val lastSentTime = MVC.values().associateWith { Instant.DISTANT_PAST } as MutableMap
            var lastLogSentTime: Instant = Instant.DISTANT_PAST
            var hasWarned: Boolean = false

            while (true) {
              // send appointment slots
              MVC.values().forEach { mvc ->
                MVCFetcher.lastUpdated[mvc]?.let {
                  if (it > lastSentTime[mvc]!!) {
                    val now = Clock.System.now()
                    lastSentTime[mvc] = now
                    mvcLogger.debug { "Sending new for $mvc to $host" }
                    send(Json.encodeToString(MVCFetcher.getForMVC(mvc)))
                  }
                }
                delay(sendDelay)
              }

              // send recent logs
              MVCFetcher.updateLog.entries.forEach { (logTime, msg) ->
                if (logTime > lastLogSentTime) {
                  lastLogSentTime = logTime
                  send(
                      Json.encodeToString<MVCWsMsg>(
                          MVCLogLine(msg, logTime.toLocalDateTime(NJTZ).format(LOG_FORMAT))))
                }
              }
              delay(sendDelay)

              // send warnings
              val lastSuccAgo = Clock.System.now() - MVCFetcher.lastSuccess
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
