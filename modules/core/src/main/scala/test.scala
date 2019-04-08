package scalaz

package schema

import monocle.Iso

/*
final case class Person(name: String, role: Option[Role])
sealed trait Role
final case class User(active: Boolean, boss: Person) extends Role
final case class Admin(rights: List[String])         extends Role
 */

final case class Foo(s: String, b: Boolean, i: BigDecimal)

trait TestModule extends JsonModule[JsonSchema.type] {
  val R = JsonSchema

  /*type PersonTuple = (Seq[Char], Option[Role])

  val user = record(
    "active" -*>: prim(JsonSchema.JsonBool) :*: "boss" -*>: self[Person](person),
    Iso[(Boolean, Person), User]((User.apply _).tupled)(u => (u.active, u.boss))
  )*/

  /*  val admin = record(
    "rights" -*>: seq(prim(JsonSchema.JsonString)),
    Iso[List[String], Admin](Admin.apply)(_.rights)
  )*/

  val foo: Schema[
    RecordR[
      (
        scalaz.schema.FieldR[R.Prim[String]],
        (
          scalaz.schema.FieldR[R.Prim[Boolean]],
          scalaz.schema.FieldR[R.Prim[BigDecimal]]
        )
      ),
      scalaz.schema.Foo
    ],
    scalaz.schema.Foo
  ] = record(
    "s" -*>: prim(JsonSchema.JsonString) :*:
      "b" -*>: prim(JsonSchema.JsonBool) :*:
      "i" -*>: prim(JsonSchema.JsonNumber),
    Iso[(String, (Boolean, BigDecimal)), Foo](
      tpl => Foo(tpl._1, tpl._2._1, tpl._2._2)
    )(
      x => (x.s, (x.b, x.i))
    )
  )

  class Deriver[Repr, A](schema: Schema[Repr, A]) {
    def deriveTo[G[_, _]](implicit derivation: Derivation[Repr, A, G]) = derivation.derive(schema)
  }

  trait LowPrio {
    implicit def primDerivation[A] = new Derivation.PrimStep[Schema, A, JsonSchema.JsonPrim[A], A](
      primGadt => prim(primGadt)
    )
    implicit def fieldDerivation[XR, X] =
      new Derivation.FieldStep[Schema, XR, X, FieldR[XR], X]({
        case (id, schema) => (id -*>: schema).toSchema
      })
    implicit def productDerivation[XR, X, YR, Y] =
      new Derivation.ProdStep[Schema, XR, X, YR, Y, (XR, YR), (X, Y)]({
        case (l, r) => l :*: r
      })
    implicit def recordDerivation[XR, XP, X] =
      new Derivation.RecordStep[Schema, XP, X, XR, XP, RecordR[XR, X], X]({
          case (iso, schema) =>
            recursion
              .FixR[RecordR[XR, X]](
                new RecordF[BareSchema, X, XP, JsonSchema.JsonPrim, String, String](
                  schema.toFix,
                  iso
                ) {}
              )
        }
      )
  }

  object Derivations extends LowPrio {
    implicit def dropString[YR, Y] =
      new Derivation.ProdStep[
        Schema,
        FieldR[JsonSchema.JsonPrim[String]],
        String,
        YR,
        Y,
        YR,
        Y
      ]({
        case (_, r) => r
      })

    implicit def reWrapProductWithDefault =
      new Derivation.RecordStep[
        Schema,
        (String, (Boolean, BigDecimal)),
        Foo,
        (FieldR[R.Prim[Boolean]], FieldR[R.Prim[BigDecimal]]),
        (Boolean, BigDecimal),
        RecordR[(JsonSchema.JsonPrim[Boolean], JsonSchema.JsonPrim[BigDecimal]), Foo],
        Foo
      ]({
        case (_, schema) =>
          recursion.FixR[
            RecordR[(JsonSchema.JsonPrim[Boolean], JsonSchema.JsonPrim[BigDecimal]), Foo]
          ](
            new RecordF[
              BareSchema,
              Foo,
              (Boolean, BigDecimal),
              JsonSchema.JsonPrim,
              String,
              String
            ](
              schema.toFix,
              Iso[(Boolean, BigDecimal), Foo](
                tpl => Foo("defaultValue", tpl._1, tpl._2)
              )(
                x => (x.b, x.i)
              )
            ) {}
          )
      })
  }

  import Derivations._

  val newFoo = new Deriver(
    foo
  ).deriveTo[Schema]

  implicit val primToEncoderNT = new (JsonSchema.JsonPrim ~> Json.Encoder) {

    def apply[A](fa: JsonSchema.JsonPrim[A]): Json.Encoder[A] = { a =>
      fa match {
        case JsonSchema.JsonNumber => a.toString
        case JsonSchema.JsonBool   => a.toString
        case JsonSchema.JsonString => s""""$a""""
        case JsonSchema.JsonNull   => "null"
      }
    }
  }

  val newEnc = newFoo.to[Json.Encoder]

  /*val role = union(
    "user" -+>: user :+:
      "admin" -+>: admin,
    Iso[User \/ Admin, Role] {
      case -\/(u) => u
      case \/-(a) => a
    } {
      case u @ User(_, _) => -\/(u)
      case a @ Admin(_)   => \/-(a)
    }
  )

  def person = record(
    "name" -*>: prim(JsonSchema.JsonString) :*:
      "role" -*>: optional(
      role
    ),
    Iso[(String, Option[Role]), Person]((Person.apply _).tupled)(p => (p.name, p.role))
  )*/

}
