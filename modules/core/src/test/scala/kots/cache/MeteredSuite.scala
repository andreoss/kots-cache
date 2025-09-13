package kots.cache

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.syntax.all._
import munit.CatsEffectSuite

final class MeteredSuite extends CatsEffectSuite {

  private final case class Probe(
    hits: Ref[IO, Int],
    misses: Ref[IO, Int],
    loads: Ref[IO, Int],
  ) extends CacheMetrics[IO] {
    def hit: IO[Unit] = hits.update(_ + 1)
    def miss: IO[Unit] = misses.update(_ + 1)
    def load: IO[Unit] = loads.update(_ + 1)
    def eviction: IO[Unit] = IO.unit
  }

  private def probe: IO[Probe] =
    (Ref.of[IO, Int](0), Ref.of[IO, Int](0), Ref.of[IO, Int](0)).mapN(Probe.apply)

  private def stub: IO[Cache[IO, String, Int]] =
    Ref.of[IO, Map[String, Int]](Map.empty).map { ref =>
      new Cache[IO, String, Int] {
        def get(key: String): IO[Option[Int]] = ref.get.map(_.get(key))
        def put(key: String, value: Int): IO[Unit] = ref.update(_.updated(key, value))
        def modify[A](key: String)(f: Option[Int] => (Option[Int], A)): IO[A] =
          ref.modify { m =>
            val (next, a) = f(m.get(key))
            (next.fold(m - key)(v => m.updated(key, v)), a)
          }
        def remove(key: String): IO[Unit] = ref.update(_ - key)
        def clear: IO[Unit] = ref.set(Map.empty)
      }
    }

  test("gets count hits and misses") {
    (stub, probe).flatMapN { (c0, p) =>
      val c = Metered.cache(c0, p)
      c.put("a", 1) *> c.get("a") *> c.get("a") *> c.get("b") *>
        (p.hits.get, p.misses.get).tupled
    }.assertEquals((2, 1))
  }

  test("a collapsed stampede counts one load") {
    (stub, probe).flatMapN { (c0, p) =>
      LoadingCache.singleFlight(c0).flatMap { lc =>
        val c = Metered.loading(lc, p)
        List.fill(20)(c.getOrLoad("k")(IO.sleep(scala.concurrent.duration.Duration(10, "ms")).as(1))).parSequence_ *>
          p.loads.get
      }
    }.assertEquals(1)
  }

  test("a cached value loads nothing") {
    (stub, probe).flatMapN { (c0, p) =>
      LoadingCache.singleFlight(c0).flatMap { lc =>
        val c = Metered.loading(lc, p)
        c.put("k", 5) *> c.getOrLoad("k")(IO.pure(1)) *> p.loads.get
      }
    }.assertEquals(0)
  }

  test("the metered cache passes writes through") {
    (stub, probe).flatMapN { (c0, p) =>
      val c = Metered.cache(c0, p)
      c.put("a", 1) *> c.modify("a")(o => (o.map(_ + 1), ())) *> c.get("a").flatMap { v =>
        c.remove("a") *> c.clear *> c.get("a").map(after => (v, after))
      }
    }.assertEquals((Some(2), None))
  }

  test("the metered loading cache passes writes through") {
    (stub, probe).flatMapN { (c0, p) =>
      LoadingCache.singleFlight(c0).flatMap { lc =>
        val c = Metered.loading(lc, p)
        c.put("a", 1) *> c.modify("a")(o => (o.map(_ + 1), ())) *> c.get("a").flatMap { v =>
          c.remove("a") *> c.put("b", 2) *> c.clear *> c.get("b").map(after => (v, after))
        }
      }
    }.assertEquals((Some(2), None))
  }

  test("every no-op metric runs") {
    val m = CacheMetrics.noop[IO]
    (m.hit *> m.miss *> m.load *> m.eviction).assertEquals(())
  }

  test("the no-op metrics change nothing") {
    stub.flatMap { c0 =>
      val c = Metered.cache(c0, CacheMetrics.noop[IO])
      c.put("a", 1) *> c.get("a")
    }.assertEquals(Some(1))
  }
}
