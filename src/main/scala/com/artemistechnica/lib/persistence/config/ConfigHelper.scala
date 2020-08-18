package com.artemistechnica.lib.persistence.config

import com.typesafe.config.Config

trait ConfigHelper {

  import com.artemistechnica.lib.persistence.common.OptionOp._

  // TODO - Move to trait
  implicit class ConfigOp(c: Config) {
    def getOptional[A](key: String)(implicit cv: ConfigValue[A]): Option[A] = {
      if (c.getIsNull(key)) None else cv.get(c, key).some
    }

    def getOrThrow[A](key: String)(implicit cv: ConfigValue[A]): A = {
      if (c.getIsNull(key)) throw new IllegalArgumentException(s"Configuration for $key not found!")
      else cv.get(c, key)
    }
  }

  implicit val stringConfigValue: ConfigValue[String] = new ConfigValue[String] {
    override def get(c: Config, key: String): String = c.getString(key)
  }
  implicit val intConfigValue: ConfigValue[Int] = new ConfigValue[Int] {
    override def get(c: Config, key: String): Int = c.getInt(key)
  }
}

sealed trait ConfigValue[A] {
  def get(c: Config, key: String): A
}
