package scalafiddle.router

import scalafiddle.shared._
import upickle.Js
import upickle.default._

object Librarian {

  case class LibraryVersion(
      version: String,
      scalaVersions: Seq[String],
      extraDeps: Seq[String],
      organization: Option[String],
      artifact: Option[String],
      doc: Option[String],
      jsDeps: List[JSLib],
      cssDeps: List[CSSLib]
  )

  private val depRE = """([^ %]+) *% *([^ %]+) *% *([^ %]+)""".r

  private implicit val jsLibVersionReader: Reader[JSLib] = Reader[JSLib] {
    case Js.Str(depRE(name, version, url)) =>
      JSLib(name, version, url)
  }

  private implicit val cssLibVersionReader: Reader[CSSLib] = Reader[CSSLib] {
    case Js.Str(depRE(name, version, url)) =>
      CSSLib(name, version, url)
  }

  implicit val libraryVersionReader: Reader[LibraryVersion] = Reader[LibraryVersion] {
    case Js.Obj(valueSeq @ _*) =>
      val values = valueSeq.toMap
      LibraryVersion(
        readJs[String](values("version")),
        readJs[Seq[String]](values("scalaVersions")),
        readJs[Seq[String]](values.getOrElse("extraDeps", Js.Arr())),
        values.get("organization").map(readJs[String]),
        values.get("artifact").map(readJs[String]),
        values.get("doc").map(readJs[String]),
        values.get("jsDeps").map(readJs[List[JSLib]]).getOrElse(Nil),
        values.get("cssDeps").map(readJs[List[CSSLib]]).getOrElse(Nil)
      )
  }

  case class LibraryDef(
      name: String,
      organization: String,
      artifact: String,
      doc: String,
      versions: Seq[LibraryVersion],
      compileTimeOnly: Boolean
  )

  case class LibraryGroup(
      group: String,
      libraries: Seq[LibraryDef]
  )

  private val repoSJSRE = """([^ %]+) *%%% *([^ %]+) *% *([^ %]+)""".r
  private val repoRE    = """([^ %]+) *%% *([^ %]+) *% *([^ %]+)""".r

  def loadLibraries(data: String): Map[String, Set[ExtLib]] = {
    val libGroups = read[Seq[LibraryGroup]](data)
    (for {
      group      <- libGroups
      lib        <- group.libraries
      versionDef <- lib.versions
    } yield {
      versionDef.scalaVersions.flatMap { scalaVersion =>
        val extraDeps = versionDef.extraDeps.map {
          case repoSJSRE(grp, artifact, version) =>
            scalaVersion -> ExtLib(grp, artifact, version, compileTimeOnly = false)
          case repoRE(grp, artifact, version) =>
            scalaVersion -> ExtLib(grp, artifact, version, compileTimeOnly = true)
        }
        Seq(
          scalaVersion -> ExtLib(
            versionDef.organization.getOrElse(lib.organization),
            versionDef.artifact.getOrElse(lib.artifact),
            versionDef.version,
            lib.compileTimeOnly,
            versionDef.jsDeps,
            versionDef.cssDeps
          )) ++ extraDeps
      }
    }).flatten.groupBy(_._1).map { case (version, libs) => version -> libs.map(_._2).toSet }
  }
}
