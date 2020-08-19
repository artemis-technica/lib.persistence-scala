package fixtures

import com.artemistechnica.lib.persistence.mongo.MongoRepo
import reactivemongo.api.DefaultDB
import reactivemongo.api.bson.collection.BSONCollection

import scala.concurrent.{ExecutionContext, Future}

object MongoDB extends MongoRepo {
  private val db = "testdb"
  def database(implicit ec: ExecutionContext): Future[DefaultDB] = db.database
  def collection(collection: String)(implicit ec: ExecutionContext): Future[BSONCollection] = db.query(collection)
}
