package kots.cache

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.syntax.all._
import munit.CatsEffectSuite

import scala.concurrent.duration._

final class SingleFlightSuite extends CatsEffectSuite {

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

  private def loading: IO[(LoadingCache[IO, String, Int], Ref[IO, Int])] =
    for {
      c <- stub
      lc <- LoadingCache.singleFlight(c)
      calls <- Ref.of[IO, Int](0)
    } yield (lc, calls)

  test("fifty concurrent loads of one key run the loader once") {
    loading.flatMap { case (c, calls) =>
      val load = calls.update(_ + 1) *> IO.sleep(10.millis).as(42)
      List.fill(50)(c.getOrLoad("k")(load)).parSequence.product(calls.get)
    }.map { case (vs, n) =>
      assertEquals(vs.toSet, Set(42))
      assertEquals(n, 1)
    }
  }

  test("a present value is returned without running the loader") {
    loading.flatMap { case (c, calls) =>
      c.put("k", 7) *> c.getOrLoad("k")(calls.update(_ + 1).as(0)).product(calls.get)
    }.assertEquals((7, 0))
  }

  test("the loaded value lands in the cache") {
    loading.flatMap { case (c, _) => c.getOrLoad("k")(IO.pure(5)) *> c.get("k") }
      .assertEquals(Some(5))
  }

  test("a failed load reaches every waiter and caches nothing") {
    loading.flatMap { case (c, _) =>
      val boom = IO.raiseError[Int](new IllegalStateException("load failed"))
      List.fill(10)(c.getOrLoad("k")(boom).attempt).parSequence.product(c.get("k"))
    }.map { case (rs, cached) =>
      assert(rs.forall(_.isLeft))
      assertEquals(cached, None)
    }
  }

  test("a failed load releases the flight for a retry") {
    loading.flatMap { case (c, calls) =>
      val once = calls.update(_ + 1)
      c.getOrLoad("k")(once *> IO.raiseError[Int](new Exception("first"))).attempt *>
        c.getOrLoad("k")(once.as(9)).product(calls.get)
    }.assertEquals((9, 2))
  }
}
