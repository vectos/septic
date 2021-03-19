package septic

import cats.implicits._
import cats.data.{State, Tuple2K}
import cats.tagless.SemigroupalK
import cats.{Functor, ~>}


class Harnass[Alg[_[_]], F[_], Tx[_], D](initState: D, db: Alg[Tx], model: Alg[Septic[D, *]], tx: Tx ~> F) {

  type Eff[A] = Tuple2K[Tx, Septic[D, *], A]
  type Paired = Alg[Eff]

  trait Evaluator {
    def eval[A](f: Paired => Eff[A]): F[(A, A)]
  }

  def model(implicit S: SemigroupalK[Alg], F: Functor[F]): Evaluator = {
    val paired: Paired = S.productK(db, model)
    new Evaluator {
      override def eval[A](f: Paired => Eff[A]): F[(A, A)] = {
        //here we get the `Tuple2K` from `f`
        val effectTuple: Eff[A] = f(paired)
        //we run the connection against a rollback transactor, and get the result
        val dbValue: F[A] = tx(effectTuple.first)
        //we run the state monad and get the value
        val stateValue: A = effectTuple.second.run(initState)

        dbValue.map(_ -> stateValue)
      }
    }
  }
}
