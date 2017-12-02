(function (global) {
  "use strict";
  var dom = global.document;

  // set up templates
  var basicTemplate = {
    pre: "import fiddle.Fiddle, Fiddle.println\n" +
    "import scalajs.js\n" +
    "\n" +
    "@js.annotation.JSExportTopLevel(\"ScalaFiddle\")\n" +
    "object ScalaFiddle {",
    post: "}"
  };
  var templates = global.scalaFiddleTemplates || {};
  templates["basic"] = templates["basic"] || basicTemplate;

  function findFiddles() {
    return Array.from(dom.querySelectorAll("div[data-scalafiddle-template]"))
  }

  function injectCSS() {
    // create CSS styles
    var css =
      ".scalafiddle-button { position: absolute; right: 10px; top: 10px; }" +
      "div[data-scalafiddle-template] { position: relative; }";

    var s = document.createElement('style');
    s.setAttribute('type', 'text/css');
    s.appendChild(dom.createTextNode(css));
    dom.querySelector("head").appendChild(s);
  }

  function buildSource(fiddleData) {
    return fiddleData.template.pre +
      "\n// $FiddleStart\n" +
      fiddleData.content +
      "\n// $FiddleEnd\n" +
      fiddleData.template.post
  }

  function constructFiddle(el) {
    var templateId = el.getAttribute("data-scalafiddle-template");
    var template = templates[templateId];
    if (template === undefined) {
      console.error("ScalaFiddle template " + templateId + " is not defined");
      return
    }
    el["scalaFiddleData"] = {
      element: el,
      content: el.querySelector("pre").textContent,
      template: template,
      injected: false
    };
    var button = dom.createElement("button");
    button.setAttribute("class", "scalafiddle-button");
    button.appendChild(dom.createTextNode("Run"));
    button.onclick = function () { runFiddle(el) };
    el.appendChild(button);
  }

  function runFiddle(el) {
    var fiddleData = el["scalaFiddleData"];
    console.log("Running fiddle:\n\n" + buildSource(fiddleData));
  }

  // set up styles
  injectCSS();

  // inject fiddles
  var fiddles = findFiddles();
  fiddles.forEach(function (el) {
    constructFiddle(el)
  })
})(window);
