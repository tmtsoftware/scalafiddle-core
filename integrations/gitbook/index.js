const fs = require("fs");

const defaultOptions = Object.freeze({
  templateDir: "templates",
  scalaFiddleUrl: "https://embed.scalafiddle.io/",
  prefix: undefined,
  dependency: undefined,
  scalaversion: undefined,
  selector: undefined,
  minheight: undefined,
  theme: undefined
});

const validParams = ["template", "prefix", "dependency", "scalaversion", "minheight", "layout", "theme", "selector"];

const processArgs = (args, config) => {
  const params = {};
  validParams.forEach(p => {
    if (args[p])
      params[p] = args[p];
    else if (config[p])
      params[p] = config[p];
  });
  return params
};

const initOptions = (config) => {
  const kv = {};
  // Overwrite default values with user book options.
  Object.keys(defaultOptions).forEach(key => {
    if (config[key] !== undefined) {
      kv[key] = config[key];
    } else if (defaultOptions[key] !== undefined) {
      kv[key] = defaultOptions[key];
    }
  });
  return Object.freeze(kv);
};

const escapeStr = str => str.replace(/\\/g, "\\\\").replace(/\r/g, "").replace(/\t/g, "\\t").replace(/'/g, "\\\'")

const readTemplate = file => {
  const lines = fs.readFileSync(file, "utf-8").split("\n");
  const pre = [];
  const post = [];
  let inPre = true;
  lines.forEach(line => {
    if (line.trim().startsWith("////")) {
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
        const config = (this.options.pluginsConfig && this.options.pluginsConfig.scalafiddle) || {};
        const options = initOptions(config);
        const content = block.body;
        const params = processArgs(block.kwargs, options);
        const attributes = Object.keys(params).map(key => `data-${key}='${params[key]}'`).join(" ");
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
      const config = (this.options.pluginsConfig && this.options.pluginsConfig.scalafiddle) || {};
      const options = initOptions(config);
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
          const templateDefs = Object.keys(templates).map(function (key) {
            const fileName = book.resolve(`${options.templateDir}/${key}.scala`);
            const tf = readTemplate(fileName);
            return `    '${key}': {\n      pre: '${tf.pre.map(escapeStr).join("\\n")}',\n      post: '${tf.post.map(escapeStr).join("\\n")}'\n    }`
          }).join(",\n");
          script += `<script>\n  window.scalaFiddleTemplates = {\n${templateDefs}\n}</script>\n`
        }
        script += `<script defer src="${options.scalaFiddleUrl}integration.js"></script>\n`
        // insert script just before body-end tag, if it exists
        const bodyIdx = page.content.lastIndexOf("</body>")
        if (bodyIdx > 0) {
          page.content = page.content.slice(0, bodyIdx).concat(script, page.content.slice(bodyIdx));
        } else {
          page.content = page.content.concat(script);
        }
      }
      return page
    }
  }
}
