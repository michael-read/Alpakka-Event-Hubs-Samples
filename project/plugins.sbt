ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.16")
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "2.3.4")
//addSbtPlugin("com.lightbend.cinnamon" % "sbt-cinnamon" % "2.17.3")
addSbtPlugin("com.lightbend.cinnamon" % "sbt-cinnamon" % "2.17.4-20230526-9b952fc")
