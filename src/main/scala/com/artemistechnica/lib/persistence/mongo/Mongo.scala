package com.artemistechnica.lib.persistence.mongo

import com.artemistechnica.lib.persistence.config.ConfigHelper
import com.artemistechnica.lib.persistence.mongo.MongoDatabaseOp.DBName
import com.typesafe.config.{Config, ConfigFactory}
import reactivemongo.api.{AsyncDriver, Cursor, DefaultDB}
import reactivemongo.api.bson.{BSONArray, BSONDocument, BSONDocumentReader, BSONDocumentWriter, BSONValue}
import reactivemongo.api.bson.collection.BSONCollection

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
trait MongoRepo extends MongoOp with MongoQueryHelper

object Mongo {
  private val driver  = AsyncDriver()
  val connection      = driver.connect(s"mongodb://${MongoConfig.instance.host}:${MongoConfig.instance.port}")
}

trait MongoDatabaseOp {
  val dbName: DBName
  def query(collection: String)(implicit ec: ExecutionContext): Future[BSONCollection]
  def database(implicit ec: ExecutionContext):Future[DefaultDB]
}

object MongoDatabaseOp {
  type DBName = String
}

trait MongoCollectionOp {
  val collection: BSONCollection
  def readOne[A](query: BSONDocument)(implicit r: BSONDocumentReader[A], ec: ExecutionContext): Future[Option[A]]
  def readMany[A](query: BSONDocument, max: Int)(implicit r: BSONDocumentReader[A], ec: ExecutionContext): Future[List[A]]
}

trait MongoCollectionOpM[F[_]] {
  val collection: F[BSONCollection]
  def readOne[A](query: BSONDocument)(implicit r: BSONDocumentReader[A], ec: ExecutionContext): F[Option[A]]
  def readMany[A](query: BSONDocument, max: Int)(implicit r: BSONDocumentReader[A], ec: ExecutionContext): F[List[A]]
  def saveWhere[A](entity: A, query: BSONDocument)(implicit w: BSONDocumentWriter[A], ec: ExecutionContext): F[Either[String, A]]
}

trait MongoOp {

  implicit def toMongoCollOp(value: BSONCollection): MongoCollectionOp = new MongoCollectionOp {
    override val collection: BSONCollection = value
    override def readOne[A](query: BSONDocument)(implicit r: BSONDocumentReader[A], ec: ExecutionContext): Future[Option[A]] = collection.find[BSONDocument, BSONDocument](query, None).one[A]
    override def readMany[A](query: BSONDocument, max: Int = -1)(implicit r: BSONDocumentReader[A], ec: ExecutionContext): Future[List[A]] = collection.find[BSONDocument, BSONDocument](query, None).cursor[A]().collect(max, Cursor.FailOnError[List[A]]())
  }

  implicit def toMongoCollOpF(value: Future[BSONCollection]): MongoCollectionOpM[Future] = new MongoCollectionOpM[Future] {
    override val collection: Future[BSONCollection] = value

    override def readOne[A](query: BSONDocument)(implicit r: BSONDocumentReader[A], ec: ExecutionContext): Future[Option[A]] = {
      collection.flatMap(_.find[BSONDocument, BSONDocument](query, None).one[A])
    }

    override def readMany[A](query: BSONDocument, max: Int = -1)(implicit r: BSONDocumentReader[A], ec: ExecutionContext): Future[List[A]] = {
      collection.flatMap(_.find[BSONDocument, BSONDocument](query, None).cursor[A]().collect(max, Cursor.FailOnError[List[A]]()))
    }

    override def saveWhere[A](entity: A, query: BSONDocument)(implicit w: BSONDocumentWriter[A], ec: ExecutionContext): Future[Either[String, A]] = {
      collection.flatMap(_.update(false).one(query, entity, true, false)).map(wr => wr.ok match {
        case true   => Right(entity)
        case false  => Left(s"Error persisting entity: ${wr.writeErrors.mkString(",")}")
      })
      //      collection.flatMap(_.update(query, entity, upsert = true).map(wr => wr.ok match {
      //        case true   => Right(entity)
      //        case false  => Left(s"Error persisting entity: ${wr.writeErrors.mkString(",")}")
      //      })
    }
  }

  implicit def toMongoDBOp(value: DBName): MongoDatabaseOp = new MongoDatabaseOp {
    override val dbName: DBName = value
    override def query(collection: String)(implicit ec: ExecutionContext): Future[BSONCollection] = Mongo.connection.flatMap(_.database(dbName).map(_.collection(collection)))
    override def database(implicit ec: ExecutionContext): Future[DefaultDB] = Mongo.connection.flatMap(_.database(dbName))
  }
}

object MongoOp extends MongoOp

case class MongoQueryBuilder(doc: BSONDocument)
object MongoQueryBuilder {
  implicit def toBSON(builder: MongoQueryBuilder): BSONDocument = builder.doc

  def apply[A <: BSONValue](key: String, value: A): MongoQueryBuilder = MongoQueryBuilder(BSONDocument(key -> value))
  def apply(key: String, value: String): MongoQueryBuilder = MongoQueryBuilder(BSONDocument(key -> value))
}

trait MongoQueryHelper {

  // Simple idiom to allow easier reading of building queries
  def where(s: String): String = identity(s)

  implicit class QueryBuilderOp(builder: MongoQueryBuilder) {
    def and(b: BSONDocument): MongoQueryBuilder = &&(b)
    def &&(b: BSONDocument): MongoQueryBuilder = {
      builder.doc.getAsOpt[BSONArray]("$and") match {
        case Some(b0) => MongoQueryBuilder((builder.doc -- "$and") ++ BSONDocument("$and" -> (b0 ++ b)))
        case None     => MongoQueryBuilder("$and", BSONArray(builder.doc, b))
      }
    }

    def or(b: BSONDocument): MongoQueryBuilder = ||(b)
    def ||(b: BSONDocument): MongoQueryBuilder = {
      builder.doc.getAsOpt[BSONArray]("$or") match {
        case Some(b0) => MongoQueryBuilder((builder.doc -- "$or") ++ BSONDocument("$or" -> (b0 ++ b)))
        case None     => MongoQueryBuilder(BSONDocument("$or" -> BSONArray(builder.doc, b)))
      }
    }
  }

  implicit class BSONOp(root: BSONDocument) {
    def and(b: BSONDocument): MongoQueryBuilder = &&(b)
    def &&(b: BSONDocument): MongoQueryBuilder = {
      root.getAsOpt[BSONArray]("$and") match {
        case Some(b0) => MongoQueryBuilder((root -- "$and") ++ BSONDocument("$and" -> (b0 ++ b)))
        case None     => MongoQueryBuilder("$and", BSONArray(root, b))
      }
    }

    def or(b: BSONDocument): MongoQueryBuilder = ||(b)
    def ||(b: BSONDocument): MongoQueryBuilder = {
      root.getAsOpt[BSONArray]("$or") match {
        case Some(b0) => MongoQueryBuilder((root -- "$or") ++ BSONDocument("$or" -> (b0 ++ b)))
        case None     => MongoQueryBuilder("$or", BSONArray(root, b))
      }
    }
  }

  implicit class StringOp(key: String) {
    def >(value: Long)      = MongoQueryBuilder(key, BSONDocument("$gt" -> value))
    def >=(value: Long)     = MongoQueryBuilder(key, BSONDocument("$gte" -> value))
    def ===(value: String)  = MongoQueryBuilder(key, BSONDocument("$type" -> value))
    def |=|(value: String)  = MongoQueryBuilder(key, value)
    def is(value: String)   = MongoQueryBuilder(key, value)
  }
}

case class MongoConfig(host: String, port: Int)

object MongoConfig extends ConfigHelper {

  // Singleton app configuration instance
  lazy val instance = MongoConfig(ConfigFactory.load)

  def apply(c: Config): MongoConfig = {
    val h = c.getOrThrow[String]("db.mongo.host")
    val p = c.getOrThrow[Int]("db.mongo.port")
    MongoConfig(h, p)
  }
}
