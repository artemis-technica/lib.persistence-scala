package com.artemistechnica.lib.persistence.common

sealed trait RepoError {
  def message: String
  def code: Int
}

case class PostgresError(message: String, code: Int) extends RepoError
case class MongoError(message: String, code: Int) extends RepoError

object ErrorCode {
  val readError     = 10
  val writeError    = 20
  val generalError  = 30
  val unknownError  = 0
}

case object ReadError extends RepoError {
  override def message: String = "Read error"
  override def code: Int = ErrorCode.readError
}

case object WriteError extends RepoError {
  override def message: String = "Write error"
  override def code: Int = ErrorCode.writeError
}

case object GeneralError extends RepoError {
  override def message: String = "General error"
  override def code: Int = ErrorCode.generalError
}