package kots.cache.infinispan

import cats.effect.IO
import cats.syntax.all._
import kots.cache.Codec
import munit.CatsEffectSuite
import org.infinispan.client.hotrod.DefaultTemplate

import scala.concurrent.duration._

final class InfinispanTtlSuite extends CatsEffectSuite {

  private def cacheIn(name: String, ttl: FiniteDuration) = {
    val remote = InfinispanTestSupport.manager
      .administration()
      .getOrCreateCache(name, DefaultTemplate.DIST_SYNC)
      .asInstanceOf[org.infinispan.client.hotrod.RemoteCache[String, String]]
    (remote, InfinispanCache.of[IO, String, Int](remote, Codec.string, Codec.int, Some(ttl)))
  }

  test("the time-to-live lands as entry lifespan") {
    val (remote, c) = cacheIn(s"ttl-${java.util.UUID.randomUUID()}", 30.seconds)
    c.put("a", 1) *> IO.blocking {
      val meta = remote.getWithMetadata("a")
      assertEquals(meta.getLifespan, 30)
    }
  }

  test("an entry expires on the backend once its time-to-live elapses") {
    val (_, c) = cacheIn(s"exp-${java.util.UUID.randomUUID()}", 1.second)
    c.put("a", 1) *> IO.sleep(2500.millis) *> c.get("a").assertEquals(None)
  }
}
