import kotlinx.serialization.Serializable

enum class MVCType {
  VC,
  LC;

  fun classString(): String {
    return "mvcType${name}"
  }
}

enum class MVC(val location: String, val zipCode: String, val centerType: MVCType) {
  // Licence centers
  BakersBasin("Baker's Basin", "08648", MVCType.LC),
  Bayonne("Bayonne", "07002", MVCType.LC),
  Camden("Camden", "08104", MVCType.LC),
  Cardiff("Cardiff", "08234-3935", MVCType.LC),
  Delanco("Delanco", "08075", MVCType.LC),
  Eatontown("Eatontown", "07724", MVCType.LC),
  Edison("Edison", "08817", MVCType.LC),
  Flemington("Flemington", "08551", MVCType.LC),
  Freehold("Freehold", "07728", MVCType.LC),
  Lodi("Lodi", "07644", MVCType.LC),
  Newark("Newark", "07114", MVCType.LC),
  NBergen("North Bergen", "07047", MVCType.LC),
  Oakland("Oakland", "07436", MVCType.LC),
  Paterson("Paterson", "07505", MVCType.LC),
  Rahway("Rahway", "07065", MVCType.LC),
  Randolph("Randolph", "07869", MVCType.LC),
  RGrande("Rio Grande", "08204", MVCType.LC),
  Salem("Salem", "08079", MVCType.LC),
  SPlainfield("South Plainfield", "07080", MVCType.LC),
  TRiver("Toms River", "08753", MVCType.LC),
  Vineland("Vineland", "08360", MVCType.LC),
  Wayne("Wayne", "07470", MVCType.LC),
  WDeptford("West Deptford", "08086", MVCType.LC),

  // Vehicle Centers
  CherryHill("Cherry Hill", "08002", MVCType.VC),
  EOrange("East Orange", "07018", MVCType.VC),
  Hazlet("Hazlet", "07730", MVCType.VC),
  JCity("Jersey City", "07307", MVCType.VC),
  Lakewood("Lakewood", "08701", MVCType.VC),
  Manahawkin("Manahawkin", "08050", MVCType.VC),
  Medford("Medford", "08055", MVCType.VC),
  Newton("Newton", "07860", MVCType.VC),
  Runnemede("Runnemede", "08078", MVCType.VC),
  Somerville("Somerville", "08876", MVCType.VC),
  SBrunswick("South Brunswick", "08810", MVCType.VC),
  Springfield("Springfiled", "07081", MVCType.VC),
  TRegional("Trenton Regional", "08666", MVCType.VC),
  Turnersville("Turnersville", "08012", MVCType.VC),
  Wallington("Wallington", "07057", MVCType.VC),
  Washington("Washington", "07882", MVCType.VC);

  fun cellDivId(): String {
    return "mvc_${zipCode}"
  }

  companion object {
    val byZip = values().associateBy { it.zipCode }
    init {
      // check there are no zip duplicates
      require(byZip.size == values().size)
    }
  }
}

@Serializable
enum class ApptType(
    val fullName: String,
    val id: Int,
    val centerType: MVCType,
    val queryDelay: Long = 750,
) {
  InitPerm("Initial Permit/Licence", 15, MVCType.LC, 150),
  CDL("CDL Permit or Endorsement", 14, MVCType.LC, 500),
  RealID("Real ID", 12, MVCType.LC, 150),
  NonDriver("Non-Driver ID", 16, MVCType.LC, 200),
  RenewLic("Licence/Non-Driver ID Renewal", 11, MVCType.LC, queryDelay = 150),
  RenewCDL("CDL Renewal", 6, MVCType.LC, 1000),
  OOSTransfer("Transfer From out of State", 7, MVCType.LC, 500),
  NewTitle("New Title or Registration", 8, MVCType.VC, 500),
  SeniorTitle("Senior New Title or Registration (65+)", 9, MVCType.VC, 1000),
  RegRenew("Registration Renewal", 10, MVCType.VC, 1000),
  TitleDup("Title Duplicate/Replacement", 13, MVCType.VC, 1000);

  fun getIdForMVC(mvc: MVC): Int? {
    return ID_MAP[id]!![mvc.zipCode]
  }

  val centers
    get() = ID_MAP[id]!!.keys.mapNotNull { MVC.byZip[it] }
}

val DEFAULT_APPT_TYPE = ApptType.RenewLic
