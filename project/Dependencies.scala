import sbt.*

object Dependencies {

  val catsCore = "org.typelevel" %% "cats-core" % "2.13.0"
  val catsEffect = "org.typelevel" %% "cats-effect" % "3.7.1"
  val munitCatsEffect = "org.typelevel" %% "munit-cats-effect" % "2.1.0" % Test
  val catsEffectTestkit = "org.typelevel" %% "cats-effect-testkit" % "3.7.1" % Test
  val jcacheApi = "javax.cache" % "cache-api" % "1.1.1"
  val caffeineJcache = "com.github.ben-manes.caffeine" % "jcache" % "3.1.8" % Test
  val caffeine = "com.github.ben-manes.caffeine" % "caffeine" % "3.1.8"
  val ehcache = "org.ehcache" % "ehcache" % "3.10.8" % Test
  val infinispanJcache = "org.infinispan" % "infinispan-jcache" % "15.2.6.Final" % Test
  val hazelcast = "com.hazelcast" % "hazelcast" % "5.5.0"
  val couchbase = "com.couchbase.client" % "java-client" % "3.12.3"
  val redis4cats = "dev.profunktor" %% "redis4cats-effects" % "2.0.6"
  val zio = "dev.zio" %% "zio" % "2.1.26" % Test
  val zioInteropCats = "dev.zio" %% "zio-interop-cats" % "23.1.0.13" % Test

  val scala213 = "2.13.18"
  val scala3 = "3.3.8"

  val common = Seq(catsCore, catsEffect, munitCatsEffect, catsEffectTestkit)
}