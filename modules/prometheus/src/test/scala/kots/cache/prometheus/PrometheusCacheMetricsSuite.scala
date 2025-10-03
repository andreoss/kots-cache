package kots.cache.prometheus

import cats.effect.IO
import cats.effect.testkit.TestControl
import cats.syntax.all._
import io.prometheus.metrics.model.registry.PrometheusRegistry
import io.prometheus.metrics.model.snapshots.{CounterSnapshot, HistogramSnapshot}
import kots.cache.{Expiry, LoadingCache, Metered}
import kots.cache.mem.MemCache
import munit.CatsEffectSuite

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

final class PrometheusCacheMetricsSuite extends CatsEffectSuite {

  private def counter(
    registry: PrometheusRegistry,
    name: String,
    labels: Map[String, String],
  ): Option[Double] =
    registry.scrape().asScala.collectFirst {
      case s: CounterSnapshot if s.getMetadata.getName == name =>
        s.getDataPoints.asScala.collectFirst {
          case p if labels.forall { case (k, v) => p.getLabels.get(k) == v } => p.getValue
        }
    }.flatten

  private def histogram(
    registry: PrometheusRegistry,
    name: String,
    labels: Map[String, String],
  ): Option[(Long, Double)] =
    registry.scrape().asScala.collectFirst {
      case s: HistogramSnapshot if s.getMetadata.getName == name =>
        s.getDataPoints.asScala.collectFirst {
          case p if labels.forall { case (k, v) => p.getLabels.get(k) == v } =>
            (p.getCount, p.getSum)
        }
    }.flatten

  test("hits and misses land in the gets counter with latency observed") {
    val registry = new PrometheusRegistry()
    val metrics = PrometheusCacheMetrics.register(registry).forCache[IO]("users")
    MemCache.of[IO, String, Int].flatMap { c0 =>
      val c = Metered.cache(c0, metrics)
      c.put("a", 1) *> c.get("a") *> c.get("a") *> c.get("b")
    } *> IO {
      assertEquals(counter(registry, "cache_gets", Map("cache" -> "users", "result" -> "hit")), Some(2.0))
      assertEquals(counter(registry, "cache_gets", Map("cache" -> "users", "result" -> "miss")), Some(1.0))
      assert(histogram(registry, "cache_get_latency_seconds", Map("cache" -> "users")).exists(_._1 == 3L))
    }
  }

  test("loads are counted with latency by outcome") {
    val registry = new PrometheusRegistry()
    val metrics = PrometheusCacheMetrics.register(registry).forCache[IO]("users")
    MemCache.of[IO, String, Int].flatMap { c0 =>
      LoadingCache.singleFlight(c0).flatMap { lc =>
        val c = Metered.loading(lc, metrics)
        c.getOrLoad("k")(IO.pure(1)) *>
          c.getOrLoad("gone")(IO.raiseError[Int](new Exception("boom"))).attempt.void
      }
    } *> IO {
      assertEquals(counter(registry, "cache_loads", Map("cache" -> "users")), Some(2.0))
      assert(
        histogram(registry, "cache_load_latency_seconds", Map("cache" -> "users", "outcome" -> "success"))
          .exists(_._1 == 1L),
      )
      assert(
        histogram(registry, "cache_load_latency_seconds", Map("cache" -> "users", "outcome" -> "failure"))
          .exists(_._1 == 1L),
      )
    }
  }

  test("an expiry death observes the entry lifetime in seconds") {
    val registry = new PrometheusRegistry()
    val metrics = PrometheusCacheMetrics.register(registry).forCache[IO]("users")
    TestControl.executeEmbed {
      MemCache.expiring[IO, String, Int](Expiry.ttl(1.minute), metrics).flatMap { c =>
        c.put("a", 1) *> IO.sleep(90.seconds) *> c.get("a").void
      }
    } *> IO {
      assertEquals(
        histogram(registry, "cache_entry_lifetime_seconds", Map("cache" -> "users")),
        Some((1L, 90.0)),
      )
    }
  }

  test("evictions are counted") {
    val registry = new PrometheusRegistry()
    val metrics = PrometheusCacheMetrics.register(registry).forCache[IO]("users")
    MemCache.bounded[IO, String, Int](2, metrics).flatMap { c =>
      c.put("a", 1) *> c.put("b", 2) *> c.put("c", 3)
    } *> IO {
      assertEquals(counter(registry, "cache_evictions", Map("cache" -> "users")), Some(1.0))
    }
  }

  test("two caches share one registry under distinct labels") {
    val registry = new PrometheusRegistry()
    val binder = PrometheusCacheMetrics.register(registry)
    val a = binder.forCache[IO]("a")
    val b = binder.forCache[IO]("b")
    (MemCache.of[IO, String, Int], MemCache.of[IO, String, Int]).flatMapN { (ca0, cb0) =>
      Metered.cache(ca0, a).get("x") *> Metered.cache(cb0, b).get("x")
    } *> IO {
      assertEquals(counter(registry, "cache_gets", Map("cache" -> "a", "result" -> "miss")), Some(1.0))
      assertEquals(counter(registry, "cache_gets", Map("cache" -> "b", "result" -> "miss")), Some(1.0))
    }
  }
}
