const plain = `
{% scalafiddle %}
\`\`\`scala
var x = 0
\`\`\`
{% endscalafiddle %}
`

const params = `
{% scalafiddle template="custom", layout="v70", theme="dark" %}
\`\`\`scala
var x = 0
\`\`\`
{% endscalafiddle %}

{% scalafiddle template="another", minheight="700", theme="dark" %}
\`\`\`scala
var x = 0
\`\`\`
{% endscalafiddle %}
`

module.exports = {
  plain,
  params
}
