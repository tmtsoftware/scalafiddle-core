const validParams = ["template", "minheight", "layout", "theme"];
const processArgs = args => {
  const params = {};
  validParams.forEach(p => {if (args[p]) params[p] = args[p]});
  return params
}

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
          const templateDefs = Object.keys(templates).map(key => `    "${key}": {\n      pre: "",\n      post: ""\n    }`).join(",\n");
          script += `<script>\n  window.scalaFiddleTemplates = {\n${templateDefs}\n}</script>\n`
        }
        script += `<script src="https://embed.scalafiddle.io/integration.js"></script>`
        console.log(script)
      }
      return page
    }
  }
}
