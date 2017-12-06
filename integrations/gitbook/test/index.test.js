const tester = require('gitbook-tester')
const pluginDir = require('path').join(__dirname, '..')
const examples = require('./examples')

const setupBook = content => {
  return tester
    .builder()
    .withFile("templates/custom.scala", "import custom\nobject test {\n////\n}\n")
    .withFile("templates/another.scala", "import another")
    .withContent(content)
    .withLocalPlugin(pluginDir)
    .create()
}

describe('Injecting ScalaFiddle integration', () => {
  jest.setTimeout(20000)

  it('should return HTML with ScalaFiddle attributes in place', done => {
    setupBook(examples.params)
      .then(function (results) {
        console.log(results[0].content);
        expect(results[0].content.includes("data-scalafiddle")).toBe(true);
        expect(results[0].content.includes("data-template=\"custom\"")).toBe(true);
        expect(results[0].content.includes("data-layout=\"v70\"")).toBe(true);
        expect(results[0].content.includes("data-theme=\"dark\"")).toBe(true);

        expect(results[0].content.includes(`pre: 'import custom\\nobject test {',`)).toBe(true);
        expect(results[0].content.includes(`post: ''`)).toBe(true);
        expect(results[0].content.includes(`post: '}\\n'`)).toBe(true);
      })
      .then(done)
      .done()
  })
})