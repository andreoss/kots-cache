package kots.cache.mem

import cats.effect.IO
import kots.cache.{Cache, CacheContract}

final class MemCacheSuite extends CacheContract {
  def cache: IO[Cache[IO, String, Int]] = MemCache.of[IO, String, Int]
}
