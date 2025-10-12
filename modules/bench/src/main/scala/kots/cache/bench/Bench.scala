package kots.cache.bench

import cats.effect.{IO, IOApp}
import cats.effect.kernel.{Clock, Ref}
import cats.syntax.all._
import kots.cache.mem.MemCache

import scala.concurrent.duration.FiniteDuration

object Bench {

  final case class Report(label: String, ops: Int, elapsed: FiniteDuration) {
    def show: String = s"$label: $ops mixed put/get ops in ${elapsed.toMillis} ms"
  }

  private def timed(label: String, ops: Int)(op: Int => IO[Unit]): IO[Report] =
    Clock[IO].timed((1 to ops).toList.traverse_(op)).map { case (elapsed, _) =>
      Report(label, ops, elapsed)
    }

  /** Runs the comparison and prints one line per contender. */
  def report(ops: Int): IO[Unit] =
    run(ops).flatMap(_.traverse_(r => IO.println(r.show)))

  /** Times mixed put/get rounds on the mem adapter and a bare ref map. */
  def run(ops: Int): IO[List[Report]] =
    for {
      cache <- MemCache.of[IO, String, Int]
      adapter <- timed("mem adapter", ops) { i =>
        cache.put((i % 100).toString, i) *> cache.get(((i + 50) % 100).toString).void
      }
      ref <- Ref.of[IO, Map[String, Int]](Map.empty)
      baseline <- timed("baseline ref map", ops) { i =>
        ref.update(_.updated((i % 100).toString, i)) *>
          ref.get.map(_.get(((i + 50) % 100).toString)).void
      }
    } yield List(adapter, baseline)
}

object Main extends IOApp.Simple {
  def run: IO[Unit] = Bench.report(100000)
}
