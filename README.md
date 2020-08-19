# lib.persistence-scala
A scala persistence library.

###### Scala support:
* 2.12.x
* 2.13.x

###### Supported databases:
* MongoDB
* MySQL
* PostgreSQL

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


##### Quickstart

###### Installation
TODO
###### Components
TODO

##### MongoDB

###### Configuration
TODO

##### MySQL

###### Configuration
TODO

##### PostgreSQL

The generalized steps to interface with a PostgreSQL DB is:
1. Describe the tables available in the database using Slick.
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
```json
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