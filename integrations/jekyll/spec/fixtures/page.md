---
title: Test Page
layout: default
---

** Jekyll ScalaFiddle Plugin **

{% scalafiddle template="cats" layout="v70"%}
```scala
package cats.data

object OptionT {

  private[data] final class PurePartiallyApplied[F[_]](val dummy: Boolean = true ) extends AnyVal {
    def apply[A](value: A)(implicit F: Applicative[F]): OptionT[F, A] =
      OptionT(F.pure(Some(value)))
  }

  def pure[F[_]]: PurePartiallyApplied[F] = new PurePartiallyApplied[F]
}
```
{% endscalafiddle %}

## Some other stuff

{% scalafiddle template="dogs" %}
```scala
package cats.data

object OptionT {

  private[data] final class PurePartiallyApplied[F[_]](val dummy: Boolean = true ) extends AnyVal {
    def apply[A](value: A)(implicit F: Applicative[F]): OptionT[F, A] =
      OptionT(F.pure(Some(value)))
  }

  def pure[F[_]]: PurePartiallyApplied[F] = new PurePartiallyApplied[F]
}
```
{% endscalafiddle %}
