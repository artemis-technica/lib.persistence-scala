package fixtures

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

case class User(id: String, createDate: Timestamp, updateDate: Timestamp, firstName: String, lastName: String)
object UserFixture {
  def generateUser() = {
    val id = UUID.randomUUID.toString
    val now = Timestamp.from(Instant.now())
    User(id, now, now, s"FIRST_NAME_$id", s"LAST_NAME_$id")
  }
}
object UserImplicits {
//  implicit val json = Json.format[User]
//  implicit val bson = Macros.handler[Book]
}

case class Book(isbn: String, title: String, author: String, publishDate: Timestamp)
object BookImplicits {
//  implicit val json = Json.format[Book]
//  implicit val bson = Macros.handler[Book]
}

