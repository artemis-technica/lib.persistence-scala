package com.artemistechnica.lib.persistence

import fixtures.{MongoDB, Profile}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import reactivemongo.api.bson.BSONDocument

import scala.concurrent.ExecutionContext.Implicits.global

class MongoRepoSpec extends FlatSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  // Global future timeout
  implicit override val patienceConfig = PatienceConfig(timeout = Span(2, Seconds), interval = Span(20, Millis))

  override protected def beforeAll(): Unit = {
    whenReady(MongoDB.database.map(db => db.drop()))(_ => println("Mongo database dropped"))
  }

  override protected def afterAll(): Unit = {
    whenReady(MongoDB.database.map(db => db.drop()))(_ => println("Mongo database dropped"))
  }

  "Profile" should "persist" in {
    val profile = Profile()
    val query   = BSONDocument("id" -> profile.id)
    val result  = for {
      db  <- MongoDB.collection("profile")
      wr  <- db.insert.one(profile)
      p   <- db.find[BSONDocument, BSONDocument](query, None).one[Profile]
    } yield (wr, p)

    whenReady(result) { case (wr, optProfile) => {
      assert(wr.ok)
      assert(optProfile.isDefined)
      assert(optProfile.get.id === profile.id)
    }}
  }

  "Profile" should "update only its updateDate" in {

  }

  "Profile" should "delete as expected" in {

  }
}
