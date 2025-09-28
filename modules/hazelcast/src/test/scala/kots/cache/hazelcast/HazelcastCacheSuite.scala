package kots.cache.hazelcast

import cats.effect.IO
import com.hazelcast.client.HazelcastClient
import kots.cache.{Cache, CacheContract}

final class HazelcastCacheSuite extends CacheContract {

  private lazy val client = HazelcastClient.newHazelcastClient()

  override def afterAll(): Unit = {
    client.shutdown()
    super.afterAll()
  }

  def cache: IO[Cache[IO, String, Int]] =
    IO {
      HazelcastCache.of[IO, String, Int](
        client.getMap(s"contract-${java.util.UUID.randomUUID()}"),
      )
    }
}
