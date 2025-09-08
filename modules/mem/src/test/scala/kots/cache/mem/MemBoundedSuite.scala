package kots.cache.mem

import cats.effect.IO
import cats.syntax.all._
import kots.cache.{Cache, CacheContract}
import munit.CatsEffectSuite

final class MemBoundedContractSuite extends CacheContract {
  def cache: IO[Cache[IO, String, Int]] = MemCache.bounded[IO, String, Int](1000)
}

final class MemBoundedSuite extends CatsEffectSuite {

  test("putting beyond the bound evicts the least recently used entry") {
    MemCache.bounded[IO, String, Int](2).flatMap { c =>
      c.put("a", 1) *> c.put("b", 2) *> c.put("c", 3) *>
        (c.get("a"), c.get("b"), c.get("c")).tupled
    }.assertEquals((None, Some(2), Some(3)))
  }

  test("a read refreshes recency") {
    MemCache.bounded[IO, String, Int](2).flatMap { c =>
      c.put("a", 1) *> c.put("b", 2) *> c.get("a") *> c.put("c", 3) *>
        (c.get("a"), c.get("b"), c.get("c")).tupled
    }.assertEquals((Some(1), None, Some(3)))
  }

  test("an insert through modify respects the bound") {
    MemCache.bounded[IO, String, Int](2).flatMap { c =>
      c.put("a", 1) *> c.put("b", 2) *> c.modify("c")(o => (Some(3), o)) *>
        (c.get("a"), c.get("b"), c.get("c")).tupled
    }.assertEquals((None, Some(2), Some(3)))
  }

  test("the bound holds under concurrent puts") {
    MemCache.bounded[IO, String, Int](10).flatMap { c =>
      (1 to 100).toList.parTraverse_(i => c.put(i.toString, i)) *>
        (1 to 100).toList.traverse(i => c.get(i.toString)).map(_.flatten.size)
    }.assertEquals(10)
  }

  test("overwriting a present key does not evict") {
    MemCache.bounded[IO, String, Int](2).flatMap { c =>
      c.put("a", 1) *> c.put("b", 2) *> c.put("a", 9) *>
        (c.get("a"), c.get("b")).tupled
    }.assertEquals((Some(9), Some(2)))
  }
}
