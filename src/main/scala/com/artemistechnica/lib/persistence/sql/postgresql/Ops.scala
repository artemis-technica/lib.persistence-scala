package com.artemistechnica.lib.persistence.sql.postgresql

import java.sql.Timestamp

import com.artemistechnica.lib.persistence.sql.common.TimestampCon

/**
 * Injects date comparison operators for more convenient postgres date queries.
 *
 * Example:
 *
 * import PostgresTimestampOp._
 *
 * val dt0  = 1597848610
 * val dt1  = 2016-11-16 06:43:19.77
 * val dt2  = new Timestamp()
 *
 * val res0 = DB.read(_.users.filter(u => u.createDate =>= dt0))
 * val res1 = DB.read(_.users.filter(u => u.createDate =>= dt1))
 * val res2 = DB.read(_.users.filter(u => u.createDate =>= dt2))
 */
trait PostgresTimestampOp {

  import com.artemistechnica.lib.persistence.sql.postgresql.PostgresApiProfile.api._

  // TODO - Move from implicit class to trait
  implicit class Op(ts: Rep[Timestamp]) {
    // Greater than or equal to
    def =>=[A](a: A)(implicit con: TimestampCon[A]): Rep[Boolean] = ts >= con.construct(a)
    // Less than or equal to
    def =<=[A](a: A)(implicit con: TimestampCon[A]): Rep[Boolean] = ts <= con.construct(a)
    // Equal to
    def |==|[A](a: A)(implicit con: TimestampCon[A]): Rep[Boolean] = ts === con.construct(a)
  }
}

// Either mix-in the trait or import the companion object's contents.
object PostgresTimestampOp extends PostgresTimestampOp
