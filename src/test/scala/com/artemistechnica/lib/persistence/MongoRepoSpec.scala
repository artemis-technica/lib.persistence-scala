package com.artemistechnica.lib.persistence

import akka.stream.scaladsl.Sink
import akka.stream.{ActorMaterializer, Materializer}
import com.artemistechnica.lib.persistence.common.{StreamError, WriteError}
import com.artemistechnica.lib.persistence.mongo.MongoRepo.MongoResponse
import com.artemistechnica.lib.persistence.mongo.{Mongo, MongoResponseGen}
import fixtures.{MongoDB, Profile}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import reactivemongo.api.bson.BSONDocument

import scala.concurrent.ExecutionContext.Implicits.global

class MongoRepoSpec extends FlatSpec with Matchers with ScalaFutures with BeforeAndAfterAll with MongoResponseGen {

  import cats.implicits.catsStdInstancesForFuture

  // Global future timeout
  implicit override val patienceConfig = PatienceConfig(timeout = Span(2, Seconds), interval = Span(20, Millis))
  // Akka stream materializer
  // TODO - upgrade to 2.6.x
  implicit val system = akka.actor.ActorSystem("TheSystem")
  implicit val mat: Materializer = ActorMaterializer()


  override protected def beforeAll(): Unit = {
    whenReady(Mongo.db.map(_.drop()).value)(_ => ())
  }

  override protected def afterAll(): Unit = {
    whenReady(Mongo.db.map(_.drop()).value)(_ => ())
  }

  "MongoRepo" should "persist a single profile" in {
    val profile = Profile()
    val query   = BSONDocument("_id" -> profile._id)
    val result  = for {
      wr  <- MongoDB.insert("profile", profile)
      p   <- MongoDB.readOne[Profile]("profile")(query)
    } yield (wr, p)

    whenReady(result.value)(_ match {
      case Left(e)  => assert(false, s"Test failed: ${e.message}")
      case Right((wr, optProfile)) => {
        assert(wr.ok)
        assert(optProfile.isDefined)
        assert(optProfile.get._id === profile._id)
      }
    })
  }

  "MongoRepo" should "update only a profile's updateDate" in {
    val profile     = Profile()
    val query       = BSONDocument("_id" -> profile._id)
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
        assert(optP0.get._id === profile._id)
        assert(optP0.get.updateDate == profile.updateDate)
        assert(uwr.ok)
        assert(uwr.nModified == 1)
        assert(optP1.isDefined)
        assert(optP1.get._id === profile._id)
        assert(optP1.get.updateDate == updateDate)
      }
    })
  }

  "MongoRepo" should "delete one profile as expected" in {
    val profile0  = Profile()
    val profile1  = Profile()
    val query0    = BSONDocument("_id" -> profile0._id)
    val query1    = BSONDocument("_id" -> profile1._id)
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
        assert(optP0.get._id === profile0._id)
        assert(wr.ok)
        assert(wr.n == 1)
        assert(optP1.isEmpty)
        assert(optP2.isDefined)
        assert(optP2.get._id === profile1._id)
      }
    })
  }

  "MongoRepo" should "delete many profiles as expected" in {
    val profile0  = Profile("inactive")
    val profile1  = Profile("inactive")
    val query0    = BSONDocument("_id" -> profile0._id)
    val query1    = BSONDocument("_id" -> profile1._id)
    val delQuery  = BSONDocument("status" -> "inactive")
    val result    = for {
      bwr <- MongoDB.batchInsert("profile", List(profile0, profile1))
      p0  <- MongoDB.readOne[Profile]("profile")(query0)
      p1  <- MongoDB.readOne[Profile]("profile")(query1)
      wr  <- MongoDB.deleteMany("profile")(delQuery)
      p2  <- MongoDB.readOne[Profile]("profile")(query0)
      p3  <- MongoDB.readOne[Profile]("profile")(query1)
    } yield (bwr, p0, p1, wr, p2, p3)

    whenReady(result.value)(_ match {
      case Left(e)  => assert(false, s"Test failed: ${e.message}")
      case Right((bwr, optP0, optP1, wr, optP2, optP3)) => {
        assert(bwr.ok)
        assert(bwr.totalN == 2)
        assert(optP0.isDefined)
        assert(optP0.get._id === profile0._id)
        assert(optP1.isDefined)
        assert(optP1.get._id === profile1._id)
        assert(wr.ok)
        assert(wr.n == 2)
        assert(optP2.isEmpty)
        assert(optP3.isEmpty)
      }
    })
  }

  "MongoRepo" should "fail inserting the same document twice" in {
    val profile = Profile()
    val result = for {
      wr0 <- MongoDB.insert("profile", profile)
      wr1 <- MongoDB.insert("profile", profile)
    } yield (wr0, wr1)

    whenReady(result.value)(_ match {
      case Left(e)  => {
        // Assert duplicate insert results in a WriteError
        assert(e.code == WriteError.code)
        // Should contain duplicate error code
        assert(e.message.contains("E11000"))
      }
      case Right((_, _))  => assert(false, "Test failed: unexpected outcome. Able to write duplicate documents to collection")
    })
  }

  "MongoRepo" should "stream documents" in {
    val profiles = (1 to 25).map(_ => Profile())

    val result3: MongoResponse[Seq[Profile]] = for {
      src <- MongoDB.stream[Profile] ("profile")(BSONDocument.empty)
      res <- (src.runWith(Sink.seq), StreamError)
    } yield res


    val results = for {
      _           <- MongoDB.deleteMany("profile")(BSONDocument.empty) // Clearing the collection
      _           <- MongoDB.batchInsert("profile", profiles)
      stream0     <- MongoDB.stream[Profile]("profile")(BSONDocument.empty)
      collection0 <- (stream0.runWith(Sink.seq), StreamError) // Implicitly transformed to a MongoResponse[Seq[Profile]]
      _           <- MongoDB.deleteManyDistinct("profile")(profiles.map(p => BSONDocument("_id" -> p._id)))
      stream1     <- MongoDB.stream[Profile]("profile")(BSONDocument.empty)
      collection1 <- (stream1.runWith(Sink.seq), StreamError) // Implicitly transformed to a MongoResponse[Seq[Profile]]
    } yield (collection0, collection1)

    whenReady(results.value)(_ match {
      case Left(e)  => assert(false, s"Test failed: Unexpected stream error. ${e.message}")
      case Right((psx0, psx1)) => {
        assert(psx0.length == profiles.length)
        assert(psx0.distinctBy(_._id).length == profiles.length)
        assert(psx1.isEmpty)
      }
    })

  }
}
