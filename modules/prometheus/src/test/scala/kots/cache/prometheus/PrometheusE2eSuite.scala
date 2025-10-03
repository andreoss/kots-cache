package kots.cache.prometheus

import cats.effect.IO
import cats.syntax.all._
import io.prometheus.metrics.exporter.httpserver.HTTPServer
import io.prometheus.metrics.model.registry.PrometheusRegistry
import kots.cache.Metered
import kots.cache.mem.MemCache
import munit.CatsEffectSuite

import scala.concurrent.duration._

final class PrometheusE2eSuite extends CatsEffectSuite {

  private val exporterPort = 19095

  private def fetch(url: String): IO[String] =
    IO.blocking(scala.io.Source.fromURL(url, "UTF-8").mkString)

  private def pollUntil(url: String, marker: String): IO[String] =
    fetch(url).attempt
      .map(_.toOption.filter(_.contains(marker)))
      .flatMap {
        case Some(body) => IO.pure(body)
        case None       => IO.sleep(500.millis) *> pollUntil(url, marker)
      }
      .timeout(90.seconds)

  test("a live workload's series reach the composed Prometheus server") {
    val registry = new PrometheusRegistry()
    val metrics = PrometheusCacheMetrics.register(registry).forCache[IO]("e2e")
    val workload =
      MemCache.of[IO, String, Int].flatMap { c0 =>
        val c = Metered.cache(c0, metrics)
        c.put("a", 1) *> c.get("a") *> c.get("missing").void
      }
    val server = IO.blocking(
      HTTPServer.builder().port(exporterPort).registry(registry).buildAndStart(),
    )
    server.bracket { _ =>
      for {
        _ <- workload
        exposed <- fetch(s"http://127.0.0.1:$exporterPort/metrics")
        _ <- IO {
          assert(exposed.contains("cache_gets_total{cache=\"e2e\",result=\"hit\"} 1.0"))
          assert(exposed.contains("cache_gets_total{cache=\"e2e\",result=\"miss\"} 1.0"))
          assert(exposed.contains("cache_get_latency_seconds_count{cache=\"e2e\"} 2"))
        }
        scraped <- pollUntil(
          "http://127.0.0.1:9090/api/v1/query?query=cache_gets_total",
          "\"cache\":\"e2e\"",
        )
        _ <- IO {
          assert(scraped.contains("\"status\":\"success\""))
          assert(scraped.contains("\"result\":\"hit\""))
        }
      } yield ()
    }(s => IO.blocking(s.stop()))
  }
}
