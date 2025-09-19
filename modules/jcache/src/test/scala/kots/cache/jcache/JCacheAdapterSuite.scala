package kots.cache.jcache

import cats.effect.IO
import kots.cache.{Cache, CacheContract}

final class JCacheAdapterSuite extends CacheContract {

  def cache: IO[Cache[IO, String, Int]] =
    JCacheTestSupport.cacheVia("com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider")
}
