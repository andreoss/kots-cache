import Dependencies.*

lazy val commonSettings = Seq(
  organization := "kots.cache",
  version := "0.1.0-SNAPSHOT",
  scalaVersion := scala213,
  crossScalaVersions := Seq(scala213, scala3),
  libraryDependencies ++= common,
  Compile / compile / scalacOptions ++= ScalacOptions.forVersion(scalaVersion.value),
  Test / publishArtifact := false,
  coverageMinimumStmtTotal := 85,
  coverageFailOnMinimum := true,
)

lazy val root = (project in file("."))
  .settings(commonSettings)
  .settings(name := "kots-cache", publish / skip := true)
  .aggregate(core, mem, jcache, redis, interop, bench)

lazy val core = project
  .in(file("modules/core"))
  .settings(commonSettings)
  .settings(name := "kots-cache-core")

lazy val mem = project
  .in(file("modules/mem"))
  .settings(commonSettings)
  .settings(name := "kots-cache-mem")
  .dependsOn(core)

lazy val jcache = project
  .in(file("modules/jcache"))
  .settings(commonSettings)
  .settings(name := "kots-cache-jcache")
  .dependsOn(core)

lazy val redis = project
  .in(file("modules/redis"))
  .settings(commonSettings)
  .settings(name := "kots-cache-redis")
  .dependsOn(core)

lazy val interop = project
  .in(file("modules/interop"))
  .settings(commonSettings)
  .settings(name := "kots-cache-interop")
  .dependsOn(core)

lazy val bench = project
  .in(file("modules/bench"))
  .settings(commonSettings)
  .settings(name := "kots-cache-bench")
  .dependsOn(core, mem)