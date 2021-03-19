package septic

import cats.data.State
import cats.implicits._
import monocle.Lens

final case class Septic[D, A] private (db: State[D, A]) {
  def run(state: D): A = db.runA(state).value
}

object Septic {

  def all[D, A](at: Lens[D, List[A]]): Septic[D, List[A]] =
    Septic(State.get.map(at.get))

  def unit: Septic[Any, Unit] =
    Septic(State.pure(()))

  def succeed[A](value: A): Septic[Any, A] =
    Septic(State.pure(value))

  def insertMany[D, A](at: Lens[D, List[A]])(elements: List[A]): Septic[D, Long] =
    insertMany_(at)(elements).size

  def insertMany_[D, A](at: Lens[D, List[A]])(elements: List[A]): Septic[D, List[A]] =
    Septic(State.modify[D](s => at.modify(_ ++ elements)(s)) *> State.pure(elements))

  def insert[D, A](at: Lens[D, List[A]])(element: A): Septic[D, Long] =
    insertMany(at)(List(element))

  def update[D, A](at: Lens[D, List[A]])(filter: A => Boolean, update: A => A): Septic[D, Long] =
    update_(at)(filter, update).size

  def update_[D, A](at: Lens[D, List[A]])(filter: A => Boolean, update: A => A): Septic[D, List[A]] =
    Septic {
      for {
        elements <- State.get[D]
        (toUpdate, notToUpdate) = at.get(elements).partition(filter)
        _ <- State.modify[D](s => at.modify(_ => toUpdate.map(update) ++ notToUpdate)(s))
      } yield toUpdate
    }

  def delete[D, A](at: Lens[D, List[A]])(filter: A => Boolean): Septic[D, Long] =
    delete_(at)(filter).size

  def delete_[D, A](at: Lens[D, List[A]])(filter: A => Boolean): Septic[D, List[A]] =
    Septic {
      for {
        elements <- State.get[D]
        (toDelete, toKeep) = at.get(elements).partition(filter)
        _ <- State.modify[D](s => at.modify(_ => toKeep)(s))
      } yield toDelete
    }

  def truncate[D, A](at: Lens[D, List[A]]): Septic[D, Long] =
    Septic {
      for {
        elements <- State.get[D]
        _ <- State.modify[D](d => at.modify(_ => List.empty)(d))
      } yield at.get(elements).size
    }

  def upsert[D, A, B](at: Lens[D, List[A]])(conflict: A => B, update: A => A, item: A): Septic[D, Long] =
    upsertMany(at)(conflict, update, List(item))

  def upsertMany[D, A, B](at: Lens[D, List[A]])(conflict: A => B, update: A => A, items: List[A]): Septic[D, Long] =
    upsertMany_(at)(conflict, update, items).size

  def upsertMany_[D, A, B](at: Lens[D, List[A]])(conflict: A => B, update: A => A, items: List[A]): Septic[D, List[A]] =
    Septic {
      for {
        elements <- State.get[D]
        conflicting = items.map(conflict)
        (toUpdate, notToUpdate) = at.get(elements).partition(x => conflicting.contains(conflict(x)))
        updated = toUpdate.map(update)
        conflictedUpdated = toUpdate.map(conflict)
        toInsert = items.filterNot(x => conflictedUpdated.contains(conflict(x)))
        _ <- State.modify[D](s => at.modify(_ => updated ++ notToUpdate ++ toInsert)(s))
      } yield toInsert ++ updated
    }
  
}