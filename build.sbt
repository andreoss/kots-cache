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
  .aggregate(core, mem, jcache, redis, interop, bench, caffeine, hazelcast, couchbase, infinispan)

lazy val core = project
  .in(file("modules/core"))
  .settings(commonSettings)
  .settings(name := "kots-cache-core")

lazy val mem = project
  .in(file("modules/mem"))
  .settings(commonSettings)
  .settings(name := "kots-cache-mem")
  .dependsOn(core % "compile->compile;test->test")

lazy val jcache = project
  .in(file("modules/jcache"))
  .settings(commonSettings)
  .settings(name := "kots-cache-jcache")
  .settings(
    libraryDependencies ++= Seq(jcacheApi, caffeineJcache, ehcache, infinispanJcache, cache2kJcache),
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val redis = project
  .in(file("modules/redis"))
  .settings(commonSettings)
  .settings(name := "kots-cache-redis")
  .settings(libraryDependencies += redis4cats)
  .dependsOn(core % "compile->compile;test->test")

lazy val interop = project
  .in(file("modules/interop"))
  .settings(commonSettings)
  .settings(name := "kots-cache-interop")
  .settings(libraryDependencies ++= Seq(zio, zioInteropCats))
  .dependsOn(core % "compile->compile;test->test", mem % "test->compile")

lazy val caffeine = project
  .in(file("modules/caffeine"))
  .settings(commonSettings)
  .settings(name := "kots-cache-caffeine")
  .settings(libraryDependencies += Dependencies.caffeine)
  .dependsOn(core % "compile->compile;test->test")

lazy val hazelcast = project
  .in(file("modules/hazelcast"))
  .settings(commonSettings)
  .settings(name := "kots-cache-hazelcast")
  .settings(libraryDependencies += Dependencies.hazelcast)
  .settings(Test / fork := true)
  .dependsOn(core % "compile->compile;test->test")

lazy val couchbase = project
  .in(file("modules/couchbase"))
  .settings(commonSettings)
  .settings(name := "kots-cache-couchbase")
  .settings(libraryDependencies += Dependencies.couchbase)
  .settings(Test / fork := true)
  .dependsOn(core % "compile->compile;test->test")

lazy val infinispan = project
  .in(file("modules/infinispan"))
  .settings(commonSettings)
  .settings(name := "kots-cache-infinispan")
  .settings(libraryDependencies += Dependencies.infinispanHotrod)
  .settings(Test / fork := true)
  .dependsOn(core % "compile->compile;test->test")

lazy val bench = project
  .in(file("modules/bench"))
  .settings(commonSettings)
  .settings(name := "kots-cache-bench")
  .dependsOn(core, mem)