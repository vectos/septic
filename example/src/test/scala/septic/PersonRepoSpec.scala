package septic

import cats.effect.IO
import cats.~>
import doobie.ConnectionIO

class PersonRepoSpec {

  val nt: ConnectionIO ~> IO = ???

  val harnass: Harnass[PersonRepository, IO, ConnectionIO, Universe] =
    new Harnass(Universe.zero, DoobiePersonRepository, SepticPersonRepository, nt)


  harnass.model.eval { x =>
    x.insertMany(???) *> x.deleteWhenOlderThen(30) *> x.listAll()
  }

}
