package com.artemistechnica.lib.persistence

import fixtures.{PostgresTestDB, User, UserFixture}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import cats.implicits.catsStdInstancesForFuture

import scala.concurrent.ExecutionContext.Implicits.global

class PostgresRepoSpec extends FlatSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  // The all important postgres api import
  import com.artemistechnica.lib.persistence.sql.postgresql.PostgresApiProfile.api._

  // Global future timeout
  implicit override val patienceConfig = PatienceConfig(timeout = Span(2, Seconds), interval = Span(20, Millis))

  val db: PostgresTestDB = PostgresTestDB()

  // Create the schemas before running tests
  override protected def beforeAll(): Unit = {
    whenReady(db.createSchemas.value)(_ match {
      case Left(e) => throw new Exception(e.message)
      case Right(_) => println("Found or created schemas")
    })
  }
  // Drop all schemas after running tests
  override protected def afterAll(): Unit = {
    whenReady(db.dropSchemas.value)(_ match {
      case Left(e) => throw new Exception(e.message)
      case Right(_) => println("Schemas dropped")
    })
  }

  "User" should "save" in {
    val user = UserFixture.generateUser()
    val resEF = for {
      r0 <- db.write(_.users += user)
      r1 <- db.read[Option[User]](_.users.filter(_.id === user.id).result.headOption)
    } yield (r0, r1)

    whenReady(resEF.value)( _ match {
      case Left(e) => {
        println(s"Error testing: ${e.message}")
        assert(false)
      }
      case Right((i, u))  => {
        println(s"$i with user ${u.get.id}")
        assert(i == 1)
        assert(u.isDefined)
        assert(u.get.id === user.id)
      }
    })
  }

}
