import java.time.LocalDateTime as JLDT
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime
import kotlin.time.seconds
import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.util.TimeValue
import org.apache.hc.core5.util.Timeout

internal val mvcLogger = KotlinLogging.logger("MVC")

private const val QUERY_URL =
    "https://telegov.njportal.com/njmvc/CustomerCreateAppointments/GetNextAvailableDate"
private const val SCHEDULE_URL = "https://telegov.njportal.com/njmvc/AppointmentWizard"

private val NEXT_APT_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a")
private val DISPLAY_FMT = DateTimeFormatter.ofPattern("MMM dd, hh:mm a")

fun LocalDateTime.format(formatter: DateTimeFormatter): String {
  return this.toJavaLocalDateTime().format(formatter)
}

@ExperimentalTime
object MVCFetcher {

  @Serializable private data class NextAppt(val next: String)

  // TODO add all appointment types
  private const val typeId = 11

  private val requestConfig =
      RequestConfig.custom().setConnectionRequestTimeout(Timeout.ofSeconds(5)).build()


  private val client =
      HttpClients.custom()
          .setDefaultRequestConfig(requestConfig)
          .setKeepAliveStrategy { _, _ -> TimeValue.ofSeconds(10) }
          .build()

  private val availMap: MutableMap<MVC, LocalDateTime?> =
      MVC.values().associateWith { null }.toMutableMap()

  val lastUpdated: Map<MVC, Instant> = availMap.mapValues { Clock.System.now() }.toMutableMap()
  private val queryDelay = System.getenv("QUERY_DELAY_MS")?.toLong() ?: 200

  init {
    mvcLogger.info { "Starting fetcher with delay = $queryDelay ms" }
  }

  fun getForMVC(mvc: MVC): ApptDetails {
    availMap[mvc]?.let {
      return ApptDetails(mvc, it.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), apptUrl(mvc, it))
    }
        ?: run {
          return ApptDetails(mvc, null, null)
        }
  }

  private var lastSuccess: Instant = Clock.System.now()

  fun start() {
    lastUpdated as MutableMap
    thread(name = "watchdog") {
      while (true) {
        Clock.System.now().let {
          when {
            it - lastSuccess > 15.seconds -> exitProcess(-1)
            it - lastSuccess > 10.seconds ->
                mvcLogger.error { "More than 10 seconds since last update!" }
            it - lastSuccess > 5.seconds ->
                mvcLogger.warn { "More than 5 seconds since last update!" }
          }
        }
        Thread.sleep(1000)
      }
    }

    thread(name = "fetcher", isDaemon = true) {
      while (true) {
        MVC.values().forEach { mvc ->
          Thread.sleep(queryDelay)
          try {
            val get = HttpGet("$QUERY_URL?appointmentTypeId=$typeId&locationId=${mvc.id}")
            val resp = client.execute(get)

            if (resp.code != 200) {
              println("Got code ${resp.code}!")
              return@forEach
            }

            lastSuccess = Clock.System.now()

            // TEST
            val next =
                Json.decodeFromString<NextAppt>(resp.entity.content.readAllBytes().decodeToString())
                    .next

            if (next == "No Appointments Available") {
              availMap.remove(mvc)?.also {
                lastUpdated[mvc] = Clock.System.now()
                mvcLogger.info {
                  "Appointment for ${mvc.location} at ${it.format(NEXT_APT_FORMAT)} taken."
                }
              }
              return@forEach
            }
            val gotDate =
                JLDT.parse(next.removePrefix("Next Available: "), NEXT_APT_FORMAT)
                    .toKotlinLocalDateTime()

            availMap[mvc]?.let { curDate -> if (curDate <= gotDate) return@forEach }
            availMap[mvc] = gotDate
            lastUpdated[mvc] = Clock.System.now()

            mvcLogger.info { "New slot at ${mvc.location} at ${gotDate.format(NEXT_APT_FORMAT)}" }
          } catch (e: Exception) {
            mvcLogger.error { "Caught $e" }
            Thread.sleep(1000)
            return@forEach
          }
        }
      }
    }
  }

  private fun apptUrl(mvc: MVC, date: LocalDateTime): String {
    val minString = "${date.minute}".padStart(2, '0')
    return "$SCHEDULE_URL/$typeId/${mvc.id}/" +
        "${date.format(DateTimeFormatter.ISO_DATE)}/${date.hour}$minString"
  }
}
