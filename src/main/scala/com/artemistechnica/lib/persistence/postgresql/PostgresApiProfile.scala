package com.artemistechnica.lib.persistence.postgresql

import com.github.tminglei.slickpg.{ExPostgresProfile, PgPlayJsonSupport, utils}
import play.api.libs.json.{JsNull, JsValue, Json}
import slick.basic.Capability
import slick.jdbc.{GetResult, JdbcCapabilities, PositionedResult, SetParameter}

/**
 * Custom Postgres api profile
 */
trait PostgresApiProfile extends ExPostgresProfile with PgPlayJsonSupport{
  // Defaults to Postgres' jsonb json type
  override def pgjson = "jsonb"
  // Defining the API
  override val api    = PostgresAPI
  // Add back 'insertOrUpdate' to enable native 'upsert' support for postgres 9.5+
  override protected def computeCapabilities: Set[Capability] = super.computeCapabilities + JdbcCapabilities.insertOrUpdate

  // API definition with play json support
  object PostgresAPI extends API with PlayJsonImplicits { }
}

/**
 * Primary import object
 *
 * e.g. import com.artemistechnica.lib.persistence.postgresql.PostgresApiProfile.api._
 */
object PostgresApiProfile extends PostgresApiProfile

/**
 * Adds native json support when working with postgres via raw SQL queries.
 */
trait PostgresJsValueImplicits {
  import utils.PlainSQLUtils._

  implicit class PgJsonPositionedResults(r: PositionedResult) {
    def nextJson(): JsValue = nextJsonOption().getOrElse(JsNull)
    def nextJsonOption(): Option[JsValue] = r.nextStringOption().map(Json.parse)
  }

  // Ability to read JsValues when performing raw SQL queries
  implicit val getJson: GetResult[JsValue] = mkGetResult(_.nextJson())
  implicit val getJsonOption: GetResult[Option[JsValue]] = mkGetResult(_.nextJsonOption())
  // Ability to write JSValues as jsonb when performing raw SQL queries
  implicit val setJson: SetParameter[JsValue] = mkSetParameter[JsValue]("jsonB", Json.stringify)
  implicit val setJsonOption: SetParameter[Option[JsValue]] = mkOptionSetParameter("jsonB", Json.stringify)
}

// Import object contents or mix in the trait
object PostgresJsValueImplicits extends PostgresJsValueImplicits