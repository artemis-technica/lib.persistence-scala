package fixtures

import com.artemistechnica.lib.persistence.common.SimpleConfigProvider
import com.artemistechnica.lib.persistence.mongo.MongoRepo.MongoResponse
import com.artemistechnica.lib.persistence.mongo.{Mongo, MongoRepo}

import scala.concurrent.ExecutionContext

// Just extend the MongoRepo trait
object MongoDB extends MongoRepo with SimpleConfigProvider {

  import cats.implicits.catsStdInstancesForFuture

  def drop(implicit ec: ExecutionContext): MongoResponse[Unit] = {
    for {
      db <- Mongo.db
    } yield db.drop()
  }
}
