Scala-Js-Fiddle
==============

Source code for [www.scala-js-fiddle.com](http://www.scala-js-fiddle.com). To develop, run:

```
sbt "~; server/reStart"
```

You can also run

```
sbt stage; ./server/target/start
```

to stage and run without SBT,

```
sbt assembly; java -jar server/target/scala-2.10/server-assembly-0.1-SNAPSHOT.jar
```

to package as a fat jar and run.

Access the fiddle via browser at http://localhost:8080/embed
