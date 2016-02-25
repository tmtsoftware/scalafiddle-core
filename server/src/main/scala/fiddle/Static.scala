package fiddle

import scalatags.Text.all._
import scalatags.Text.svgTags.{svg, use}
import scalatags.Text.{tags2, svgAttrs => svga}
import scala.collection.concurrent.TrieMap
import scala.reflect.io.Streamable
import java.security.MessageDigest

import scala.scalajs.niocharset.StandardCharsets

object Static {
  val aceFiles = Seq(
    "/META-INF/resources/webjars/ace/1.2.2/src-min/ace.js",
    "/META-INF/resources/webjars/ace/1.2.2/src-min/ext-language_tools.js",
    "/META-INF/resources/webjars/ace/1.2.2/src-min/ext-static_highlight.js",
    "/META-INF/resources/webjars/ace/1.2.2/src-min/mode-scala.js",
    "/META-INF/resources/webjars/ace/1.2.2/src-min/theme-eclipse.js",
    "/META-INF/resources/webjars/ace/1.2.2/src-min/theme-tomorrow_night_eighties.js"
  )

  val cssFiles = Seq(
    "/META-INF/resources/webjars/normalize.css/2.1.3/normalize.css",
    "/common.css"
  )

  val cache = TrieMap.empty[Seq[String], (String, Array[Byte])]

  final val layoutRE = """([vh])(\d\d)""".r

  def page(arg: String, srcFiles: Seq[String], paramMap: Map[String, String]) = {
    val customStyle = paramMap.getOrElse("style", "")
    val themeCSS = paramMap.get("theme") match {
      case Some("dark") => "/styles-dark.css"
      case _ => "/styles-light.css"
    }
    val allJS = joinResources(aceFiles ++ srcFiles, ".js", ";\n")
    val allCSS = joinResources(cssFiles :+ themeCSS, ".css", "\n")
    val jsURLs = s"/cache/$allJS" +: Config.extJS
    val cssURLs = s"/cache/$allCSS" +: Config.extCSS

    val layout = paramMap.get("layout") match {
      case Some(layoutRE(direction, ratio)) =>
        val editorSize = ratio.toInt
        val outputSize = 100 - editorSize
        val responsive =
          s"""
             |@media only screen and (max-device-width: 768px) {
             |    #editorWrap {
             |        top: 0;
             |        bottom: $outputSize%;
             |        left: 0;
             |        right: 0;
             |    }
             |
             |    #sandbox {
             |        top: $editorSize%;
             |        bottom: 0;
             |        left: 0;
             |        right: 0;
             |        border-top: 1px solid;
             |        border-left-width: 0;
             |    }
             |}
          """.stripMargin
        direction match {
          case "h" =>

            s"""
               |#editorWrap {
               |    top: 0;
               |    bottom: 0;
               |    left: 0;
               |    right: $outputSize%;
               |}
               |#sandbox {
               |    top: 0;
               |    bottom: 0;
               |    left: $editorSize%;
               |    right: 0;
               |    border-color: #333333;
               |    border-left: 1px solid;
               |}
              """.stripMargin + responsive
          case "v" =>

            s"""
               |#editorWrap {
               |    top: 0;
               |    bottom: $outputSize%;
               |    left: 0;
               |    right: 0;
               |}
               |
               |#sandbox {
               |    top: $editorSize%;
               |    bottom: 0;
               |    left: 0;
               |    right: 0;
               |    border-top: 1px solid;
               |    border-left-width: 0;
               |}
               """.stripMargin + responsive
        }
      case _ => ""
    }

    "<!DOCTYPE html>" + html(
      head(
        meta(charset := "utf-8"),
        meta(name := "viewport", content := "width=device-width, initial-scale=1"),
        tags2.title("ScalaFiddle"),
        for (jsURL <- jsURLs) yield script(`type` := "application/javascript", src := jsURL),
        for (cssURL <- cssURLs) yield link(rel := "stylesheet", href := cssURL),
        scalatags.Text.tags2.style(raw(s"""#output{$customStyle}.ace_editor{$customStyle}""")),
        scalatags.Text.tags2.style(raw(layout))
      ),
      body(
        raw(
          """
            |<svg xmlns="http://www.w3.org/2000/svg">
            | <symbol id="sym_help" viewBox="0 0 24 24">
            |   <g>
            |     <circle cx=12 cy=12 r=12 fill="white" fill-opacity="0"/>
            |  		<path id="circle" style="fill-rule:evenodd;clip-rule:evenodd;" d="M12.001,2.085c-5.478,0-9.916,4.438-9.916,9.916    c0,5.476,4.438,9.914,9.916,9.914c5.476,0,9.914-4.438,9.914-9.914C21.915,6.523,17.477,2.085,12.001,2.085z M12.002,20.085    c-4.465,0-8.084-3.619-8.084-8.083c0-4.465,3.619-8.084,8.084-8.084c4.464,0,8.083,3.619,8.083,8.084    C20.085,16.466,16.466,20.085,12.002,20.085z"/>
            |  		<g>
            |  			<path style="fill-rule:evenodd;clip-rule:evenodd;" d="M11.766,6.688c-2.5,0-3.219,2.188-3.219,2.188l1.411,0.854     c0,0,0.298-0.791,0.901-1.229c0.516-0.375,1.625-0.625,2.219,0.125c0.701,0.885-0.17,1.587-1.078,2.719     C11.047,12.531,11,15,11,15h1.969c0,0,0.135-2.318,1.041-3.381c0.603-0.707,1.443-1.338,1.443-2.494S14.266,6.688,11.766,6.688z"/>
            |  			<rect x="11" y="16" style="fill-rule:evenodd;clip-rule:evenodd;" width="2" height="2"/>
            |  		</g>
            |  	</g>
            | </symbol>
            | <symbol id="sym_run" viewBox="0 0 21 21">
            |   <polygon points="3,1 18,10 3,19"/>
            | </symbol>
            | <symbol id="sym_reset" viewBox="0 0 500 500">
            |   <circle cx=250 cy=250 r=250 fill="white" fill-opacity="0"/>
            |   <path d= "M492.1,213.8h-62.2C412.6,111.5,323.6,33.5,216.5,33.5C97,33.5,0.1,130.5,0.1,250S97,
            |            466.5,216.5,466.5 c54.5,0,104.3-20.2,142.3-53.4L314,356.2c-25.7,23.6-59.9,38.1-97.4,
            |            38.1c-79.6,0-144.3-64.7-144.3-144.3s64.7-144.3,144.3-144.3 c67.1,0,123.4,46,139.5,
            |            108.1h-63c-7.9,0-10.3,5.1-5.3,11.2l95.8,117.9c5,6.2,13.1,6.2,18.2,0L497.6,225 C502.4,
            |            218.9,500,213.8,492.1,213.8z"/>
            | </symbol>
            | <symbol id="sym_upload" viewBox="0 0 16 16">
            |  <path d="M7 9H5l3-3 3 3H9v5H7V9z m5-4c0-0.44-0.91-3-4.5-3-2.42 0-4.5 1.92-4.5 4C1.02 6 0 7.52 0 9c0 1.53 1 3 3 3 0.44 0 2.66 0 3 0v-1.3H3C1.38 10.7 1.3 9.28 1.3 9c0-0.17 0.05-1.7 1.7-1.7h1.3v-1.3c0-1.39 1.56-2.7 3.2-2.7 2.55 0 3.13 1.55 3.2 1.8v1.2h1.3c0.81 0 2.7 0.22 2.7 2.2 0 2.09-2.25 2.2-2.7 2.2H10v1.3c0.38 0 1.98 0 2 0 2.08 0 4-1.16 4-3.5 0-2.44-1.92-3.5-4-3.5z" />
            | </symbol>
            |</svg>
            |</svg>
          """.stripMargin),

        div(id := "editorWrap")(
          div(id := "fiddleSelectorDiv", style := "display: none")(
            span(cls := "normal", "Select fiddle "),
            select(id := "fiddleSelector")
          ),
          div(id := "editorContainer")(
            div(cls := "label",
              div(title := "Ctrl/Cmd-Enter to run,\nShift-Ctrl/Cmd-Enter to run optimized", id := "run-icon")(
                svg(width := 21, height := 21)(use(svga.xLinkHref := "#sym_run"))
              ),
              div(title := "Reset", id := "reset-icon")(
                svg(width := 21, height := 21)(use(svga.xLinkHref := "#sym_reset"))
              ),
              div(title := "Upload gist", id := "upload-icon")(
                svg(width := 21, height := 21)(use(svga.xLinkHref := "#sym_upload"))
              ),
              div(title := "Help", id := "help-icon")(
                svg(width := 21, height := 21)(use(svga.xLinkHref := "#sym_help"))
              )
            ),
            pre(id := "editor")
          )
        ),
        div(id := "sandbox")(
          div(cls := "label")(span(id := "output-tag", "Output")),
          canvas(id := "canvas", style := "position: absolute"),
          div(id := "output")
        )
      ),
      script(
        id := "compiled"
      ),
      script(raw(arg)),
      script(raw(
        """
            (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
                (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
              m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
              })(window,document,'script','//www.google-analytics.com/analytics.js','ga');

              ga('create', 'UA-27464920-3', 'scala-js-fiddle.com');
              ga('send', 'pageview');
        """
      ))
    ).toString()
  }

  def concatHash(resources: Seq[String], glueStr: String): (String, Array[Byte]) = {
    val hash = MessageDigest.getInstance("MD5")
    // files need a bit of glue between them to work properly in concatenated form
    val glue = glueStr.getBytes
    // read all resources and calculate both hash and concatenated string
    val data = resources.map { res =>
      val stream = getClass.getResourceAsStream(res)
      val data = Streamable.bytes(stream) ++ glue
      hash.update(data)
      data
    }.reduceLeft(_ ++ _)
    (hash.digest().map("%02x".format(_)).mkString, data)
  }

  def joinResources(resources: Seq[String], extension: String, glueStr: String): String = {
    cache.getOrElseUpdate(resources, concatHash(resources, glueStr))._1 + extension
  }

  def fetchResource(hash: String): Option[Array[Byte]] = {
    cache.values.find(_._1 == hash).map(_._2)
  }
}
