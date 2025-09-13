package kots.cache.redis

import cats.effect.IO
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.effect.Log.NoOp._
import kots.cache.{Cache, CacheContract, Codec}

final class RedisCacheSuite extends CacheContract {

  private lazy val allocated: (RedisCommands[IO, String, String], IO[Unit]) =
    Redis[IO].utf8("redis://127.0.0.1").allocated.unsafeRunSync()(munitIORuntime)

  override def afterAll(): Unit = {
    allocated._2.unsafeRunSync()(munitIORuntime)
    super.afterAll()
  }

  def cache: IO[Cache[IO, String, Int]] =
    IO {
      RedisCache.of[IO, String, Int](
        allocated._1,
        Codec.string,
        Codec.int,
        namespace = s"contract-${java.util.UUID.randomUUID()}",
        timeToLive = None,
      )
    }
}
