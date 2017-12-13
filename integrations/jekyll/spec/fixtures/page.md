---
title: Test Page
layout: default
---

** Jekyll ScalaFiddle Plugin **

{% scalafiddle template="cats" layout="v70" prefix="import cats.data._" %}
```scala
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._

sealed trait Foo

case class Bar(xs: List[String]) extends Foo

case class Qux(i: Int, d: Option[Double]) extends Foo

val foo: Foo = Qux(13, Some(14.0))

foo.asJson.noSpaces

decode[Foo](foo.asJson.spaces4)
```

{% endscalafiddle %}

## Some other stuff

{% scalafiddle template="dogs" %}
```scala
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._

sealed trait Foo

case class Bar(xs: List[String]) extends Foo

case class Qux(i: Int, d: Option[Double]) extends Foo

val foo: Foo = Qux(13, Some(14.0))

foo.asJson.noSpaces

decode[Foo](foo.asJson.spaces4)
```
{% endscalafiddle %}
