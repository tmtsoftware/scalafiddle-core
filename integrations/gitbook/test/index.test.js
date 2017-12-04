const tester = require('gitbook-tester')
const pluginDir = require('path').join(__dirname, '..')
const examples = require('./examples')

const setupBook = content => {
  return tester
    .builder()
    .withContent(content)
    .withLocalPlugin(pluginDir)
    .create()
}

describe('Injecting ScalaFiddle integration', () => {
  jest.setTimeout(20000)

/*
  it('should return HTML with ScalaFiddle div injected', done => {
    setupBook(examples.plain)
      .then(function (results) {
        console.log(results[0].content)
        expect(results[0].content.includes("data-scalafiddle")).toBe(true)
      })
      .then(done)
      .done()
  })
*/

  it('should return HTML with ScalaFiddle attributes in place', done => {
    setupBook(examples.params)
      .then(function (results) {
        console.log(results[0].content)
        expect(results[0].content.includes("data-scalafiddle")).toBe(true)
        expect(results[0].content.includes("data-template=\"custom\"")).toBe(true)
        expect(results[0].content.includes("data-layout=\"v70\"")).toBe(true)
        expect(results[0].content.includes("data-theme=\"dark\"")).toBe(true)
      })
      .then(done)
      .done()
  })
})