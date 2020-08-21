package com.artemistechnica.lib.persistence

import com.artemistechnica.lib.persistence.mongo.Mongo
import fixtures.{MongoDB, Profile}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import reactivemongo.api.bson.BSONDocument

import scala.concurrent.ExecutionContext.Implicits.global

class MongoRepoSpec extends FlatSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  import cats.implicits.catsStdInstancesForFuture

  // Global future timeout
  implicit override val patienceConfig = PatienceConfig(timeout = Span(2, Seconds), interval = Span(20, Millis))

  override protected def beforeAll(): Unit = {
    whenReady(Mongo.db.map(_.drop()).value)(_ => println("Mongo database dropped"))
  }

  override protected def afterAll(): Unit = {
    whenReady(Mongo.db.map(_.drop()).value)(_ => println("Mongo database dropped"))
  }

  "Profile" should "persist" in {
    val profile = Profile()
    val query   = BSONDocument("id" -> profile.id)
    val result  = for {
      wr  <- MongoDB.insert("profile", profile)
      p   <- MongoDB.readOne[Profile]("profile")(query)
    } yield (wr, p)

    whenReady(result.value)(_ match {
      case Left(e)  => assert(false, s"Test failed: ${e.message}")
      case Right((wr, optProfile)) => {
        assert(wr.ok)
        assert(optProfile.isDefined)
        assert(optProfile.get.id === profile.id)
      }
    })
  }

  "Profile" should "update only its updateDate" in {
    val profile     = Profile()
    val query       = BSONDocument("id" -> profile.id)
    val updateDate  = 1583884800000L
    val update      = BSONDocument("$set" -> BSONDocument("updateDate" -> updateDate))
    val result      = for {
      wr0 <- MongoDB.insert("profile", profile)
      p0  <- MongoDB.readOne[Profile]("profile")(query)
      uwr <- MongoDB.updateOne("profile")(query, update)
      p1  <- MongoDB.readOne[Profile]("profile")(query)
    } yield (wr0, p0, uwr, p1)

    whenReady(result.value)(_ match {
      case Left(e)  => assert(false, s"Test failed: ${e.message}")
      case Right((wr0, optP0, uwr, optP1)) => {
        assert(wr0.ok)
        assert(optP0.isDefined)
        assert(optP0.get.id === profile.id)
        assert(optP0.get.updateDate == profile.updateDate)
        assert(uwr.ok)
        assert(uwr.nModified == 1)
        assert(optP1.isDefined)
        assert(optP1.get.id === profile.id)
        assert(optP1.get.updateDate == updateDate)
      }
    })
  }

  "Profile" should "delete as expected" in {

  }
}
