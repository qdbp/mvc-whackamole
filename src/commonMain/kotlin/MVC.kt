enum class MVC(val location: String, val id: Int) {
  BakersBasin("Baker's Basin", 101),
  Flemington("Flemington", 111),
  Edison("Edison", 110),
  Delanco("Delanco", 107),
  Bayonne("Bayonne", 102),
  Camden("Camden", 104),
  Cardiff("Cardiff", 105),
  Eatontoen("Eatontown", 108),
  Freehold("Freehold", 113),
  Lodi("Lodi", 114),
  Newark("Newark", 116),
  NBergen("North Bergen", 117),
  Oakland("Oakland", 119),
  Paterson("Paterson", 120),
  Rahway("Rahway", 122),
  Randolph("Randolph", 123),
  RGrande("Rio Grande", 103),
  Salem("Salem", 106),
  TRiver("Toms River", 112),
  SPlainfield("South Plainfield", 109),
  Vineland("Vineland", 115),
  Wayne("Wayne", 118),
  WDeptford("West Deptford", 121);

  fun cellDivId(): String {
    return "mvc_${id}"
  }
}
