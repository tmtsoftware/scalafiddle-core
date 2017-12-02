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
    post: "}\n" +
      "// $ScalaVersion 2.12"
  };
  var templates = global.scalaFiddleTemplates || {};
  templates["basic"] = templates["basic"] || basicTemplate;

  function findFiddles() {
    return Array.from(dom.querySelectorAll("div[data-scalafiddle-template]"))
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
      "    padding: 6px 10px;\n" +
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
      "    font-family: Lato,'Helvetica Neue',Arial,Helvetica,sans-serif;\n" +
      "    padding: 6px 10px;\n" +
      "}\n" +
      "div[data-theme='dark'] > button.scalafiddle-button:hover {\n" +
      "    background: rgba(60,60,60,0.8)!important;\n" +
      "    color: rgba(220, 220, 220, 0.8)!important;\n" +
      "    box-shadow: 0 0 0 1px rgba(200,200,200,.35), 0 0 0 0 rgba(200,200,200,.15);\n" +
      "}\n" +
      ".scalafiddle-button img {margin-bottom: -2px;}\n" +
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
      injected: false,
      minHeight: el.getAttribute("data-minheight") || 300
    };
    var button = dom.createElement("button");
    button.setAttribute("class", "scalafiddle-button");
    var icon = dom.createElement("img");
    icon.src = "https://scalafiddle.io/assets/images/667910cf64d167e46065336c7b5a93d0-favicon-16.png";
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
    var src = encodeURIComponent(buildSource(fiddleData));
    var layout = el.getAttribute("data-layout") || "h65";
    var theme = el.getAttribute("data-theme") || "light";
    iframe.setAttribute("src", "https://embed.scalafiddle.io/embed?theme=" + theme + "&layout=" + layout + "&source=" + src);
    // clear out existing code block and the button
    el.innerHTML = "";
    el.appendChild(iframe);
  }

  // set up styles
  injectCSS();

  // inject fiddles
  var fiddles = findFiddles();
  fiddles.forEach(function (el) {
    constructFiddle(el)
  })
})(window);
