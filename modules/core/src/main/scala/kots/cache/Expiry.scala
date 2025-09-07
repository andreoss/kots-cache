package kots.cache

import scala.concurrent.duration.FiniteDuration

/** Expiry policy: entries past time-to-live or time-to-idle read as absent. */
final case class Expiry(
  timeToLive: Option[FiniteDuration],
  timeToIdle: Option[FiniteDuration],
)

object Expiry {
  val none: Expiry = Expiry(None, None)
  def ttl(duration: FiniteDuration): Expiry = Expiry(Some(duration), None)
  def tti(duration: FiniteDuration): Expiry = Expiry(None, Some(duration))
}
