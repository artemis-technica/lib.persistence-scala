package com.artemistechnica.lib.persistence.sql.postgresql

import cats.data.EitherT
import com.artemistechnica.lib.persistence.common.CommonResponse.RepoResponse
import com.artemistechnica.lib.persistence.common.{ErrorCode, GeneralError, PostgresError, ReadError, RepoError, WriteError}
import com.artemistechnica.lib.persistence.config.ConfigHelper
import com.artemistechnica.lib.persistence.sql.common.{SqlRepo, SqlTableList}
import com.artemistechnica.lib.persistence.sql.postgresql.PostgresRepo.PostgresResponse
import com.typesafe.config.ConfigFactory
import slick.basic.DatabasePublisher
import slick.dbio.{DBIOAction, Effect, NoStream, StreamingDBIO}
import slick.sql.SqlAction

import scala.concurrent.{ExecutionContext, Future}

/**
 * Typed table list explicit for Postgres
 */
trait PostgresTableList extends SqlTableList

/**
 * Primary trait to mix-in for providing an interface to a Postgres database instance.
 * Postgres implementation specifics of a SQL repository. It is left to concrete implementations of this trait to define
 * a [[tables]] parameter.
 * @tparam A DB table descriptions to build a query against
 */
trait PostgresRepo[A <: PostgresTableList] extends SqlRepo[A, PostgresResponse] {
  // Provides a database reference and error handling
  import PostgresRepo._

  def tables: A

  override def run[T](a: DBIOAction[T, NoStream, _])(implicit ec: ExecutionContext): PostgresResponse[T] = (database.run(a), GeneralError)
  override def read[T](query: A => SqlAction[T, NoStream, Effect.Read])(implicit ec: ExecutionContext): PostgresResponse[T] = (database.run(query(tables)), ReadError)
  override def write[T](query: A => SqlAction[T, NoStream, Effect.Write])(implicit ec: ExecutionContext): PostgresResponse[T] = (database.run(query(tables)), WriteError)
  override def stream[T](buffer: Boolean, query: A => StreamingDBIO[_, T])(implicit ec: ExecutionContext): DatabasePublisher[T] = database.stream(query(tables), buffer)
}

object PostgresRepo extends ConfigHelper {

  import PostgresErrorHandler._
  import PostgresApiProfile.api._

  type PostgresResponse[T] = RepoResponse[Future, PostgresError, T]

  // TODO - Externalize root config path
  lazy val database = Database.forConfig("db.postgres", ConfigFactory.load)

  /**
   * Implicitly provides error handling by converting a (Future[T], RepoError) tuple to a PostgresResponse[T] providing a recover function for a failed future.
   * @param tx
   * @param ec
   * @tparam T
   * @tparam E
   * @return
   */
  implicit def toRepoResponse[T, E <: RepoError](tx: (Future[T], E))(implicit ec: ExecutionContext): PostgresResponse[T] = EitherT(tx._1.map(Right(_)).recover(recoverPF(tx._2)))
}

/**
 * Error handling for failed postgres queries where an exception is thrown.
 */
object PostgresErrorHandler {
  def recoverPF[T, E <: RepoError](err: E): PartialFunction[Throwable, Either[PostgresError, T]] = {
    case t: Throwable => Left(PostgresError(t.getMessage, err.code))
    case _            => Left(PostgresError("Unknown postgres error", ErrorCode.unknownError))
  }
}