lazy val root = (project in file(".")).aggregate(parent, child)
lazy val parent = project
lazy val child = project.enablePlugins(JmhPlugin).dependsOn(parent)
