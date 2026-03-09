package kots.cache.redis

import cats.effect.IO
import cats.syntax.all._
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.effect.Log.NoOp._
import kots.cache.{Codec, Retry}
import munit.CatsEffectSuite

import java.util.UUID

final class RedisCasSuite extends CatsEffectSuite {

  private lazy val allocated: (RedisCommands[IO, String, String], IO[Unit]) =
    Redis[IO].utf8("redis://127.0.0.1").allocated.unsafeRunSync()(munitIORuntime)

  override def afterAll(): Unit = {
    allocated._2.unsafeRunSync()(munitIORuntime)
    super.afterAll()
  }

  test("modify wins within a single-attempt budget when uncontended") {
    val ns = s"cas-${UUID.randomUUID()}"
    val c = RedisCache.of[IO, String, Int](
      allocated._1,
      Codec.string,
      Codec.int,
      namespace = ns,
      timeToLive = None,
      retry = Retry.attempts(1),
    )
    c.put("a", 1) *> c.modify("a")(o => (o.map(_ + 1), o)).assertEquals(Some(1)) *>
      c.get("a").assertEquals(Some(2))
  }
}
