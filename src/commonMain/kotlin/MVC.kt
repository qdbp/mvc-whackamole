import kotlinx.serialization.Serializable

enum class MVCType {
  VC,
  LC;

  fun classString(): String {
    return "mvcType${name}"
  }
}

enum class MVC(val location: String, val id: Int, val centerType: MVCType) {
  // Licence centers
  BakersBasin("Baker's Basin", 101, MVCType.LC),
  Flemington("Flemington", 111, MVCType.LC),
  Edison("Edison", 110, MVCType.LC),
  Delanco("Delanco", 107, MVCType.LC),
  Bayonne("Bayonne", 102, MVCType.LC),
  Camden("Camden", 104, MVCType.LC),
  Cardiff("Cardiff", 105, MVCType.LC),
  Eatontoen("Eatontown", 108, MVCType.LC),
  Freehold("Freehold", 113, MVCType.LC),
  Lodi("Lodi", 114, MVCType.LC),
  Newark("Newark", 116, MVCType.LC),
  NBergen("North Bergen", 117, MVCType.LC),
  Oakland("Oakland", 119, MVCType.LC),
  Paterson("Paterson", 120, MVCType.LC),
  Rahway("Rahway", 122, MVCType.LC),
  Randolph("Randolph", 123, MVCType.LC),
  RGrande("Rio Grande", 103, MVCType.LC),
  Salem("Salem", 106, MVCType.LC),
  TRiver("Toms River", 112, MVCType.LC),
  SPlainfield("South Plainfield", 109, MVCType.LC),
  Vineland("Vineland", 115, MVCType.LC),
  Wayne("Wayne", 118, MVCType.LC),
  WDeptford("West Deptford", 121, MVCType.LC),

  // Vehicle Centers
  CherryHill("Cherry Hill", 30, MVCType.VC),
  EOrange("East Orange", 31, MVCType.VC),
  Hazlet("Hazlet", 32, MVCType.VC),
  JCity("Jersey City", 33, MVCType.VC),
  Lakewood("Lakewood", 34, MVCType.VC),
  Manahawkin("Manahawkin", 35, MVCType.VC),
  Medford("Medford", 36, MVCType.VC),
  Newton("Newton", 37, MVCType.VC),
  Runnemede("Runnemede", 38, MVCType.VC),
  Somerville("Somerville", 39, MVCType.VC),
  SBrunswick("South Brunswick", 40, MVCType.VC),
  Springfield("Springfiled", 41, MVCType.VC),
  TRegional("Trenton Regional", 42, MVCType.VC),
  Turnersville("Turnersville", 43, MVCType.VC),
  Wallington("Wallington", 44, MVCType.VC),
  Washington("Washington", 45, MVCType.VC);

  fun cellDivId(): String {
    return "mvc_${id}"
  }
}

@Serializable
enum class ApptType(
    val fullName: String,
    val id: Int,
    val centerType: MVCType,
    val queryDelay: Long = 500,
) {
  InitPerm("Initial Permit/Licence", 15, MVCType.LC),
  CDL("CDL Permit or Endorsement", 14, MVCType.LC),
  RealID("Real ID", 12, MVCType.LC),
  NonDriver("Non-Driver ID", 16, MVCType.LC),
  RenewLic("Licence/Non-Driver ID Renewal", 11, MVCType.LC, queryDelay = 150),
  RenewCDL("CDL Renewal", 6, MVCType.LC),
  OOSTransfer("Transfer From out of State", 7, MVCType.LC),
  NewTitle("New Title or Registration", 8, MVCType.VC),
  SeniorTitle("Senior New Title or Registration (65+)", 9, MVCType.VC),
  RegRenew("Registration Renewal", 10, MVCType.VC),
  TitleDup("Title Duplicate/Replacement", 13, MVCType.VC);

  val centers
    get() = if (centerType == MVCType.LC) LICENCE_CENTERS else VEHICLE_CENTERS
}

val LICENCE_CENTERS = MVC.values().filter { it.centerType == MVCType.LC }
val VEHICLE_CENTERS = MVC.values().filter { it.centerType == MVCType.VC }
val DEFAULT_APPT_TYPE = ApptType.RenewLic
