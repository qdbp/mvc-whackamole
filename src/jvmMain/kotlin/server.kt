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
              if (lastSuccAgo > 10.seconds) {
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
