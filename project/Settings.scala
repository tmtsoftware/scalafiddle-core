/**
  * Application settings. Configure the build for your application here.
  * You normally don't have to touch the actual build definition after this.
  */
object Settings {
  /** Options for the scala compiler */
  val scalacOptions = Seq(
    "-Xlint",
    "-unchecked",
    "-deprecation",
    "-feature"
  )

  /** Declare global dependency versions here to avoid mismatches in multi part dependencies */
  object versions {
    val fiddle = "1.0.4"
    val scalatest = "3.0.0"
    val macroParadise = "2.1.0"
    val akka = "2.4.10"
    val upickle = "0.4.1"
    val ace = "1.2.2"
    val dom = "0.9.1"
    val scalatags = "0.6.0"
    val async = "0.9.1"
    val coursier = "1.0.0-M14"
  }

}
