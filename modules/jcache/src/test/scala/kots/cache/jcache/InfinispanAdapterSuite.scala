package kots.cache.jcache

import cats.effect.IO
import kots.cache.{Cache, CacheContract}

final class InfinispanAdapterSuite extends CacheContract {

  def cache: IO[Cache[IO, String, Int]] =
    JCacheTestSupport.cacheVia("org.infinispan.jcache.embedded.JCachingProvider")
}
