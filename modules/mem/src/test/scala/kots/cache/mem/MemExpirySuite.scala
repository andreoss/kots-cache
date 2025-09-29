package kots.cache.mem

import cats.effect.IO
import cats.effect.testkit.TestControl
import cats.syntax.all._
import kots.cache.Expiry
import munit.CatsEffectSuite

import scala.concurrent.duration._

final class MemExpirySuite extends CatsEffectSuite {

  private def ttlCache =
    MemCache.expiring[IO, String, Int](Expiry.ttl(1.minute))

  test("a live entry is visible before its time-to-live elapses") {
    TestControl.executeEmbed {
      ttlCache.flatMap(c => c.put("a", 1) *> IO.sleep(59.seconds) *> c.get("a"))
    }.assertEquals(Some(1))
  }

  test("an entry expires once its time-to-live elapses") {
    TestControl.executeEmbed {
      ttlCache.flatMap(c => c.put("a", 1) *> IO.sleep(1.minute) *> c.get("a"))
    }.assertEquals(None)
  }

  test("put restarts the time-to-live") {
    TestControl.executeEmbed {
      ttlCache.flatMap { c =>
        c.put("a", 1) *> IO.sleep(45.seconds) *> c.put("a", 2) *>
          IO.sleep(45.seconds) *> c.get("a")
      }
    }.assertEquals(Some(2))
  }

  test("modify sees an expired entry as absent") {
    TestControl.executeEmbed {
      ttlCache.flatMap { c =>
        c.put("a", 1) *> IO.sleep(1.minute) *> c.modify("a")(o => (Some(9), o))
      }
    }.assertEquals(None)
  }

  test("an expiry death reports the entry lifetime") {
    TestControl.executeEmbed {
      cats.effect.kernel.Ref.of[IO, List[FiniteDuration]](Nil).flatMap { ages =>
        val metrics = new kots.cache.CacheMetrics[IO] {
          def hit: IO[Unit] = IO.unit
          def miss: IO[Unit] = IO.unit
          def load: IO[Unit] = IO.unit
          def eviction: IO[Unit] = IO.unit
          def getLatency(duration: FiniteDuration): IO[Unit] = IO.unit
          def loadLatency(duration: FiniteDuration, success: Boolean): IO[Unit] = IO.unit
          def entryLifetime(age: FiniteDuration): IO[Unit] = ages.update(age :: _)
        }
        MemCache.expiring[IO, String, Int](Expiry.ttl(1.minute), metrics).flatMap { c =>
          c.put("a", 1) *> IO.sleep(90.seconds) *> c.get("a") *> ages.get
        }
      }
    }.assertEquals(List(90.seconds))
  }

  test("no expiry keeps entries forever") {
    TestControl.executeEmbed {
      MemCache
        .expiring[IO, String, Int](Expiry.none)
        .flatMap(c => c.put("a", 1) *> IO.sleep(365.days) *> c.get("a"))
    }.assertEquals(Some(1))
  }
}
