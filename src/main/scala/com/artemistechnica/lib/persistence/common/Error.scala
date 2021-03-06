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
  val updateError   = 30
  val deleteError   = 40
  val streamError   = 50
  val generalError  = 60
  val databaseError = 70
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

case object UpdateError extends RepoError {
  override def message: String = "Update error"
  override def code: Int = ErrorCode.updateError
}

case object DeleteError extends RepoError {
  override def message: String = "Delete error"
  override def code: Int = ErrorCode.deleteError
}

case object GeneralError extends RepoError {
  override def message: String = "General error"
  override def code: Int = ErrorCode.generalError
}

case object DatabaseError extends RepoError {
  override def message: String = "Database error"
  override def code: Int = ErrorCode.databaseError
}

case object StreamError extends RepoError {
  override def message: String = "Database streaming error"
  override def code: Int = ErrorCode.streamError
}