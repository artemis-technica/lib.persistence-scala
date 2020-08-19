package com.artemistechnica.lib.persistence

import fixtures.PostgresTestDB
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global

class PostgresRepoSpec extends FlatSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  // import com.artemistechnica.lib.persistence.sql.postgresql.PostgresApiProfile.api._

  // Global future timeout
  implicit override val patienceConfig = PatienceConfig(timeout = Span(2, Seconds), interval = Span(20, Millis))

  val db: PostgresTestDB = PostgresTestDB()

  override protected def beforeAll(): Unit = {
    whenReady(db.createSchemas.value)(_ match {
      case Left(e) => {
        println(s"BAD ERROR - ${e.message}")
        throw new Exception(e.message)
      }
      case Right(_) => println("Found or created schemas")
    })
  }

  override protected def afterAll(): Unit = {
    whenReady(db.dropSchemas.value)(_ match {
      case Left(e) => {
        println(s"BAD ERROR - ${e.message}")
        throw new Exception(e.message)
      }
      case Right(_) => println("Schemas dropped")
    })
  }

  "User" should "save" in {

  }

}
