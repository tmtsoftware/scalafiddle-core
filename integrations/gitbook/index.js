const fs = require("fs");
const path = require("path");
const cfg = {
  "templateDir": {
    "type": "string",
    "default": "templates",
    "description": "Location of ScalaFiddle template files"
  },
  "scalaFiddleUrl": {
    "type": "string",
    "default": "https://embed.scalafiddle.io/",
    "description": "ScalaFiddle service URL"
  }
};

const defaultOptions = Object.freeze({
  templateDir: cfg.templateDir.default,
  scalaFiddleUrl: cfg.scalaFiddleUrl.default
});

const validParams = ["template", "minheight", "layout", "theme"];

const processArgs = args => {
  const params = {};
  validParams.forEach(p => {if (args[p]) params[p] = args[p]});
  return params
};

const convertValue = (valstr, valtype) => {
  if (valtype === "boolean" || valtype === "number") {
    return JSON.parse(valstr);
  }
  return valstr;
};

const initOptions = (options) => {
  const kv = Object.assign({}, defaultOptions);
  // Overwrite default value with user book options.
  Object.keys(defaultOptions).forEach(key => {
    if (options.config.get(key) !== undefined) {
      kv[key] = convertValue(options.config.get(key), typeof defaultOptions[key]);
    }
  });
  return Object.freeze(kv);
};

const escapeStr = str => str.replace(/\r/g,"").replace(/\t/g,"\\t").replace(/'/g,"\\\'")

const readTemplate = file => {
  const lines = fs.readFileSync(file, "utf-8").split("\n");
  const pre = [];
  const post = [];
  let inPre = true;
  lines.forEach(line => {
    if (line.startsWith("////")) {
      inPre = false
    } else if (inPre) {
      pre.push(line)
    } else {
      post.push(line)
    }
  });
  return {
    pre,
    post
  }
};

module.exports = {
  blocks: {
    scalafiddle: {
      process: function (block) {
        const content = block.body;
        const params = processArgs(block.kwargs);
        const attributes = Object.keys(params).map(key => `data-${key}="${params[key]}"`).join(" ");
        const output = this
          .renderBlock('markdown', content)
          .then(md => `<div data-scalafiddle ${attributes}>${md}</div>`);
        return output;
      }
    }
  },
  hooks: {
    page: function (page) {
      const content = page.content;
      const book = this;
      const options = initOptions(this);
      const scalaFiddleRE = /<div data-scalafiddle/g;

      if (content.match(scalaFiddleRE)) {
        const templateRE = /<div data-scalafiddle data-template="([^"]+)"/g;
        const templates = {};
        let script = "";
        let m = templateRE.exec(content);
        while (m) {
          templates[m[1]] = true;
          m = templateRE.exec(content);
        }
        if (Object.keys(templates).length > 0) {
          const templateDefs = Object.keys(templates).map(function(key) {
            const fileName = book.resolve(`${options.templateDir}/${key}.scala`);
            const tf = readTemplate(fileName);
            return `    '${key}': {\n      pre: '${tf.pre.map(escapeStr).join("\\n")}',\n      post: '${tf.post.map(escapeStr).join("\\n")}'\n    }`
          }).join(",\n");
          script += `<script>\n  window.scalaFiddleTemplates = {\n${templateDefs}\n}</script>\n`
        }
        script += `<script src="${options.scalaFiddleUrl}integration.js"></script>\n`
        console.log(script)
        // insert script just before body-end tag, if it exists
        const bodyIdx = page.content.lastIndexOf("</body>")
        if(bodyIdx > 0) {
          page.content = page.content.slice(0, bodyIdx).concat(script, page.content.slice(bodyIdx));
        } else {
          page.content = page.content.concat(script);
        }
      }
      return page
    }
  }
}
