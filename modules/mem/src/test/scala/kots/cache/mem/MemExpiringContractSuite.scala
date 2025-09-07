package kots.cache.mem

import cats.effect.IO
import kots.cache.{Cache, CacheContract, Expiry}

import scala.concurrent.duration._

final class MemExpiringContractSuite extends CacheContract {
  def cache: IO[Cache[IO, String, Int]] =
    MemCache.expiring[IO, String, Int](Expiry.ttl(1.day))
}
