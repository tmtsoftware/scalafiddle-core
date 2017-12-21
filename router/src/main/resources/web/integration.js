(function (global, scalaFiddleUrl, iconUrl, validVersions, defaultScalaVersion) {
  "use strict";
  var dom = global.document;

  // set up templates
  var defaultTemplate = {
    pre: "import fiddle.Fiddle, Fiddle.println\n" +
    "\n" +
    "@scalajs.js.annotation.JSExportTopLevel(\"ScalaFiddle\")\n" +
    "object ScalaFiddle {\n",
    post: "\n}\n"
  };
  var templates = global.scalaFiddleTemplates || {};

  function findFiddles() {
    return Array.from(dom.querySelectorAll("div[data-scalafiddle]"))
  }

  function injectCSS() {
    // create CSS styles
    var css =
      ".scalafiddle-button {\n" +
      "    position: absolute; right: 10px; top: 10px;\n" +
      "    background: rgba(255,255,255,0.6)!important;\n" +
      "    color: rgba(0, 0, 0, 0.6)!important;\n" +
      "    border-radius: 5px;\n" +
      "    border: 1px solid #ddd;\n" +
      "    font-family: Lato,'Helvetica Neue',Arial,Helvetica,sans-serif;\n" +
      "    font-size: 14px;\n" +
      "    padding: 3px 8px;\n" +
      "}\n" +
      ".scalafiddle-button:hover {\n" +
      "    background: rgba(255,255,255,0.8)!important;\n" +
      "    color: rgba(0, 0, 0, 0.8)!important;\n" +
      "    box-shadow: 0 0 0 1px rgba(35,35,35,.35) inset, 0 0 0 0 rgba(35,35,35,.15) inset;\n" +
      "}\n" +
      "div[data-theme='dark'] > button.scalafiddle-button {\n" +
      "    background: rgba(60,60,60,0.6)!important;\n" +
      "    color: rgba(220, 220, 220, 0.6)!important;\n" +
      "    border-radius: 3px;\n" +
      "    border: 1px solid #333;\n" +
      "}\n" +
      "div[data-theme='dark'] > button.scalafiddle-button:hover {\n" +
      "    background: rgba(60,60,60,0.8)!important;\n" +
      "    color: rgba(220, 220, 220, 0.8)!important;\n" +
      "    box-shadow: 0 0 0 1px rgba(200,200,200,.35), 0 0 0 0 rgba(200,200,200,.15);\n" +
      "}\n" +
      ".scalafiddle-button img {margin-bottom: 4px; vertical-align: middle; width: 16px; height: 16px; }\n" +
      "div[data-scalafiddle] { position: relative; }";

    var s = document.createElement('style');
    s.setAttribute('type', 'text/css');
    s.appendChild(dom.createTextNode(css));
    dom.querySelector("head").appendChild(s);
  }

  function buildSource(fiddleData) {
    return fiddleData.template.pre +
      fiddleData.prefix +
      "\n// $FiddleStart\n" +
      fiddleData.content +
      "\n// $FiddleEnd\n" +
      fiddleData.template.post + "\n" +
      fiddleData.dependencies.map(function (dep) { return "// $FiddleDependency " + dep + "\n"}).join("") +
      "// $ScalaVersion " + fiddleData.scalaVersion + "\n"
  }

  var dependencyRE = / *([^ %]+) +%%%? +([^ %]+) +% +([^ %]+) */;
  var rawTemplateRE = /.+_raw/;

  function constructFiddle(el) {
    var templateId = el.getAttribute("data-template");

    var template;
    if (templateId === null) {
      template = defaultTemplate
    } else {
      template = templates[templateId];
      if (template === undefined) {
        throw "ScalaFiddle template '" + templateId + "' is not defined";
      }
      if (!rawTemplateRE.test(templateId)) {
        // add default template
        template = {
          pre: defaultTemplate.pre + template.pre,
          post: template.post + defaultTemplate.post
        }
      }
    }
    var dependencies = el.hasAttribute("data-dependency") ? el.getAttribute("data-dependency").split(",") : [];
    var prefix = el.hasAttribute("data-prefix") ? "\n" + el.getAttribute("data-prefix") + "\n" : "";
    var scalaVersion = el.hasAttribute("data-scalaversion") ? el.getAttribute("data-scalaversion") : defaultScalaVersion;
    var selector = el.getAttribute("data-selector") || "pre";
    var minHeight = parseInt(el.getAttribute("data-minheight") || "350", 10);
    var contentElement = el.querySelector(selector);
    // validate parameters
    dependencies.forEach(function (dep) {
      if (!dependencyRE.test(dep)) throw "ScalaFiddle dependency '" + dep + "' is not correctly formed";
    });
    if (validVersions[scalaVersion] === undefined)
      throw "Invalid Scala version '" + scalaVersion + "'";
    if (isNaN(minHeight))
      throw "Invalid minheight value '" + el.getAttribute("data-minheight") + "'";
    if (contentElement === null)
      throw "No content element '" + selector + "' found";

    el["scalaFiddleData"] = {
      element: el,
      content: contentElement.textContent,
      template: template,
      scalaVersion: scalaVersion,
      dependencies: dependencies,
      prefix: prefix,
      injected: false,
      minHeight: minHeight
    };
    var button = dom.createElement("button");
    button.setAttribute("class", "scalafiddle-button");
    var icon = dom.createElement("img");
    icon.src = iconUrl;
    button.appendChild(icon);
    button.appendChild(dom.createTextNode("Run"));
    button.onclick = function () { injectFiddle(el) };
    el.appendChild(button);
  }

  function injectFiddle(el) {
    var fiddleData = el["scalaFiddleData"];
    // compute height for the iframe
    var height = Math.max(el.clientHeight, fiddleData.minHeight);
    var iframe = dom.createElement("iframe");
    iframe.setAttribute("height", height + "px");
    iframe.setAttribute("frameborder", "0");
    iframe.setAttribute("style", "width: 100%");
    var layout = el.getAttribute("data-layout") || "v65";
    var theme = el.getAttribute("data-theme") || "light";
    iframe.setAttribute("src", scalaFiddleUrl + "embed?theme=" + theme + "&layout=" + layout);
    iframe.onload = function(e) {
      var msg = {"cmd": "setSource", "data": buildSource(fiddleData)};
      iframe.contentWindow.postMessage(msg, "*");
    };
    fiddleData.injected = true;
    // clear out existing code block and the button
    el.innerHTML = "";
    el.appendChild(iframe);
  }

  // set up styles
  injectCSS();

  // inject fiddles
  findFiddles().forEach(function (el) {
    try {
      constructFiddle(el)
    } catch (e) {
      console.error(e);
    }
  })
})(
window,"http://localhost:8880/","http://localhost:8880/runicon.png",{"2.11": true, "2.12": true},"2.12" // PARAMETERS
);
