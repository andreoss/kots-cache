package kots.cache

import cats.effect.IO
import cats.effect.kernel.Ref

final class CacheSuite extends CacheContract {

  def cache: IO[Cache[IO, String, Int]] =
    Ref.of[IO, Map[String, Int]](Map.empty).map { ref =>
      new Cache[IO, String, Int] {
        def get(key: String): IO[Option[Int]] = ref.get.map(_.get(key))
        def put(key: String, value: Int): IO[Unit] =
          ref.update(_.updated(key, value))
        def modify[A](key: String)(f: Option[Int] => (Option[Int], A)): IO[A] =
          ref.modify { m =>
            val (next, a) = f(m.get(key))
            (next.fold(m - key)(v => m.updated(key, v)), a)
          }
        def remove(key: String): IO[Unit] = ref.update(_ - key)
        def clear: IO[Unit] = ref.set(Map.empty)
      }
    }
}
