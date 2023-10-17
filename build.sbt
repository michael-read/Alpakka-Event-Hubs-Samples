import com.lightbend.cinnamon.sbt.Cinnamon.CinnamonKeys.cinnamon

name := "Alpakka-Event-Hubs-Samples"

val AkkaVersion = "2.8.4"
val AkkaManagementVersion = "1.4.1"
val AkkaHttpVersion = "10.5.2"
val akkaDiagnosticsVersion = "2.0.0"
val akkaPersistenceR2DBC = "1.1.1"
val akkaCassandraVersion  = "1.1.1"
val MockitoVersion = "5.2.0"
val LombokVersion = ""

val LogbackVersion = "1.2.11"
val JupiterVersion = "5.9.2"
val AlpakkaEventHubsVersion = "2.0.0-M4"

val globalDockerBaseImage = "eclipse-temurin:17"
//val globalDockerBaseImage = "openjdk:11-slim"

ThisBuild / scalaVersion := "2.13.10"
ThisBuild / organization := "com.lightbend"
ThisBuild / Compile / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlog-reflective-calls",
  "-Xlint")
//ThisBuild / Compile / javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")
ThisBuild / Compile / javacOptions ++= Seq("--enable-preview", "--release", "17")
ThisBuild / Test / testOptions += Tests.Argument("-oDF")
ThisBuild / licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))

// we're relying on the new credential file format for lightbend.sbt as described
//  here -> https://www.lightbend.com/account/lightbend-platform/credentials, which
//  requires a commercial Lightbend Subscription.
val credentialFile = file("./lightbend.sbt")

def doesCredentialExist : Boolean = {
  import java.nio.file.Files
  val exists = Files.exists(credentialFile.toPath)
  println(s"doesCredentialExist: ($credentialFile) " + exists)
  exists
}

def commercialDependencies : Seq[ModuleID] = {
  import com.lightbend.cinnamon.sbt.Cinnamon.CinnamonKeys.cinnamon
  Seq(
    // BEGIN: this requires a commercial Lightbend Subscription
    Cinnamon.library.cinnamonAkkaHttp,
    Cinnamon.library.cinnamonAkka,
    Cinnamon.library.cinnamonAkkaTyped,
    Cinnamon.library.cinnamonAkkaStream,
    Cinnamon.library.cinnamonAkkaGrpc,
    Cinnamon.library.cinnamonAkkaPersistence,
    Cinnamon.library.cinnamonJvmMetricsProducer,
    Cinnamon.library.cinnamonSlf4jEvents,
    Cinnamon.library.cinnamonPrometheus,
    Cinnamon.library.cinnamonPrometheusHttpServer,
    Cinnamon.library.jmxImporter,
    "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
    "com.lightbend.akka" %% "akka-stream-azure-eventhubs" % AlpakkaEventHubsVersion,
    // END: this requires a commercial Lightbend Subscription
  )
}

def testDependencies : Seq[ModuleID] = {
  Seq(
    "org.projectlombok" % "lombok" % "1.18.26" % "provided",
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
    "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,

    "junit" % "junit" % "4.13.2" % Test,

    "org.junit.jupiter" % "junit-jupiter-api" % JupiterVersion % Test,
    "org.junit.jupiter" % "junit-jupiter-engine" % JupiterVersion % Test,

    "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion % Test,
    "org.scalatest" %% "scalatest" % "3.2.15" % Test,

/*
    "org.mockito" % "mockito-inline" % MockitoVersion % Test,
    "org.mockito" % "mockito-junit-jupiter" % MockitoVersion % Test,
*/

    "org.testcontainers" % "testcontainers" % "1.18.3" % Test,
    "org.testcontainers" % "pulsar" % "1.18.3" % Test

  )
}

def appDependencies : Seq[ModuleID] = {
  Seq(
    "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
//    "com.azure" % "azure-messaging-eventhubs" % "5.15.5",
    "com.azure" % "azure-messaging-eventhubs-checkpointstore-blob" % "1.16.6",
    "com.azure" % "azure-identity" % "1.10.1",
    "com.azure" % "azure-core-http-okhttp" % "1.11.7"
  )
}


lazy val root = (project in file("."))
//  .enablePlugins(DockerPlugin)
//  .enablePlugins(JavaAppPackaging)
  .enablePlugins(if (doesCredentialExist.booleanValue()) Cinnamon else Plugins.empty) // NOTE: Cinnamon requires a commercial Lightbend Subscription
  .enablePlugins(AkkaGrpcPlugin)
  .settings(
//    Docker / packageName := "akka-typed-blog-distributed-state/cluster",
    akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java),
    libraryDependencies ++= {
      if (doesCredentialExist.booleanValue()) {
        commercialDependencies ++ appDependencies
      }
      else {
        appDependencies
      }
    }
  )

fork := true