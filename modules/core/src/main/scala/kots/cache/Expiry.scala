package kots.cache

import scala.concurrent.duration.FiniteDuration

/** Expiry policy: entries past their time-to-live read as absent. */
final case class Expiry(timeToLive: Option[FiniteDuration])

object Expiry {
  val none: Expiry = Expiry(None)
  def ttl(duration: FiniteDuration): Expiry = Expiry(Some(duration))
}
