package septic

import cats.effect.testing.specs2.CatsIO
import cats.effect.{Blocker, IO}
import cats.implicits._
import com.softwaremill.diffx.generic.auto._
import com.softwaremill.diffx.specs2.DiffMatcher
import doobie._
import doobie.free.connection
import doobie.util.transactor.Strategy
import doobie.implicits._
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification

import java.util.UUID

// -- Run `docker run --name some-postgres -p 5432:5432 -e POSTGRES_PASSWORD=mysecretpassword -d postgres`
class PersonRepoSpec extends Specification with ScalaCheck with CatsIO with DiffMatcher {

  // A transactor that gets connections from java.sql.DriverManager and executes blocking operations
  // on an our synchronous EC. See the chapter on connection handling for more info.
  val xa = Transactor.strategy.modify(Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",     // driver classname
    "jdbc:postgresql:postgres",     // connect URL (driver-specific)
    "postgres",                  // user
    "mysecretpassword",                          // password
    Blocker.liftExecutionContext(ExecutionContexts.synchronous) // just for testing
  ),
  _ => Strategy(before = connection.setAutoCommit(false), after = connection.unit, oops = connection.unit, always = connection.rollback *> connection.close)
)

  val harnass: Harnass[PersonRepository, IO, ConnectionIO, Universe] =
    new Harnass(Universe.zero, DoobiePersonRepository, SepticPersonRepository, xa.trans)

  val persons = List(Person(UUID.randomUUID(), "Mark", 1337), Person(UUID.randomUUID(), "Klaas", 3))


  "PersonRepository" should {
    "should delete people older then 30" in {
      harnass.model.eval { x =>
        x.create *>
        x.insertMany(persons) *>
        x.deleteWhenOlderThen(30) *>
        x.listAll()
      }
      .map { case (left, right) => left must matchTo(right) }
    }
  }



}
