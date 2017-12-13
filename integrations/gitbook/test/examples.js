const plain = `
{% scalafiddle %}
\`\`\`scala
var x = 0
\`\`\`
{% endscalafiddle %}
`

const params = `
{% scalafiddle template="custom", layout="v70", scalaversion="2.11", dependency="io.suzaku %%% boopickle % 1.2.6" %}
\`\`\`scala
var x = 0
\`\`\`
{% endscalafiddle %}

{% scalafiddle template="another", minheight="700" %}
\`\`\`scala
var x = 0
\`\`\`
{% endscalafiddle %}
`

module.exports = {
  plain,
  params
}
