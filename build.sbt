import sbt._
import Keys._

val commonSettings = Seq(
  scalacOptions := Seq(
    "-Xlint",
    "-unchecked",
    "-deprecation",
    "-feature"
  ),
  scalaVersion := "2.11.8",
  version := "1.0.0-SNAPSHOT"
)

val akkaVersion = "2.4.9"
val asyncVersion = "0.9.1"
val aceVersion = "1.2.2"
val domVersion = "0.9.1"
val scalatagsVersion = "0.6.0"
val upickleVersion = "0.4.1"

lazy val root = project.in(file("."))
  .aggregate(client, page, server, runtime)
  .settings(
    (resources in(server, Compile)) ++= {
      (managedClasspath in(runtime, Compile)).value.map(_.data) ++ Seq(
        (packageBin in(page, Compile)).value,
        (fastOptJS in(client, Compile)).value.data
      )
    }
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
      "org.scala-js" %%% "scalajs-dom" % domVersion,
      "com.lihaoyi" %%% "scalatags" % scalatagsVersion,
      "com.lihaoyi" %%% "upickle" % upickleVersion,
      "com.github.marklister" %%% "base64" % "0.2.2",
      "org.scala-lang.modules" %% "scala-async" % asyncVersion % "provided"
    ),
    // rename output always to -opt.js
    artifactPath in(Compile, fastOptJS) := ((crossTarget in(Compile, fastOptJS)).value /
      ((moduleName in fastOptJS).value + "-opt.js")),
    relativeSourceMaps := true
  )

lazy val page = project
  .enablePlugins(ScalaJSPlugin)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % domVersion,
      "com.lihaoyi" %%% "scalatags" % scalatagsVersion
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
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "com.typesafe.akka" %% "akka-http-core" % akkaVersion,
      "com.typesafe.akka" %% "akka-http-experimental" % akkaVersion,
      "ch.qos.logback" % "logback-classic" % "1.1.3",
      "org.scala-js" % "scalajs-compiler" % scalaJSVersion cross CrossVersion.full,
      "org.scala-js" %% "scalajs-tools" % scalaJSVersion,
      "org.scala-lang.modules" %% "scala-async" % asyncVersion % "provided",
      "com.lihaoyi" %% "scalatags" % scalatagsVersion,
      "org.webjars" % "ace" % aceVersion,
      "org.webjars" % "normalize.css" % "2.1.3",
      "com.lihaoyi" %% "upickle" % upickleVersion,
      "com.github.marklister" %% "base64" % "0.2.2",
      "ch.megard" %% "akka-http-cors" % "0.1.4",
      "org.apache.maven" % "maven-artifact" % "3.3.9",
      "io.get-coursier" %% "coursier" % "1.0.0-M13",
      "io.get-coursier" %% "coursier-cache" % "1.0.0-M13",
      "org.xerial.snappy" % "snappy-java" % "1.1.2.1",
      "org.xerial.larray" %% "larray" % "0.3.4",
      "com.lihaoyi" %% "utest" % "0.3.0" % "test"
    ),
    resolvers += "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
    testFrameworks += new TestFramework("utest.runner.Framework"),
    javaOptions in Revolver.reStart ++= Seq("-Xmx2g", "-Xss4m"),
    javaOptions in Universal ++= Seq("-J-Xss4m"),
    resourceGenerators in Compile += Def.task {
      // store build version in a property file
      val file = (resourceManaged in Compile).value / "version.properties"
      val contents =
        s"""
           |version=${version.value}
           |scalaVersion=${scalaVersion.value}
           |scalaJSVersion=$scalaJSVersion
           |aceVersion=$aceVersion
           |""".stripMargin
      IO.write(file, contents)
      Seq(file)
    }.taskValue,
    scriptClasspath := Seq("../config/") ++ scriptClasspath.value,
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
        namespace = Some("scalafiddle"),
        repository = "scalafiddle-core",
        tag = Some("latest")
      ),
      ImageName(
        namespace = Some("scalafiddle"),
        repository = "scalafiddle-core",
        tag = Some(version.value)
      )
    )
  )
