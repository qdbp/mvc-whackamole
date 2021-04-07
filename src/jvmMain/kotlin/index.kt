import kotlin.random.Random
import kotlinx.html.*

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
      // log box elements
      div {
        id = "logBoxHolder"
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
        h2 { +"FOMO LOG" }
        div { id = LOG_BOX_ID }
      }
    }
    // footer elements
    div {
      id = "footerBox"
      form(action="http://enaumov.me") {
        input(type=InputType.submit, classes="myButton siteLink") {
          value = "VISIT MY HOME PAGE"
        }
      }
    }
    script(src = "output.js") {}
  }
}
