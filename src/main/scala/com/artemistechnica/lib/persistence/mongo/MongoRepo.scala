package com.artemistechnica.lib.persistence.mongo

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import cats.data.EitherT
import cats.implicits.catsStdInstancesForFuture
import com.artemistechnica.lib.persistence.common.CommonResponse.RepoResponse
import com.artemistechnica.lib.persistence.common._
import com.artemistechnica.lib.persistence.config.ConfigHelper
import com.artemistechnica.lib.persistence.mongo.MongoRepo.MongoResponse
import com.typesafe.config.{Config, ConfigFactory}
import reactivemongo.akkastream.State
import reactivemongo.api.bson.collection.BSONCollection
import reactivemongo.api.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter}
import reactivemongo.api.commands.{MultiBulkWriteResult, UpdateWriteResult, WriteResult}
import reactivemongo.api.{AsyncDriver, Cursor, DefaultDB}

import scala.collection.Factory
import scala.concurrent.stm.{Ref, atomic}
import scala.concurrent.{ExecutionContext, Future}

/**
 * Primary trait to interface with a Mongo database.
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
   * @param collectionName The collection name to query against. The collection will be automatically created if it does not exist.
   * @param query The query to perform against against the collection.
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
   * Query a collection for multiple documents
   * @param collectionName The collection name to query against. The collection will be automatically created if it does not exist.
   * @param query The query to perform against the collection.
   * @param sort Optionally sort the results. By default no sorting happens.
   * @param maxDocs Specify the maximum number of documents to return. Defaults to unlimited
   * @param ec Implicitly scoped ExecutionContext
   * @param r Implicitly scoped document reader to materialize typed results.
   * @param cbf Implicitly scoped Factory for constructing K[T] typed results.
   * @tparam T The document response type requested by the caller.
   * @tparam K The container encapsulating any T results; e.g. List[T].
   * @return MongoResponse[K[T]]
   */
  def readMany[T, K[_]](collectionName: String)(query: BSONDocument, sort: BSONDocument = BSONDocument.empty, maxDocs: Int = -1)(implicit ec: ExecutionContext, r: BSONDocumentReader[T], cbf: Factory[T, K[T]]): MongoResponse[K[T]] = {
    for {
      col <- collection(collectionName)
      res <- (col.find[BSONDocument, BSONDocument](query).sort(sort).cursor[T]().collect[K](maxDocs, Cursor.FailOnError()), ReadError)
    } yield res
  }

  /**
   * Insert a single document into a collection
   * @param collectionName The collection to insert a document into. The collection will be automatically created if it does not exist.
   * @param entity The entity to insert into the collection.
   * @param ec Implicitly scoped ExecutionContext
   * @param w Implicitly scoped document writer to create a BSONDocument from some T
   * @tparam T The entity type written to the collection
   * @return MongoResponse[WriteResult]
   */
  def insert[T](collectionName: String, entity: T)(implicit ec: ExecutionContext, w: BSONDocumentWriter[T]): MongoResponse[WriteResult] = {
    for {
      col <- collection(collectionName)
      res <- (col.insert.one[T](entity), WriteError)
    } yield res
  }

  /**
   * Insert multiple documents into a collection. This can be done sequentially or as an unordered operations.
   * @param collectionName The collection to insert the documents into.
   * @param entities The entities to insert into the collection
   * @param orderedInserts Boolean flag indicating if the operation should be sequential or unordered
   * @param ec Implicitly scoped ExecutionContext
   * @param w Implicitly scoped document writer to create a BSONDocument from some T
   * @tparam T The entity type written to the collection
   * @return MongoResponse[MultiBulkWriteResult]
   */
  def batchInsert[T](collectionName: String, entities: Iterable[T], orderedInserts: Boolean = false)(implicit ec: ExecutionContext, w: BSONDocumentWriter[T]): MongoResponse[MultiBulkWriteResult] = {
    for {
      col <- collection(collectionName)
      res <- (col.insert(orderedInserts).many[T](entities), WriteError)
    } yield res
  }

  /**
   * Allow an update statement to affect only the _first_ document matching the provided query. If no document matches the
   * provided query then a new document will be inserted.
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
   * Allow an update statement to affect only the _first_ document matching the provided query. This is strictly an update
   * and will not insert new documents.
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
   * Allow and update statement to affect _all_ documents matching the provided query.
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
   * Delete the first matching document to the provided query. Even if the query matches against many documents, only the first match will be deleted.
   * @param collectionName
   * @param query
   * @param ec
   * @return
   */
  def deleteOne(collectionName: String)(query: BSONDocument)(implicit ec: ExecutionContext): MongoResponse[WriteResult] = {
    for {
      col <- collection(collectionName)
      res <- (col.delete.one(query, limit = Some(1)), DeleteError)
    } yield res
  }

  /**
   * Allow a delete command to possibly delete up to a certain number of documents matching the provided query. By default
   * this does not set a limit and will delete _all_ documents matching the provided query.
   * @param collectionName
   * @param maxDeleteCount
   * @param query
   * @param ec
   * @return
   */
  def deleteMany(collectionName: String, maxDeleteCount: Option[Int] = None)(query: BSONDocument)(implicit ec: ExecutionContext): MongoResponse[WriteResult] = {
    for {
      col <- collection(collectionName)
      res <- (col.delete(ordered = false).one(query, maxDeleteCount), DeleteError)
    } yield res
  }

  /**
   * Batch execution of many distinct delete commands.
   * @param collectionName
   * @param ordered
   * @param maxDeleteCount
   * @param queries
   * @param ec
   * @return
   */
  def deleteManyDistinct(collectionName: String, ordered: Boolean = false, maxDeleteCount: Option[Int] = None)(queries: Seq[BSONDocument])(implicit ec: ExecutionContext): MongoResponse[MultiBulkWriteResult] = {
    for {
      col <- collection(collectionName)
      res <- {
        // Create the builder
        val builder = col.delete(ordered)
        // Create the commands the builder will execute
        val deletes = Future.sequence(queries.map(q => builder.element(q, maxDeleteCount)))
        // Execute each command. Tuple[T, RepoError] is implicitly converted to a MongoResponse[T]
        (deletes.flatMap(builder.many(_)), DeleteError)
      }
    } yield res
  }

  /**
   * Stream data from the database.
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
  def db(implicit ec: ExecutionContext): MongoResponse[DefaultDB] = for {
    config    <- MongoConfig.instance
    database  <- (config.conn.flatMap(_.database(config.dbName)), DatabaseError)
  } yield database
}

// Extends the trait or import the companion object's contents for implicit Tuple[T, ResponseError] to MongoResponse[T] transformations
trait MongoResponseGen {
  import MongoErrorHandler._
  implicit def asFuture[T](t: T): Future[T] = Future.successful(t)
  implicit def toRepoResponse[T, E <: RepoError, F[_] <: RepoResponse[Future, E, T]](tx: (Future[T], E))(implicit ec: ExecutionContext): MongoResponse[T] = EitherT(tx._1.map(Right(_)).recover(recoverPF(tx._2)))

  implicit class Op[T, E <: RepoError](tx: (Future[T], E)) {
    def toResponse(implicit ec: ExecutionContext): MongoResponse[T] = toRepoResponse(tx)
  }
}
object MongoResponseGen extends MongoResponseGen

// Error handling for failed mongo queries where an exception is thrown.
object MongoErrorHandler {
  def recoverPF[T, E <: RepoError](err: E): PartialFunction[Throwable, Either[MongoError, T]] = {
    case t: Throwable => Left(MongoError(t.getMessage, err.code))
    case _            => Left(MongoError("Unknown postgres error", ErrorCode.unknownError))
  }
}

// TODO - Expand configuration options
// TODO - Evaluate driver and connection references. This puts a requirements on MongoConfig to always be a singleton
case class MongoConfig(host: String, port: Int, dbName: String) {
  lazy val driver = AsyncDriver()
  lazy val conn   = driver.connect(s"mongodb://$host:$port")
}
object MongoConfig extends ConfigHelper with MongoResponseGen {

  private lazy val config     = ConfigFactory.load
  private lazy val configRef  = Ref[Option[MongoConfig]](None)

  /**
   * Used to retrieve an instance of a [[MongoConfig]]. This method will instantiate a configuration exactly once.
   * @param ec Implicitly scoped ExecutionContext
   * @return MongoResponse[MongoConfig]
   */
  def instance(implicit ec: ExecutionContext): MongoResponse[MongoConfig] = {
    // Attempt to build a mongo configuration from application config. Throw exception if config path is not found.
    def tryConfigConstruct: MongoResponse[MongoConfig] = {
      for {
        c <- (Future(if (config.hasPathOrNull("db.mongo")) config else throw new Exception("No database configuration found at path db.mongo")), GeneralError)
      } yield {
        MongoConfig(c)
      }
    }
    // Try constructing a mongo configuration and if successful atomically update the configRef's internal reference
    // and return the new mongo configuration.
    def tryConfigUpdate: MongoResponse[Option[MongoConfig]] = {
      for {
        c <- tryConfigConstruct
      } yield atomic { implicit txs => configRef.transformAndGet(_ => Some(c)) }
    }
    // Atomic transaction will init a MongoConfig exactly once and reuse the config instance going forward.
    atomic { implicit txs =>
      for {
        config <- configRef().fold(tryConfigUpdate)(c => (asFuture(Option(c)), GeneralError).toResponse)
      } yield config.get
    }
  }

  def apply(c: Config): MongoConfig = {
    val h = c.getOrThrow[String]("db.mongo.host")
    val p = c.getOrThrow[Int]("db.mongo.port")
    val d = c.getOrThrow[String]("db.mongo.dbName")
    MongoConfig(h, p, d)
  }
}
