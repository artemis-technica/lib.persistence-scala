package com.artemistechnica.lib.persistence.sql.common

import slick.basic.DatabasePublisher
import slick.dbio.{DBIOAction, Effect, NoStream, StreamingDBIO}
import slick.lifted.Query
import slick.sql.SqlAction

import scala.concurrent.ExecutionContext

trait SqlTableList

/**
 * Base SQL trait. This is compatible with SQL-based DBs
 * @tparam A DB table descriptions to build a query against
 * @tparam K The DB table higher-kinded type. This is specific to the sql profile used.
 * @tparam R Higher-kinded response R[_]
 */
trait SqlRepo[A <: SqlTableList, K[_], R[_]] {
  /**
   * Run _some_ generalized action against the database. This could be reads, writes, stream, or any kind of raw query.
   * @param a
   * @param ec
   * @tparam T
   * @return
   */
  def run[T](a: DBIOAction[T, NoStream, _])(implicit ec: ExecutionContext): R[T]

  /**
   * Specify a read query action against a database.
   * @param query
   * @param ec
   * @tparam T
   * @return
   */
  def read[T](query: A => SqlAction[T, NoStream, Effect.Read])(implicit ec: ExecutionContext): R[T]

  /**
   * Specify a write query action against a database.
   * @param query
   * @param ec
   * @tparam T
   * @return
   */
  def write[T](query: A => SqlAction[T, NoStream, Effect.Write])(implicit ec: ExecutionContext): R[T]

  /**
   * Specify a delete query action against a database.
   * @param query
   * @param ec
   * @tparam T
   * @return
   */
  def delete(query: A => Query[K[_], _, Seq])(implicit ec: ExecutionContext): R[Int]

  /**
   * Specify streaming data from a database.
   * @param buffer
   * @param query
   * @param ec
   * @tparam T
   * @return
   */
  def stream[T](buffer: Boolean, query: A => StreamingDBIO[_, T])(implicit ec: ExecutionContext): R[DatabasePublisher[T]]
}
