package com.artemistechnica.lib.persistence.common

trait OptionOp[A] {
  val value: A
  def some: Option[A] = Some(value)
}

object OptionOp {
  implicit def toOptionOp[A](a: A): OptionOp[A] = new OptionOp[A] {
    override val value: A = a
  }
}
