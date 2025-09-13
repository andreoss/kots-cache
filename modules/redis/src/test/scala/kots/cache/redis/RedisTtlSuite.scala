package kots.cache.redis

import cats.effect.IO
import cats.syntax.all._
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.effect.Log.NoOp._
import kots.cache.Codec
import munit.CatsEffectSuite

import scala.concurrent.duration._

final class RedisTtlSuite extends CatsEffectSuite {

  private lazy val allocated: (RedisCommands[IO, String, String], IO[Unit]) =
    Redis[IO].utf8("redis://127.0.0.1").allocated.unsafeRunSync()(munitIORuntime)

  override def afterAll(): Unit = {
    allocated._2.unsafeRunSync()(munitIORuntime)
    super.afterAll()
  }

  private def cacheIn(ns: String) =
    RedisCache.of[IO, String, Int](
      allocated._1,
      Codec.string,
      Codec.int,
      namespace = ns,
      timeToLive = Some(30.seconds),
    )

  test("the time-to-live lands as backend expiry") {
    val ns = s"ttl-${java.util.UUID.randomUUID()}"
    val c = cacheIn(ns)
    c.put("a", 1) *> allocated._1.pttl(s"$ns:a").map { remaining =>
      assert(remaining.exists(r => r > 20.seconds && r <= 30.seconds))
    }
  }

  test("an entry expires on the backend once its time-to-live elapses") {
    val ns = s"exp-${java.util.UUID.randomUUID()}"
    val c = RedisCache.of[IO, String, Int](
      allocated._1,
      Codec.string,
      Codec.int,
      namespace = ns,
      timeToLive = Some(200.millis),
    )
    c.put("a", 1) *> IO.sleep(400.millis) *> c.get("a").assertEquals(None)
  }

  test("modify keeps the time-to-live on the written value") {
    val ns = s"mod-${java.util.UUID.randomUUID()}"
    val c = cacheIn(ns)
    c.put("a", 1) *> c.modify("a")(o => (o.map(_ + 1), ())) *>
      allocated._1.pttl(s"$ns:a").map(r => assert(r.exists(_ > 20.seconds)))
  }
}
