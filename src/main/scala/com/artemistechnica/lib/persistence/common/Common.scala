package com.artemistechnica.lib.persistence.common

import cats.data.EitherT

object CommonResponse {
  type RepoResponse[F[_], E <: RepoError, A] = EitherT[F, E, A]
}

