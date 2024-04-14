import sbt.Keys._

object ConsoleHelper {
  def prompt: String               = s"${Console.CYAN}>${Console.RESET} "
  def header(text: String): String = s"${Console.GREEN}$text${Console.RESET}"

  def item(text: String): String =
    s"${Console.RED}> ${Console.CYAN}$text${Console.RESET}"

  // format: off
  val welcomeMessage =
    onLoadMessage :=
      raw"""|${header(raw"""""")}
            |${header(raw"""DECREL - Declarative Programming with Relations""")}
            |${header(raw"""${version.value}""")}
            |
            |Useful sbt tasks:
            |${item("~compile")} - Compile all modules with file-watch enabled
            |${item("+test")} - Run the unit test suite
            |${item("fmt")} - Run scalafmt on the entire project
            |${item("+publishLocal")} - Publish decrel locally""".stripMargin
  // format: on
}
