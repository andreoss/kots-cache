package kots.cache.jcache

import cats.effect.IO
import javax.cache.Caching
import javax.cache.configuration.MutableConfiguration
import kots.cache.{Cache, CacheContract}

final class JCacheAdapterSuite extends CacheContract {

  def cache: IO[Cache[IO, String, Int]] =
    IO {
      val manager = Caching.getCachingProvider().getCacheManager()
      val name = s"contract-${java.util.UUID.randomUUID()}"
      val backing =
        manager.createCache[String, Int, MutableConfiguration[String, Int]](
          name,
          new MutableConfiguration[String, Int](),
        )
      JCacheAdapter.of[IO, String, Int](backing)
    }
}
