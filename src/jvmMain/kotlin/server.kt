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
import kotlin.random.Random
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.html.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
                "Max 3 seconds delay from slot opening, or your money back.")
          }
        }
        img(classes = "bestFlag", src = "/nj.png")
      }
      div { id = "infoBox" }
      div {
        id = "cellHolder"
        MVC.values().forEach {
          div {
            id = it.cellDivId()
            div(classes = "innerCell") { p { +"No Data" } }
          }
        }
      }
      div {
        id = "payBox"
        form(action = "https://www.paypal.com/donate", method = FormMethod.post) {
          target = "_blank"
          input(type = InputType.hidden, name = "business") {
            value ="NQA8Y4HF65LQN"
          }
          input(type= InputType.hidden, name="currency_code") {
            value="USD"
          }
          input(type=InputType.submit, classes="myButton payLink", name="submit") {
            value="PAY ME!"
          }

        }
      }
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

            while (true) {
              MVC.values().forEach { mvc ->
                MVCFetcher.lastUpdated[mvc]?.let {
                  if (it > lastSentTime[mvc]!!) {
                    lastSentTime[mvc] = Clock.System.now()
                    mvcLogger.debug { "Sending new for $mvc to $host" }
                    send(Json.encodeToString(MVCFetcher.getForMVC(mvc)))
                  }
                }
                delay(sendDelay)
              }
            }
          }
        }
      }
      .start(wait = true)
}
