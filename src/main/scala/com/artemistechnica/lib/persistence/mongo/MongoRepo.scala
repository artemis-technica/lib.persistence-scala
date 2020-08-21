package com.artemistechnica.lib.persistence.mongo

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import cats.data.EitherT
import com.artemistechnica.lib.persistence.common.CommonResponse.RepoResponse
import com.artemistechnica.lib.persistence.common.{DatabaseError, DeleteError, ErrorCode, MongoError, ReadError, RepoError, UpdateError, WriteError}
import com.artemistechnica.lib.persistence.config.ConfigHelper
import com.artemistechnica.lib.persistence.mongo.MongoRepo.MongoResponse
import com.typesafe.config.{Config, ConfigFactory}
import reactivemongo.akkastream.State
import reactivemongo.api.{AsyncDriver, Cursor, DefaultDB}
import reactivemongo.api.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter}
import reactivemongo.api.bson.collection.BSONCollection
import reactivemongo.api.commands.{MultiBulkWriteResult, UpdateWriteResult, WriteResult}

import scala.collection.Factory
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

  /**
   * Get a reference to a Mongo collection. This can be used for more advanced interactions with Mongo; e.g. aggregate queries.
   * @param name
   * @param ec
   * @return
   */
  def collection(name: String)(implicit ec: ExecutionContext): MongoResponse[BSONCollection] = {
    for {
      db  <- Mongo.db
      col <- (asFuture(db.collection[BSONCollection](name)), DatabaseError)
    } yield col
  }

  /**
   * Query a collection for a single document.
   * @param collectionName The collection name query against. The collection will be automatically created if it does not exist.
   * @param query The query to perform against the collection.
   * @param ec Implicitly scoped ExecutionContext
   * @param r Implicitly scoped document reader to materialize the response, T.
   * @tparam T The response type requested by the caller.
   * @return MongoResponse[Option[T]]
   *
   * Example:
   *
   * val response: MongoResponse[Option[BSONDocument]] = readOne[BSONDocument]("exampleCollection")(BSONDocument("name" -> "Test Name"))
   */
  def readOne[T](collectionName: String)(query: BSONDocument)(implicit ec: ExecutionContext, r: BSONDocumentReader[T]): MongoResponse[Option[T]] = {
    for {
      col <- collection(collectionName)
      res <- (col.find[BSONDocument, BSONDocument](query).one[T], ReadError)
    } yield res
  }

  /**
   *
   * @param collectionName
   * @param query
   * @param maxDocs
   * @param ec
   * @param r
   * @param cbf
   * @tparam T
   * @tparam K
   * @return
   */
  def readMany[T, K[_]](collectionName: String)(query: BSONDocument, maxDocs: Int = -1)(implicit ec: ExecutionContext, r: BSONDocumentReader[T], cbf: Factory[T, K[T]]): MongoResponse[K[T]] = {
    for {
      col <- collection(collectionName)
      res <- (col.find[BSONDocument, BSONDocument](query).cursor[T]().collect[K](maxDocs, Cursor.FailOnError()), ReadError)
    } yield res
  }

  /**
   *
   * @param collectionName
   * @param entity
   * @param ec
   * @param w
   * @tparam T
   * @return
   */
  def insert[T](collectionName: String, entity: T)(implicit ec: ExecutionContext, w: BSONDocumentWriter[T]): MongoResponse[WriteResult] = {
    for {
      col <- collection(collectionName)
      res <- (col.insert.one[T](entity), WriteError)
    } yield res
  }

  /**
   *
   * @param collectionName
   * @param entities
   * @param orderedInserts
   * @param ec
   * @param w
   * @tparam T
   * @return
   */
  def batchInsert[T](collectionName: String, entities: Iterable[T], orderedInserts: Boolean = false)(implicit ec: ExecutionContext, w: BSONDocumentWriter[T]): MongoResponse[MultiBulkWriteResult] = {
    for {
      col <- collection(collectionName)
      res <- (col.insert(orderedInserts).many[T](entities), WriteError)
    } yield res
  }

  /**
   *
   * @param collectionName
   * @param entity
   * @param query
   * @param ec
   * @param w
   * @tparam T
   * @return
   */
  def upsert[T](collectionName: String, entity: T)(query: BSONDocument)(implicit ec: ExecutionContext, w: BSONDocumentWriter[T]): MongoResponse[UpdateWriteResult] = {
    for {
      col <- collection(collectionName)
      res <- (col.update.one(query, entity, true, false), UpdateError)
    } yield res
  }

  /**
   *
   * @param collectionName
   * @param query
   * @param updateStatement
   * @param ec
   * @return
   */
  def updateOne(collectionName: String)(query: BSONDocument, updateStatement: BSONDocument)(implicit ec: ExecutionContext): MongoResponse[UpdateWriteResult] = {
    for {
      col <- collection(collectionName)
      res <- (col.update.one(query, updateStatement, false, false), UpdateError)
    } yield res
  }

  /**
   *
   * @param collectionName
   * @param ordered
   * @param query
   * @param updateStatement
   * @param ec
   * @return
   */
  def updateMany(collectionName: String, ordered: Boolean = false)(query: BSONDocument, updateStatement: BSONDocument)(implicit ec: ExecutionContext): MongoResponse[UpdateWriteResult] = {
    for {
      col <- collection(collectionName)
      res <- (col.update(ordered).one(query, updateStatement, false, true), UpdateError)
    } yield res
  }

  /**
   *
   * @param collectionName
   * @param query
   * @param ec
   * @return
   */
  def deleteOne(collectionName: String)(query: BSONDocument)(implicit ec: ExecutionContext): MongoResponse[WriteResult] = {
    for {
      col <- collection(collectionName)
      res <- (col.delete.one(query), DeleteError)
    } yield res
  }

  /**
   *
   * @param collectionName
   * @param query
   * @param maxDocs
   * @param ec
   * @param m
   * @param r
   * @tparam T
   * @return
   */
  def stream[T](collectionName: String)(query: BSONDocument, maxDocs: Int = -1)(implicit ec: ExecutionContext, m: Materializer, r: BSONDocumentReader[T]): MongoResponse[Source[T, Future[State]]] = {
    // For materializing an Akka Source
    import reactivemongo.akkastream.cursorProducer
    for {
      col <- collection(collectionName)
    } yield col.find[BSONDocument, BSONDocument](query).cursor[T]().documentSource()
  }
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
