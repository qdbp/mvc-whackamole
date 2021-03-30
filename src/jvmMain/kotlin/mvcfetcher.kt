import java.time.LocalDateTime as JLDT
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.concurrent.thread
import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.impl.classic.HttpClients

internal val mvcLogger = KotlinLogging.logger("MVC")

private const val QUERY_URL =
    "http://telegov.njportal.com/njmvc/CustomerCreateAppointments/GetNextAvailableDate"
private const val SCHEDULE_URL = "https://telegov.njportal.com/njmvc/AppointmentWizard"

private val NEXT_APT_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a")
private val DISPLAY_FMT = DateTimeFormatter.ofPattern("MMM dd, hh:mm a")

fun LocalDateTime.format(formatter: DateTimeFormatter): String {
  return this.toJavaLocalDateTime().format(formatter)
}

object MVCFetcher {

  @Serializable private data class NextAppt(val next: String)

  // TODO add all appointment types
  private const val typeId = 11
  private val client = HttpClients.createDefault()

  private val availMap: MutableMap<MVC, LocalDateTime?> =
      MVC.values().associateWith { null }.toMutableMap()

  val lastUpdated: Map<MVC, Instant> = availMap.mapValues { Clock.System.now() }.toMutableMap()

  fun getForMVC(mvc: MVC): ApptDetails {
    availMap[mvc]?.let {
      return ApptDetails(mvc, it.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), apptUrl(mvc, it))
    }
        ?: run {
          return ApptDetails(mvc, null, null)
        }
  }

  fun start() {
    lastUpdated as MutableMap
    thread {
      while (true) {
        MVC.values().forEach { mvc ->
          Thread.sleep(100)
          try {
            val get = HttpGet("$QUERY_URL?appointmentTypeId=$typeId&locationId=${mvc.id}")
            val resp = client.execute(get)

            if (resp.code != 200) {
              println("Got code ${resp.code}!")
              return@forEach
            }

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
