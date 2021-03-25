# septic

> Can your axioms survice the septic tank?

Septic is a model based property based testing library for tagless final algebras.

## Rationale

Improve the situation of testing repository and API clients. 

Tagless final algebras can come in many forms, the scope of library is to offer utitilies to make testing of properties easier for repository and api tagless final algebras.

### Typical properties tested

Typical properties tested with repositories and API clients are:

- *Data loss* - Does insert & update / read yield symmetric results?
- *Locality* - Do update / delete / read specific things?
- *Idempotent* - Are insert / update / delete methods idempotent?
- *Behavior* - After a insert or update, does read method yield the right results?

Testing these properties can assert that the interactions with datastores and API's is correct.

### Testing service code

Service code, is code which uses these algebras to orchestrate a certain flow. Using real implementations can be useful in an E2E test, but to test if the service code is orchestating correctly can yield tremendous amounts of slow E2E test. To avoid that unit tests which either work with mocks or in-memory variants are favored.

The downside of mocks is that you give them certain input and _mock_ the output. What if this algebra would never return such an output with that input? Then your orchestration test is also _invalid_.

Due that reason I favor testing orchestrating logic with in-memory variants.

## How does it look like ?

Simple tagless final repository

```scala
final case class Person(id: UUID, name: String, age: Int)

trait PersonRepository[F[_]] {
  def create: F[Unit]
  def insertMany(persons: List[Person]): F[Long]
  def deleteWhenOlderThen(age: Long): F[Long]
  def listAll(): F[List[Person]]
}

object PersonRepository {
  implicit val functorK: FunctorK[PersonRepository] = Derive.functorK
  implicit val semigroupalK: SemigroupalK[PersonRepository] = Derive.semigroupalK
}
```

An in-memory implementation using `Septic`, an specialized `State` monad:

```scala
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
```

A doobie implementation

```scala
object DoobiePersonRepository extends PersonRepository[ConnectionIO] {

  object queries {
    def deleteWhenOlderThen(age: Long): Update0 =
      fr"delete from persons where age > $age".update

    def create =
      fr"""create table if not exists persons (
        |	id uuid primary key,
        |	name text not null,
        |	age numeric not null
        |)""".stripMargin.update

    def listAll: Query0[Person] =
      fr"select id, name, age from persons".query[Person]
  }

  def insertMany(persons: List[Person]): ConnectionIO[Long] =
    Update[Person]("insert into persons (id, name, age) values (?, ?, ?)").updateMany(persons).map(_.toLong)

  def deleteWhenOlderThen(age: Long): ConnectionIO[Long] =
    queries.deleteWhenOlderThen(age).run.map(_.toLong)

  def listAll(): ConnectionIO[List[Person]] =
    queries.listAll.to[List]

  def create: doobie.ConnectionIO[Unit] =
    queries.create.run.void
}
```

A simple spec, to verify that there is

- No data loss
- Deletes are specific to a certain age (locality)

```scala
class PersonRepoSpec extends Specification with DoobieSpec {

  def harnass: Harnass[PersonRepository, IO, ConnectionIO, Universe] =
    new Harnass(Universe.zero, DoobiePersonRepository, SepticPersonRepository, xa.trans)

  "PersonRepository" should {
    "should insert and read" in {
      prop { persons: List[Person] =>
        assertMirroring {
          harnass.model.eval { x =>
            x.create *>
              x.insertMany(persons) *>
              x.listAll()
          }
        }
      }
    }

    "should delete people older then" in {
      prop { (persons: List[Person], age: Int) =>
        assertMirroring {
          harnass.model.eval { x =>
            x.create *>
              x.insertMany(persons) *>
              x.deleteWhenOlderThen(age) *>
              x.listAll()
          }
        }
      }
    }
  }
}
```



