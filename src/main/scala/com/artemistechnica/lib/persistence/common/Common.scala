package com.artemistechnica.lib.persistence.common


import java.sql.Timestamp

import cats.data.EitherT
import play.api.libs.json.{Format, JsValue, Json}
import reactivemongo.api.bson.{BSONHandler, BSONLong, BSONValue}

import scala.util.Try

/**
 * Common response across all databases
 */
object CommonResponse {
  type RepoResponse[F[_], E <: RepoError, A] = EitherT[F, E, A]
}

/**
 * Import when needing to work with json or bson representations of a [[java.sql.Timestamp]]
 */
object TimestampImplicits {
//  implicit val json = Json.format[Timestamp]

  implicit val bson: BSONHandler[Timestamp] = new BSONHandler[Timestamp] {
    override def writeTry(t: Timestamp): Try[BSONValue] = Try(BSONLong(t.getTime))
    override def readTry(bson: BSONValue): Try[Timestamp] = {
      for {
        t0 <- bson.asTry[Long]
        t1 <- Try(new Timestamp(t0))
      } yield t1
    }
  }
}

/**
 * Helper trait for inflating some JsValue to some A summoning the necessary formatter for A.
 */
trait JsonToA {
  def jsonToA[A](json: JsValue)(implicit f: Format[A]): Option[A] = Json.fromJson(json).asOpt
  def optJsonToA[A](optJson: Option[JsValue])(implicit f: Format[A]): Option[A] = optJson.flatMap(Json.fromJson(_).asOpt)
}