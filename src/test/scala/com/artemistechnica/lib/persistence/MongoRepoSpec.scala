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

  "MongoRepo" should "persist a single profile" in {
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

  "MongoRepo" should "update only a profile's updateDate" in {
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

  "MongoRepo" should "delete one profile as expected" in {
    val profile0  = Profile()
    val profile1  = Profile()
    val query0    = BSONDocument("id" -> profile0.id)
    val query1    = BSONDocument("id" -> profile1.id)
    val result    = for {
      bwr <- MongoDB.batchInsert("profile", List(profile0, profile1))
      p0  <- MongoDB.readOne[Profile]("profile")(query0)
      wr  <- MongoDB.deleteOne("profile")(query0)
      p1  <- MongoDB.readOne[Profile]("profile")(query0)
      p2  <- MongoDB.readOne[Profile]("profile")(query1)
    } yield (bwr, p0, wr, p1, p2)

    whenReady(result.value)(_ match {
      case Left(e)  => assert(false, s"Test failed: ${e.message}")
      case Right((bwr, optP0, wr, optP1, optP2)) => {
        assert(bwr.ok)
        assert(bwr.totalN == 2)
        assert(optP0.isDefined)
        assert(optP0.get.id === profile0.id)
        assert(wr.ok)
        assert(wr.n == 1)
        assert(optP1.isEmpty)
        assert(optP2.isDefined)
        assert(optP2.get.id === profile1.id)
      }
    })
  }
}
