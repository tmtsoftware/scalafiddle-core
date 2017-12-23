# Gitbook ScalaFiddle

Integrate ScalaFiddle easily into your Gitbook documentation using this plugin.

## Install

Add the below to your `book.json` file, then run `gitbook install` :

```json
{
  "plugins": ["scalafiddle"]
}
```

## Usage

The ScalaFiddle plugin provides a tag `scalafiddle` which you can use to convert a code block in your documentation into an
editable and runnable fiddle. Simply surround a code block with the tag as shown below:

```
{% scalafiddle %}
\`\`\`scala
def sum(a: Int, b: Int) = a + b

println(sum(2, 2))
\`\`\`
{% endscalafiddle %}
```