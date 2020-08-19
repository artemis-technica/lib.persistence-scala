package fixtures

import java.sql.Timestamp

import com.artemistechnica.lib.persistence.sql.postgresql.PostgresRepo.PostgresResponse
import com.artemistechnica.lib.persistence.sql.postgresql.{PostgresRepo, PostgresTableList}
import slick.lifted.ProvenShape

import scala.concurrent.ExecutionContext

// The all important Postgres import
import com.artemistechnica.lib.persistence.sql.postgresql.PostgresApiProfile.api._

// Primary trait to interface with a concrete Postgres DB instance
trait PostgresTestDB extends PostgresRepo[PostgresTestDBTables] {
  override def tables: PostgresTestDBTables = PostgresTestDB.tables
  def createSchemas(implicit ec: ExecutionContext): PostgresResponse[Unit] = run(DBIO.seq(tables.asList.map(_.schema.createIfNotExists): _*))
  def dropSchemas(implicit ec: ExecutionContext): PostgresResponse[Unit] = run(DBIO.seq(tables.asList.map(_.schema.dropIfExists): _*))
}

// The postgres table list required for the api
case class PostgresTestDBTables(users: TableQuery[UserTable], books: TableQuery[BookTable]) extends PostgresTableList {
  def asList = List(users, books)
}

object PostgresTestDB {
  // Queryable tables for DB instance
  private lazy val users: TableQuery[UserTable] = TableQuery[UserTable]
  private lazy val books: TableQuery[BookTable] = TableQuery[BookTable]
  // Creating the SQL table list
  private lazy val tables: PostgresTestDBTables = PostgresTestDBTables(users, books)

  def apply(): PostgresTestDB = new PostgresTestDB { }
}

// All available table columns
object Column {
  val id          = "id"
  val firstName   = "first_name"
  val lastName    = "last_name"
  val createDate  = "create_date"
  val isbn        = "isbn"
  val title       = "title"
  val author      = "author"
  val publishDate = "publish_date"
}

// All available tables in test DB
object Table {
  val user = "user"
  val book = "book"
}

// Describing the user table
class UserTable(tag: Tag) extends Table[User](tag, Table.user) {
  def id:         Rep[String]     = column[String](Column.id, O.PrimaryKey)
  def createDate: Rep[Timestamp]  = column[Timestamp](Column.createDate)
  def firstName:  Rep[String]     = column[String](Column.firstName)
  def lastName:   Rep[String]     = column[String](Column.lastName)

  override def * : ProvenShape[User] = (id, createDate, firstName, lastName).mapTo[User]
}
// Describing the book table
class BookTable(tag: Tag) extends Table[Book](tag, Table.book) {
  def isbn:         Rep[String]     = column[String](Column.isbn, O.PrimaryKey)
  def title:        Rep[String]     = column[String](Column.title)
  def author:       Rep[String]     = column[String](Column.author)
  def publishDate:  Rep[Timestamp]  = column[Timestamp](Column.publishDate)

  override def * : ProvenShape[Book] = (isbn, title, author, publishDate).mapTo[Book]
}
