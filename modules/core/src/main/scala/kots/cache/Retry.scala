package kots.cache

import cats.MonadThrow
import cats.syntax.all._

/** Bounds the compare-and-set attempts a remote adapter spends on one key. */
final case class Retry(maxAttempts: Int) {

  /** Repeats a compare-and-set until it wins or the budget is spent. */
  def cas[F[_], A](attempt: F[Option[A]])(implicit F: MonadThrow[F]): F[A] = {
    def loop(left: Int): F[A] =
      attempt.flatMap {
        case Some(won)        => F.pure(won)
        case None if left > 1 => loop(left - 1)
        case None             => F.raiseError(CasExhausted(maxAttempts))
      }
    loop(maxAttempts max 1)
  }
}

object Retry {

  /** Budget every remote adapter uses until a caller names its own. */
  val default: Retry = Retry(512)

  /** Budget of n attempts, never fewer than one. */
  def attempts(n: Int): Retry = Retry(n max 1)
}

/** Raised when a compare-and-set never wins within its attempt budget. */
final case class CasExhausted(attempts: Int)
  extends RuntimeException(s"compare-and-set spent $attempts attempts")
