import io.ktor.http.*
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
import org.apache.hc.client5.http.impl.classic.HttpClients

internal val mvcLogger = KotlinLogging.logger("MVC")

private const val QUERY_URL =
    "https://telegov.njportal.com/njmvc/CustomerCreateAppointments/GetNextAvailableDate"
private const val SCHEDULE_URL = "https://telegov.njportal.com/njmvc/AppointmentWizard"

private val NEXT_APT_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a")

fun LocalDateTime.format(formatter: DateTimeFormatter): String {
  return this.toJavaLocalDateTime().format(formatter)
}

@ExperimentalTime
object MVCFetcher {

  @Serializable private data class NextAppt(val next: String)

  // TODO add all appointment types
  private const val typeId = 11

  private val client = HttpClients.createDefault()
  private val availMap: MutableMap<MVC, LocalDateTime?> =
      MVC.values().associateWith { null }.toMutableMap()

  val lastUpdated: Map<MVC, Instant> = availMap.mapValues { Clock.System.now() }.toMutableMap()
  val updateLog: Map<Instant, String> =
      object : LinkedHashMap<Instant, String>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Instant, String>?): Boolean {
          return size > 10
        }
      }

  fun getForMVC(mvc: MVC): MVCWsMsg {
    availMap[mvc]?.let {
      return ApptAvail(mvc, it.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), apptUrl(mvc, it))
    }
        ?: run {
          return ApptTaken(mvc)
        }
  }
  var lastSuccess: Instant = Clock.System.now()
    private set
  private val queryDelay = System.getenv("QUERY_DELAY_MS")?.toLong() ?: 200
  init {
    mvcLogger.info { "Starting fetcher with delay = $queryDelay ms" }
  }

  private fun infoWithAppend(msg: String) {
    updateLog as MutableMap
    mvcLogger.info { msg }
    updateLog[Clock.System.now()] = msg
  }

  private fun watchdogLoop() {
    Clock.System.now().let {
      when {
        it - lastSuccess > 30.seconds -> exitProcess(-1)
        it - lastSuccess > 20.seconds ->
            mvcLogger.error { "More than 20 seconds since last update!" }
        it - lastSuccess > 10.seconds ->
            mvcLogger.warn { "More than 10 seconds since last update!" }
      }
    }
  }

  fun start() {
    lastUpdated as MutableMap

    infoWithAppend("Restarted.")

    thread(name = "watchdog") {
      while (true) {
        watchdogLoop()
        Thread.sleep(1000)
      }
    }

    thread(name = "fetcher", isDaemon = true) {
      val gets =
          MVC.values().associateWith {
            HttpGet("$QUERY_URL?appointmentTypeId=$typeId&locationId=${it.id}")
          }

      while (true) {
        MVC.values().forEach { mvc ->
          Thread.sleep(queryDelay)
          try {

            val get = gets[mvc]!!
            val resp = client.execute(get)

            if (resp.code != 200) {
              println("Got code ${resp.code}!")
              return@forEach
            }

            lastSuccess = Clock.System.now()

            // TEST
            val next =
                resp.entity.content.use {
                  Json.decodeFromString<NextAppt>(it.readAllBytes().decodeToString()).next
                }

            if (next == "No Appointments Available") {
              availMap.remove(mvc)?.also {
                lastUpdated[mvc] = Clock.System.now()
                infoWithAppend(
                    "Appointment for ${mvc.location} at ${it.format(NEXT_APT_FORMAT)} taken.")
              }
              return@forEach
            }
            val gotDate =
                JLDT.parse(next.removePrefix("Next Available: "), NEXT_APT_FORMAT)
                    .toKotlinLocalDateTime()

            availMap[mvc]?.let { curDate -> if (curDate <= gotDate) return@forEach }
            availMap[mvc] = gotDate
            lastUpdated[mvc] = Clock.System.now()

            infoWithAppend("New slot at ${mvc.location} at ${gotDate.format(NEXT_APT_FORMAT)}")
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
