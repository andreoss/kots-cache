package kots.cache.mem

import cats.effect.IO
import cats.effect.testkit.TestControl
import cats.syntax.all._
import kots.cache.Expiry
import munit.CatsEffectSuite

import scala.concurrent.duration._

final class MemIdleExpirySuite extends CatsEffectSuite {

  private def ttiCache =
    MemCache.expiring[IO, String, Int](Expiry.tti(1.minute))

  test("an idle entry expires once its time-to-idle elapses") {
    TestControl.executeEmbed {
      ttiCache.flatMap(c => c.put("a", 1) *> IO.sleep(1.minute) *> c.get("a"))
    }.assertEquals(None)
  }

  test("reads reset the idle timer") {
    TestControl.executeEmbed {
      ttiCache.flatMap { c =>
        c.put("a", 1) *>
          List.fill(4)(IO.sleep(30.seconds) *> c.get("a")).sequence_ *>
          IO.sleep(30.seconds) *> c.get("a")
      }
    }.assertEquals(Some(1))
  }

  test("time-to-live still bounds a busily read entry") {
    TestControl.executeEmbed {
      MemCache
        .expiring[IO, String, Int](Expiry(Some(2.minutes), Some(1.minute)))
        .flatMap { c =>
          c.put("a", 1) *>
            List.fill(4)(IO.sleep(30.seconds) *> c.get("a")).sequence_ *>
            c.get("a")
        }
    }.assertEquals(None)
  }

  test("the earlier of the two policies wins") {
    TestControl.executeEmbed {
      MemCache
        .expiring[IO, String, Int](Expiry(Some(2.minutes), Some(1.minute)))
        .flatMap(c => c.put("a", 1) *> IO.sleep(1.minute) *> c.get("a"))
    }.assertEquals(None)
  }
}
