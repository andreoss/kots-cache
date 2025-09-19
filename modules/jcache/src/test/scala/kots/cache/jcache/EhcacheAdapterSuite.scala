package kots.cache.jcache

import cats.effect.IO
import kots.cache.{Cache, CacheContract}

final class EhcacheAdapterSuite extends CacheContract {

  def cache: IO[Cache[IO, String, Int]] =
    JCacheTestSupport.cacheVia("org.ehcache.jsr107.EhcacheCachingProvider")
}
