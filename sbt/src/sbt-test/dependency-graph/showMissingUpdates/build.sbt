scalaVersion := "2.9.2"

libraryDependencies +=
  "at.blub" % "blib" % "1.2.3" % "test"

TaskKey[Unit]("check") := {
  val report = updateFull.value
  val graph = (Test / dependencyTree / asString).value

  def sanitize(str: String): String = str.split('\n').drop(1).mkString("\n")
  val expectedGraph =
    """default:default-91180e_2.9.2:0.1-SNAPSHOT
      |  +-%sat.blub:blib:1.2.3 (error: not found)%s
      |  """.stripMargin.format(scala.Console.RED, scala.Console.RESET)
  require(sanitize(graph) == sanitize(expectedGraph), "Graph for report %s was '\n%s' but should have been '\n%s'" format (report, sanitize(graph), sanitize(expectedGraph)))
  ()
}
