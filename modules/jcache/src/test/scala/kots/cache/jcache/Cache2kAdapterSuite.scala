package kots.cache.jcache

import cats.effect.IO
import kots.cache.{Cache, CacheContract}

final class Cache2kAdapterSuite extends CacheContract {

  def cache: IO[Cache[IO, String, Int]] =
    JCacheTestSupport.cacheVia("org.cache2k.jcache.provider.JCacheProvider")
}
