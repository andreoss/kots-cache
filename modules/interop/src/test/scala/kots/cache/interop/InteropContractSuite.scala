package kots.cache.interop

import cats.arrow.FunctionK
import cats.effect.IO
import cats.~>
import kots.cache.{Cache, CacheContract}
import kots.cache.mem.MemCache
import zio.interop.catz._
import zio.{Runtime, Task, Unsafe}

final class InteropContractSuite extends CacheContract {

  private val runtime = Runtime.default

  private val taskToIo: Task ~> IO =
    new FunctionK[Task, IO] {
      def apply[A](task: Task[A]): IO[A] =
        IO.fromFuture(IO(Unsafe.unsafe(implicit u => runtime.unsafe.runToFuture(task))))
    }

  def cache: IO[Cache[IO, String, Int]] =
    taskToIo(MemCache.of[Task, String, Int].map(CacheInterop.mapK(_)(taskToIo)))
}
