package com.artemistechnica.lib.persistence.sql.postgresql

import cats.data.EitherT
import com.artemistechnica.lib.persistence.common.CommonResponse.RepoResponse
import com.artemistechnica.lib.persistence.common._
import com.artemistechnica.lib.persistence.config.ConfigHelper
import com.artemistechnica.lib.persistence.sql.common.{SqlRepo, SqlTableList}
import com.artemistechnica.lib.persistence.sql.postgresql
import com.artemistechnica.lib.persistence.sql.postgresql.PostgresRepo.PostgresResponse
import com.typesafe.config.ConfigFactory
import slick.basic.DatabasePublisher
import slick.dbio.{DBIOAction, Effect, NoStream, StreamingDBIO}
import slick.sql.SqlAction

import scala.concurrent.stm.{Ref, atomic}
import scala.concurrent.{ExecutionContext, Future}

// The all important postgres api import
import com.artemistechnica.lib.persistence.sql.postgresql.PostgresApiProfile.api._

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
trait PostgresRepo[A <: PostgresTableList] extends SqlRepo[A, Table, PostgresResponse] with PostgresResponseGen {

  import cats.implicits.catsStdInstancesForFuture

  def tables: A

  override def run[T](a: DBIOAction[T, NoStream, _])(implicit ec: ExecutionContext): PostgresResponse[T] = {
    for {
      database  <- PostgresRepo.db
      results   <- (database.run(a), GeneralError)
    } yield results
  }

  override def read[T](query: A => SqlAction[T, NoStream, Effect.Read])(implicit ec: ExecutionContext): PostgresResponse[T] = {
    for {
      database  <- PostgresRepo.db
      results   <- (database.run(query(tables)), ReadError)
    } yield results
  }

  override def write[T](query: A => SqlAction[T, NoStream, Effect.Write])(implicit ec: ExecutionContext): PostgresResponse[T] = {
    for {
      database  <- PostgresRepo.db
      results   <- (database.run(query(tables)), WriteError)
    } yield results
  }

  override def stream[T](buffer: Boolean, query: A => StreamingDBIO[_, T])(implicit ec: ExecutionContext): PostgresResponse[DatabasePublisher[T]] = {
    for {
      database  <- PostgresRepo.db
    } yield database.stream(query(tables), buffer)
  }

  override def delete(query: A => Query[Table[_], _, Seq])(implicit ec: ExecutionContext): PostgresResponse[Int] = {
    for {
      database  <- PostgresRepo.db
      results   <- (database.run(query(tables).delete), DeleteError)
    } yield results
  }
}

object PostgresRepo extends ConfigHelper with PostgresResponseGen {

  import PostgresApiProfile.api._
  import cats.implicits.catsStdInstancesForFuture

  type PostgresDB = postgresql.PostgresApiProfile.backend.DatabaseDef
  type PostgresResponse[T] = RepoResponse[Future, PostgresError, T]

  // TODO - Externalize root config path
  private lazy val config = ConfigFactory.load
  private lazy val dbRef  = Ref[Option[PostgresDB]](None)
  /**
   * Used to resolve a Postgres database reference.
   * @param ec Implicitly scoped ExecutionContext
   * @return PostgresResponse[PostgresDB]
   */
  def db(implicit ec: ExecutionContext): PostgresResponse[PostgresDB] = {
    def tryDBRef: PostgresResponse[Option[PostgresDB]] = {
      for {
        c   <- (Future(if (config.hasPathOrNull("db.postgres")) config else throw new Exception("No database configuration found at path db.postgres")), GeneralError)
        db  <- (Future(Database.forConfig("db.postgres", c)), DatabaseError)
      } yield atomic { implicit txs => dbRef.transformAndGet(_ => Some(db)) }
    }
    // Atomic transaction will init a PostgresDB exactly once and reuse the db instance going forward.
    atomic { implicit txs =>
      for {
        db <- dbRef().fold(tryDBRef)(db => (Future(Option(db)), DatabaseError).toResponse)
      } yield db.get
    }
  }
}

// Extends the trait or import the companion object's contents for implicit Tuple[T, ResponseError] to PostgresResponse[T] transformations
trait PostgresResponseGen {
  import PostgresErrorHandler._
  implicit def asFuture[T](t: T): Future[T] = Future.successful(t)
  implicit def toRepoResponse[T, E <: RepoError, F[_] <: RepoResponse[Future, E, T]](tx: (Future[T], E))(implicit ec: ExecutionContext): PostgresResponse[T] = EitherT(tx._1.map(Right(_)).recover(recoverPF(tx._2)))

  implicit class Op[T, E <: RepoError](tx: (Future[T], E)) {
    def toResponse(implicit ec: ExecutionContext): PostgresResponse[T] = toRepoResponse(tx)
  }
}
object PostgresResponseGen extends PostgresResponseGen

/**
 * Error handling for failed postgres queries where an exception is thrown.
 */
object PostgresErrorHandler {
  def recoverPF[T, E <: RepoError](err: E): PartialFunction[Throwable, Either[PostgresError, T]] = {
    case t: Throwable => Left(PostgresError(t.getMessage, err.code))
    case _            => Left(PostgresError("Unknown postgres error", ErrorCode.unknownError))
  }
}