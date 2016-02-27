import sbt._
import Keys._

val commonSettings = Seq(
  scalacOptions := Seq(
    "-Xlint",
    "-unchecked",
    "-deprecation",
    "-feature"
  ),
  scalaVersion := "2.11.7",
  version := "1.0.0-SNAPSHOT"
)

lazy val root = project.in(file("."))
  .aggregate(client, page, server, runtime)
  .settings(
    (resources in(server, Compile)) ++= {
      (managedClasspath in(runtime, Compile)).value.map(_.data) ++ Seq(
        (packageBin in(page, Compile)).value,
        (fastOptJS in(client, Compile)).value.data
      )
    },
    scalaVersion := "2.11.7"
  )

lazy val shared = project
  .enablePlugins(ScalaJSPlugin)
  .settings(commonSettings)

lazy val client = project
  .dependsOn(page, shared)
  .enablePlugins(ScalaJSPlugin)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "0.9.0",
      "com.lihaoyi" %%% "scalatags" % "0.5.4",
      "com.lihaoyi" %%% "upickle" % "0.3.8",
      "org.scala-lang.modules" %% "scala-async" % "0.9.1" % "provided"
    ),
    relativeSourceMaps := true
  )

lazy val page = project
  .enablePlugins(ScalaJSPlugin)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "0.9.0",
      "com.lihaoyi" %%% "scalatags" % "0.5.4"
    )
  )

lazy val runtime = project
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-js" %% "scalajs-library" % scalaJSVersion,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value
    )
  )

lazy val server = project
  .dependsOn(shared)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(sbtdocker.DockerPlugin)
  .settings(commonSettings)
  .settings(Revolver.settings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-compiler" % scalaVersion.value,
      "com.typesafe.akka" %% "akka-actor" % "2.3.2",
      "io.spray" %% "spray-can" % "1.3.3",
      "io.spray" %% "spray-client" % "1.3.3",
      "io.spray" %% "spray-caching" % "1.3.3",
      "io.spray" %% "spray-httpx" % "1.3.3",
      "io.spray" %% "spray-routing" % "1.3.3",
      "org.scala-js" % "scalajs-compiler" % scalaJSVersion cross CrossVersion.full,
      "org.scala-js" %% "scalajs-tools" % scalaJSVersion,
      "org.scala-lang.modules" %% "scala-async" % "0.9.1" % "provided",
      "com.lihaoyi" %% "scalatags" % "0.5.4",
      "org.webjars" % "ace" % "1.2.2",
      "org.webjars" % "normalize.css" % "2.1.3",
      "com.lihaoyi" %% "upickle" % "0.3.8",
      "com.lihaoyi" %% "utest" % "0.3.0" % "test"
    ),
    resolvers += "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
    testFrameworks += new TestFramework("utest.runner.Framework"),
    javaOptions in Revolver.reStart += "-Xmx2g",
    resourceGenerators in Compile += Def.task {
      // store build version in a property file
      val file = (resourceManaged in Compile).value / "version.properties"
      val contents =s"""
           |version=${version.value}
           |scalaVersion=${scalaVersion.value}
           |scalaJSVersion=$scalaJSVersion
           |""".stripMargin
      IO.write(file, contents)
      Seq(file)
    }.taskValue,
    dockerfile in docker := {
      val appDir: File = stage.value
      val targetDir = "/app"

      new Dockerfile {
        from("java:8")
        entryPoint(s"$targetDir/bin/${executableScriptName.value}")
        copy(appDir, targetDir)
        expose(8080)
      }
    },
    imageNames in docker := Seq(
      ImageName(
        namespace = Some("ochrons"),
        repository = "scalafiddle",
        tag = Some("latest")
      ),
      ImageName(
        namespace = Some("ochrons"),
        repository = "scalafiddle",
        tag = Some(version.value)
      )
    )
  )
