name := "notes-service"

version := "1.0-SNAPSHOT"

scalaVersion := "2.13.17"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  guice,
  "com.typesafe.play" %% "play-json" % "2.9.4",
  "org.apache.pekko" %% "pekko-actor-typed" % "1.0.3",
  "com.datastax.oss" % "java-driver-core" % "4.17.0",
  "com.datastax.oss" % "java-driver-query-builder" % "4.17.0",
  "net.debasishg" %% "redisclient" % "3.30",
  "redis.clients" % "jedis" % "4.3.1"
)
