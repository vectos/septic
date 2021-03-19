package septic

import cats.{Foldable, Functor, FunctorFilter, Monoid}
import cats.implicits._
import monocle.Lens

trait SepticSyntax {

  implicit class RichSeptic[F[_], D, A](val septic: Septic[D, F[A]]) {
    def headOption(implicit F: Foldable[F]): Septic[D, F[A]] =
      Septic(septic.db.map(_.get(0)))

    def filter(f: A => Boolean)(implicit F: FunctorFilter[F]): Septic[D, F[A]] =
      Septic(septic.db.map(_.filter(f)))

    def select[B](f: A => B)(implicit F: Functor[F]): Septic[D, F[B]] =
      Septic(septic.db.map(_.map(f)))

    def collect[B](f: PartialFunction[A, B])(implicit F: FunctorFilter[F]): Septic[D, F[B]] =
      Septic(septic.db.map(_.collect(f)))

    def size(implicit F: Foldable[F]): Septic[D, Long] =
      Septic(septic.db.map(_.size))

    def reduced(implicit M: Monoid[A], F: Foldable[F]): Septic[D, A] =
      Septic(septic.db.map(_.foldLeft(Monoid[A].empty)(Monoid[A].combine)))

    def leftJoin[B, C](other: Lens[D, List[B]])(join: (A, B) => Boolean)(implicit F: Foldable[F]): Septic[D, List[(A, Option[B])]] =
      Septic {
        for {
          x <- septic.db
          y <- Septic.all(other).db
        } yield x.toList.map(a => (a, y.find(b => join(a, b))))
      }

    def rightJoin[B, C](other: Lens[D, List[B]])(join: (A, B) => Boolean)(implicit F: Foldable[F]): Septic[D, List[(Option[A], B)]] =
      Septic {
        for {
          x <- septic.db
          y <- Septic.all(other).db
        } yield y.map(b => (x.find(a => join(a, b)), b))
      }

    def innerJoin[B, C](other: Lens[D, List[B]])(join: (A, B) => Boolean)(implicit F: Foldable[F]): Septic[D, List[(A, B)]] = {
      Septic {
        for {
          x <- septic.db
          y <- Septic.all(other).db
        } yield x.toList.flatMap(l => y.filter(r => join(l, r)).map(r => (l, r)))
      }

    }
  }

}
