package fixtures

import java.sql.Timestamp

case class User(id: String, createDate: Timestamp, firstName: String, lastName: String)
object UserImplicits {
//  implicit val json = Json.format[User]
//  implicit val bson = Macros.handler[Book]
}

case class Book(isbn: String, title: String, author: String, publishDate: Timestamp)
object BookImplicits {
//  implicit val json = Json.format[Book]
//  implicit val bson = Macros.handler[Book]
}

