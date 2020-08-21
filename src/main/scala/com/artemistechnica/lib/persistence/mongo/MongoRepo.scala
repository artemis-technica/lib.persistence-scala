package com.artemistechnica.lib.persistence.mongo

import cats.data.EitherT
import com.artemistechnica.lib.persistence.common.CommonResponse.RepoResponse
import com.artemistechnica.lib.persistence.common.{DatabaseError, ErrorCode, MongoError, ReadError, RepoError, WriteError}
import com.artemistechnica.lib.persistence.config.ConfigHelper
import com.artemistechnica.lib.persistence.mongo.MongoRepo.MongoResponse
import com.typesafe.config.{Config, ConfigFactory}
import reactivemongo.api.{AsyncDriver, Cursor, DefaultDB}
import reactivemongo.api.bson.{BSONArray, BSONDocument, BSONDocumentReader, BSONDocumentWriter, BSONValue}
import reactivemongo.api.bson.collection.BSONCollection
import reactivemongo.api.commands.{MultiBulkWriteResult, WriteResult}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Primary trait to interface with MongoDB. [[MongoOp]] gives an easy interface for executing queries against
 * a Mongo database. [[MongoQueryHelper]] gives access to a simple query DSL.
 *
 * Example:
 * val dbName   = "some_database_name"
 * val colName  = "someCollectionName"
 *
 * dbName.query(colName).readOne[A](where("_id") is id)
 * dbName.query(colName).saveWhere[A](p, where("_id") is p.id)
 */
trait MongoRepo extends MongoResponseGen {

  import cats.implicits.catsStdInstancesForFuture

  private def getCollection(name: String)(implicit ec: ExecutionContext): MongoResponse[BSONCollection] = {
    for {
      db  <- Mongo.db
      col <- (asFuture(db.collection[BSONCollection](name)), DatabaseError)
    } yield col
  }

  def readOne[T](collection: String)(query: BSONDocument)(implicit ec: ExecutionContext, r: BSONDocumentReader[T]): MongoResponse[Option[T]] = {
    for {
      col <- getCollection(collection)
      res   <- (col.find[BSONDocument, BSONDocument](query).one[T], ReadError)
    } yield res
  }
  def readMany[T](collection: String)(query: BSONDocument, maxDocs: Int = -1)(implicit ec: ExecutionContext, r: BSONDocumentReader[T]): MongoResponse[List[T]] = {
    for {
      col <- getCollection(collection)
      res <- (col.find[BSONDocument, BSONDocument](query).cursor[T]().collect[List](maxDocs, Cursor.FailOnError()), ReadError)
    } yield res
  }
  def insert[T](collection: String, entity: T)(implicit ec: ExecutionContext, r: BSONDocumentWriter[T]): MongoResponse[WriteResult] = {
    for {
      col <- getCollection(collection)
      res <- (col.insert(false).one[T](entity), WriteError)
    } yield res
  }
  def batchInsert[T](collection: String, entities: Iterable[T], orderedInserts: Boolean = false)(implicit ec: ExecutionContext, r: BSONDocumentWriter[T]): MongoResponse[MultiBulkWriteResult] = {
    for {
      col <- getCollection(collection)
      res <- (col.insert(orderedInserts).many[T](entities), WriteError)
    } yield res
  }
  // TODO - Implement me!
//  def upsert[T](collection: String, entity: T)(implicit ec: ExecutionContext, r: BSONDocumentReader[T]): MongoResponse[Option[T]]
//  def deleteOne(collection: String)(query: BSONDocument)(implicit ec: ExecutionContext): MongoResponse[Unit]
//  def deleteMany(collection: String)(query: BSONDocument)(implicit ec: ExecutionContext): MongoResponse[Int]
//  def stream[T](collection: String)(implicit ec: ExecutionContext, r: BSONDocumentReader[T]): MongoResponse[Option[T]]
}

object MongoRepo {
  type MongoResponse[T] = RepoResponse[Future, MongoError, T]
}

object Mongo extends MongoResponseGen {
  private val driver  = AsyncDriver()
  private val conn    = driver.connect(s"mongodb://${MongoConfig.instance.host}:${MongoConfig.instance.port}")
  def db(implicit ec: ExecutionContext): MongoResponse[DefaultDB] = (conn.flatMap(_.database(MongoConfig.instance.dbName)), DatabaseError)
}

trait MongoResponseGen {
  import MongoErrorHandler._
  implicit def asFuture[T](t: T): Future[T] = Future.successful(t)
  implicit def toRepoResponse[T, E <: RepoError, F[_] <: RepoResponse[Future, E, T]](tx: (Future[T], E))(implicit ec: ExecutionContext): MongoResponse[T] = EitherT(tx._1.map(Right(_)).recover(recoverPF(tx._2)))

  implicit class Op[T, E <: RepoError](tx: (Future[T], E)) {
    def toResponse(implicit ec: ExecutionContext): MongoResponse[T] = toRepoResponse(tx)
  }
}

/**
 * Error handling for failed mongo queries where an exception is thrown.
 */
object MongoErrorHandler {
  def recoverPF[T, E <: RepoError](err: E): PartialFunction[Throwable, Either[MongoError, T]] = {
    case t: Throwable => Left(MongoError(t.getMessage, err.code))
    case _            => Left(MongoError("Unknown postgres error", ErrorCode.unknownError))
  }
}

case class MongoConfig(host: String, port: Int, dbName: String)

object MongoConfig extends ConfigHelper {

  // Singleton app configuration instance
  lazy val instance = MongoConfig(ConfigFactory.load)

  def apply(c: Config): MongoConfig = {
    val h = c.getOrThrow[String]("db.mongo.host")
    val p = c.getOrThrow[Int]("db.mongo.port")
    val d = c.getOrThrow[String]("db.mongo.dbName")
    MongoConfig(h, p, d)
  }
}
