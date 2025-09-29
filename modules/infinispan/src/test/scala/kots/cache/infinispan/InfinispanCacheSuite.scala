package kots.cache.infinispan

import cats.effect.IO
import kots.cache.{Cache, CacheContract, Codec}
import org.infinispan.client.hotrod.{DefaultTemplate, RemoteCacheManager}
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder

object InfinispanTestSupport {

  lazy val manager: RemoteCacheManager = {
    val builder = new ConfigurationBuilder()
    builder
      .addServer()
      .host("127.0.0.1")
      .port(11222)
      .security()
      .authentication()
      .username("admin")
      .password("password")
    new RemoteCacheManager(builder.build())
  }
}

final class InfinispanCacheSuite extends CacheContract {

  def cache: IO[Cache[IO, String, Int]] =
    IO {
      val name = s"contract-${java.util.UUID.randomUUID()}"
      val remote = InfinispanTestSupport.manager
        .administration()
        .getOrCreateCache(name, DefaultTemplate.DIST_SYNC)
        .asInstanceOf[org.infinispan.client.hotrod.RemoteCache[String, String]]
      InfinispanCache.of[IO, String, Int](remote, Codec.string, Codec.int, timeToLive = None)
    }
}
