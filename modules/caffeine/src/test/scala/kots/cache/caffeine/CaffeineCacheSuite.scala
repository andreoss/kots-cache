package kots.cache.caffeine

import cats.effect.IO
import com.github.benmanes.caffeine.cache.Caffeine
import kots.cache.{Cache, CacheContract}

final class CaffeineCacheSuite extends CacheContract {

  def cache: IO[Cache[IO, String, Int]] =
    IO(CaffeineCache.of[IO, String, Int](Caffeine.newBuilder().build[String, Int]()))
}
