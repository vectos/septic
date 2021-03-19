package septic

import monocle.macros.Lenses

@Lenses
final case class Universe(
  persons: List[Person]
)

object Universe {
  def zero: Universe = Universe(Nil)
}

object SepticPersonRepository extends PersonRepository[Septic[Universe, *]] {
  def insertMany(persons: List[Person]): Septic[Universe, Long] =
    Septic.insertMany(Universe.persons)(persons)

  def deleteWhenOlderThen(age: Long): Septic[Universe, Long] =
    Septic.delete(Universe.persons)(_.age > age)

  def listAll(): Septic[Universe, List[Person]] =
    Septic.all(Universe.persons)

  def create: Septic[Universe, Unit] =
    Septic.unit
}
