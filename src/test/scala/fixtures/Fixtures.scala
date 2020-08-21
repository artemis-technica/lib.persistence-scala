package fixtures

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

import play.api.libs.json.{Json, OFormat}
import reactivemongo.api.bson.{BSONDocumentHandler, Macros}

case class User(id: String, createDate: Timestamp, updateDate: Timestamp, firstName: String, lastName: String)
object UserFixture {
  def generateUser() = {
    val id = UUID.randomUUID.toString
    val now = Timestamp.from(Instant.now())
    User(id, now, now, s"FIRST_NAME_$id", s"LAST_NAME_$id")
  }
}
object UserImplicits { }

case class Book(isbn: String, title: String, author: String, publishDate: Timestamp)
object BookImplicits { }

case class Profile(id: String, createDate: Long, updateDate: Long)
object Profile {
  implicit val json: OFormat[Profile] = Json.format[Profile]
  implicit val bson: BSONDocumentHandler[Profile] = Macros.handler[Profile]
  def apply(): Profile = {
    val now = System.currentTimeMillis
    Profile(UUID.randomUUID.toString, now, now)
  }
}