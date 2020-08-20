package com.artemistechnica.lib.persistence

import java.sql.Timestamp
import java.time.Instant

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

  "User" should "persist" in {
    val user = UserFixture.generateUser()
    val resEF = for {
      // Write the user to the DB
      r0 <- db.write(_.users += user)
      // Read back the user from the DB
      r1 <- db.read[Option[User]](_.users.filter(_.id === user.id).result.headOption)
    } yield (r0, r1)

    whenReady(resEF.value)( _ match {
      case Left(e) => assert(false, s"Error testing: ${e.message}")
      case Right((i, u))  => {
        // Affected only a single row
        assert(i == 1)
        // User is defined
        assert(u.isDefined)
        // Found user is the expected user
        assert(u.get.id === user.id)
      }
    })
  }

  "User" should "update only its updateDate" in {
    val user = UserFixture.generateUser()
    val resEF = for {
      // Write the user to the DB
      r0 <- db.write(_.users += user)
      // Read back the new user from the DB
      r1 <- db.read[Option[User]](_.users.filter(_.id === user.id).result.headOption)
      // Update the user's updateDate in the DB
      r2 <- db.write(_.users.filter(_.id === user.id).map(_.updateDate).update(Timestamp.from(Instant.now())))
      // Read back the updated user from the DB
      r3 <- db.read[Option[User]](_.users.filter(_.id === user.id).result.headOption)
    } yield (r0, r1, r2, r3)

    whenReady(resEF.value)(_ match {
      case Left(e) => assert(false, s"Test error: ${e.message}")
      case Right((i0, u0, i1, u1)) => {
        // Only a single row affected
        assert(i0 == 1)
        // User is defined
        assert(u0.isDefined)
        // Is the user we expect
        assert(u0.get.id === user.id)
        // Update affected only a single row
        assert(i1 == 1)
        // Updated user is defined
        assert(u1.isDefined)
        // Updated user is the original user
        assert(u1.get.id === user.id)
        // Updated user has had its updateDate value updated as expected
        assert(u1.get.updateDate.after(u0.get.updateDate))
      }
    })
  }

  "User" should "delete as expected" in {
    val user0 = UserFixture.generateUser()
    val user1 = UserFixture.generateUser()

    val resEF = for {
      // Write the users to the DB
      r0 <- db.write(_.users ++= List(user0, user1))
      // Read back the first user
      r1 <- db.read[Option[User]](_.users.filter(_.id === user0.id).result.headOption)
      // Delete the user by id
      r2 <- db.delete(_.users.filter(_.id === user0.id))
      // Read back the first user after deleting
      r3 <- db.read[Option[User]](_.users.filter(_.id === user0.id).result.headOption)
      // Read back the second user
      r4 <- db.read[Option[User]](_.users.filter(_.id === user1.id).result.headOption)
    } yield (r0, r1, r2, r3, r4)

    whenReady(resEF.value)( _ match {
      case Left(e)  => assert(false, s"Test error: ${e.message}")
      case Right((i0, u0, i1, u1, u2)) => {
        // Assert the batched write count is defined
        assert(i0.isDefined)
        // Assert the batched write count is the expected value
        assert(i0.get == 2)
        // Assert the first query for the first user returns a defined user
        assert(u0.isDefined)
        // Assert deleting the user returns the correct affected row count
        assert(i1 == 1)
        // Assert the second query for the first user returns an empty result
        assert(u1.isEmpty)
        // Assert querying for the second user returns a defined user
        assert(u2.isDefined)
        // Assert the defined user is the expected second user
        assert(u2.get.id === user1.id)
      }
    })
  }
}
