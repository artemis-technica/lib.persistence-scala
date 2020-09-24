# lib.persistence-scala
A scala persistence library.

###### Scala support:
* 2.12.x
* 2.13.x

###### Supported databases:
* MongoDB
* MySQL
* PostgreSQL
* Redis

### Table of Contents
* Quickstart
  * Installation
  * Components
* MongoDB
  * Configuration
* MySQL
  * Configuration
* PostgreSQL
  * Configuration
* Redis


##### Quickstart

All repositories return a common `RepoResponse` response which is really a type alias for an `EitherT[F[_], E <: RepoError, A]`.

###### Installation
Add the following dependency to your `libraryDependencies` in `build.sbt`.

```scala
"com.artemistechnica"  %%  "lib-persistence-scala"  %  "0.0.1"
```

###### Components
TODO

##### MongoDB

MongoDB is fairly straight-forward to interface with.

1. Extends the `com.artemistechnica.lib.persistence.mongo.MongoRepo` trait and mix in a `com.artemistechnica.lib.persistence.common.ConfigProvider`. This gives basic functionality for interacting with Mongo database. For convenience there is a `SimpleConfigProvider` that implements the `ConfigProvider` trait and wraps a call to `ConfigFactory.load` to provide the configuration.
```scala
// Just extend the MongoRepo trait mixing in a ConfigProvider
object MongoDB extends MongoRepo with SimpleConfigProvider
// Simple example data
case class Profile(id: String, createDate: Long, updateDate: Long, status: String)
object Profile {
  // Implicit bson handler for reading and writing types to a Mongo database.
  implicit val bson: BSONDocumentHandler[Profile] = Macros.handler[Profile]
  def apply(): Profile = {
    val now = System.currentTimeMillis
    Profile(UUID.randomUUID.toString, now, now, "active")
  }
}

val collection = "profile"
val result0: MongoResponse[WriteResult] = MongoDB.insert(collection, profile)
val result1: MongoResponse[Option[Profile]] = MongoDB.readOne[Profile](collection)(BSONDocument("_id" -> profile.id))
val result2: MongoResponse[List[Profile]] = MongoDB.readMany[Profile](collection)(BSONDocument("status" -> "active"))

// Extends the trait or import the companion object's contents for implicit Tuple[T, ResponseError] to MongoResponse[T] transformations
import com.artemistechnica.lib.persistence.mongo.MongoResponseGen._
// Akka streams support
val result3: MongoResponse[Seq[Profile]] = for {
  src <- MongoDB.stream[Profile](collection)(BSONDocument.empty)
  res <- (src.runWith(Sink.seq), StreamError) // Implicitly transformed
} yield res
```

###### Configuration
```hocon
{
  db {
    mongo {
      host = "localhost"
      port = "27017"
      dbName = "testdb"
    }
  }
}
```

##### MySQL

MySQL and Postgres are both integrated in very similar fashions.

###### Configuration
TODO

##### PostgreSQL

The generalized steps to interface with a PostgreSQL DB are:
1. Describe the tables available in the database using Slick. Make sure to import the Postgres profile's api contents; this is typically the 'glue' to summon the right type aliases.
```scala
// The all important postgres api import
import com.artemistechnica.lib.persistence.sql.postgresql.PostgresApiProfile.api._
```
```scala
// Describing the user table
class UserTable(tag: Tag) extends Table[User](tag, Table.user) {
  def id:         Rep[String]     = column[String](Column.id, O.PrimaryKey)
  def createDate: Rep[Timestamp]  = column[Timestamp](Column.createDate)
  def updateDate: Rep[Timestamp]  = column[Timestamp](Column.updateDate)
  def firstName:  Rep[String]     = column[String](Column.firstName)
  def lastName:   Rep[String]     = column[String](Column.lastName)

  override def * : ProvenShape[User] = (id, createDate, updateDate, firstName, lastName).mapTo[User]
}
// Describing the book table
class BookTable(tag: Tag) extends Table[Book](tag, Table.book) {
  def isbn:         Rep[String]     = column[String](Column.isbn, O.PrimaryKey)
  def title:        Rep[String]     = column[String](Column.title)
  def author:       Rep[String]     = column[String](Column.author)
  def publishDate:  Rep[Timestamp]  = column[Timestamp](Column.publishDate)

  override def * : ProvenShape[Book] = (isbn, title, author, publishDate).mapTo[Book]
}
```
2. Create an object extending the ```PostgresTableList``` trait that contains the available tables.
```scala
class PostgresTestDBTables(users: TableQuery[UserTable], books: TableQuery[BookTable]) extends PostgresTableList
```
3. Finally either extend or mix-in the ```PostgresRepo``` trait explicitly typing its ```PostgresTableList``` and satisfying the tables contract.
```scala
// The all important Postgres import
import com.artemistechnica.lib.persistence.sql.postgresql.PostgresApiProfile.api._

// Primary trait to interface with a concrete Postgres DB instance
trait PostgresTestDB extends PostgresRepo[PostgresTestDBTables] {
  // Contract defined in PostgresRepo
  override def tables: PostgresTestDBTables = PostgresTestDB.tables
  // Additional functionality
  def createSchemas(implicit ec: ExecutionContext): PostgresResponse[Unit] = run(DBIO.seq(tables.asList.map(_.schema.createIfNotExists): _*))
  def dropSchemas(implicit ec: ExecutionContext): PostgresResponse[Unit] = run(DBIO.seq(tables.asList.map(_.schema.dropIfExists): _*))
}

object PostgresTestDB {
  // Queryable tables for DB instance
  private lazy val users: TableQuery[UserTable] = TableQuery[UserTable]
  private lazy val books: TableQuery[BookTable] = TableQuery[BookTable]
  // Creating the SQL table list. PostgresTestDB#tables will reference this
  private lazy val tables: PostgresTestDBTables = PostgresTestDBTables(users, books)

  def apply(): PostgresTestDB = new PostgresTestDB { }
}
```
4. Query your database
```scala
val user: User = UserFixture.generateUser()
val result0: PostgresResponse[Int] = db.write((t: PostgresTestDBTables) => t.users += user)
val result1: PostgresResponse[Option[User]] = db.read[Option[User]]((t: PostgresTestDBTables) => t.users.filter(_.id === user.id).result.headOption)
```


###### Configuration
```hocon
{
  db {
    postgres {
      connectionPool = "HikariCP"
      dataSourceClass ="org.postgresql.ds.PGSimpleDataSource"
      properties = {
        serverName = "localhost"
        portNumber = "5432"
        databaseName = "some_db_name"
        user = "some_user"
        password = "some_password"
      }
      numThreads = 1
      queueSize = 1000
    }
  }
}
```