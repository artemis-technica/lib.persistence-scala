package com.artemistechnica.lib.persistence.sql.common

import java.sql.Timestamp
import java.time.LocalDateTime

import scala.util.{Failure, Success, Try}

/**
 * Implicit constructs for instantiating a [[TimestampCon]] from some type A. This is to construct
 * a Timestamp from some A and typically used for date comparison queries.
 */
object TimestampConImplicits {
  // Available timestamp constructs
  implicit val longToTimestamp: TimestampCon[Long] = new TimestampCon[Long] {
    override def construct(a: Long): Timestamp = new Timestamp(a)
  }
  implicit val stringToTimestamp: TimestampCon[String] = new TimestampCon[String] {
    override def construct(a: String): Timestamp = Try(LocalDateTime.parse(a)) match {
      case Success(t) => Timestamp.valueOf(t)
      case Failure(e) => throw e  // TODO - Evaluate if throwing an exception is correct here
    }
  }
  implicit val identityTimestamp: TimestampCon[Timestamp] = new TimestampCon[Timestamp] {
    override def construct(a: Timestamp): Timestamp = a
  }
}

/**
 * Type class for constructing a java.sql.Timestamp from some type A
 * @tparam A The argument to create a Timestamp instance from.
 */
trait TimestampCon[A] {
  def construct(a: A): Timestamp
}
