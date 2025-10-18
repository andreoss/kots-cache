package kots.cache

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.syntax.all._

import scala.concurrent.duration.FiniteDuration

/** Recording metrics sink for tests; every signal lands in a Ref. */
final case class MetricsProbe(
  hits: Ref[IO, Int],
  misses: Ref[IO, Int],
  loads: Ref[IO, Int],
  evictions: Ref[IO, Int],
  getLatencies: Ref[IO, List[FiniteDuration]],
  loadLatencies: Ref[IO, List[(FiniteDuration, Boolean)]],
  lifetimes: Ref[IO, List[FiniteDuration]],
) extends CacheMetrics[IO] {
  def hit: IO[Unit] = hits.update(_ + 1)
  def miss: IO[Unit] = misses.update(_ + 1)
  def load: IO[Unit] = loads.update(_ + 1)
  def eviction: IO[Unit] = evictions.update(_ + 1)
  def getLatency(duration: FiniteDuration): IO[Unit] =
    getLatencies.update(duration :: _)
  def loadLatency(duration: FiniteDuration, success: Boolean): IO[Unit] =
    loadLatencies.update((duration, success) :: _)
  def entryLifetime(age: FiniteDuration): IO[Unit] = lifetimes.update(age :: _)
}

object MetricsProbe {

  val make: IO[MetricsProbe] =
    (
      Ref.of[IO, Int](0),
      Ref.of[IO, Int](0),
      Ref.of[IO, Int](0),
      Ref.of[IO, Int](0),
      Ref.of[IO, List[FiniteDuration]](Nil),
      Ref.of[IO, List[(FiniteDuration, Boolean)]](Nil),
      Ref.of[IO, List[FiniteDuration]](Nil),
    ).mapN(MetricsProbe.apply)
}
