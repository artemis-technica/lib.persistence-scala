package com.artemistechnica.lib.persistence.common


import cats.data.EitherT
import play.api.libs.json.{Format, JsValue, Json}

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
//  implicit val bson = Macros.handler[Timestamp]
}

/**
 * Helper trait for inflating some JsValue to some A summoning the necessary formatter for A.
 */
trait JsonToA {
  def jsonToA[A](json: JsValue)(implicit f: Format[A]): Option[A] = Json.fromJson(json).asOpt
  def optJsonToA[A](optJson: Option[JsValue])(implicit f: Format[A]): Option[A] = optJson.flatMap(Json.fromJson(_).asOpt)
}