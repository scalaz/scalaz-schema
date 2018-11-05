package scalaz

package schema

import monocle.Iso
import testz._
import scalaz.Scalaz._

object SchemaModuleExamples {

  def tests[T](harness: Harness[T]): T = {
    import harness._
    import scalaz.schema.Json.module._
    import scalaz.schema.Json.module.Schema._

    section("Manipulating Schemas")(
      test("Building Schemas using the smart constructors") { () =>
        type PersonTuple = (Seq[Char], Option[Role])

        val personTupleSchema = iso[Person, PersonTuple](
          record[Person](
            ^(
              essentialField[Person, String](
                "name",
                prim(JsonSchema.JsonString),
                Person.name,
                None
              ),
              nonEssentialField[Person, Role](
                "role",
                union[Role](
                  branch(
                    "user",
                    record[User](
                      essentialField(
                        "active",
                        prim(JsonSchema.JsonBool),
                        Person.active,
                        None
                      ).map(User.apply)
                    ),
                    Person.user
                  ),
                  branch(
                    "admin",
                    record[Admin](
                      essentialField(
                        "rights",
                        seq(prim(JsonSchema.JsonString)),
                        Person.rights,
                        None
                      ).map(Admin.apply)
                    ),
                    Person.admin
                  )
                ),
                Person.role
              )
            )(Person.apply)
          ),
          Person.personToTupleIso
        )

        val expected =
          IsoSchema(
            RecordSchema[Person](
              ^(
                FreeAp.lift[Field[Person, ?], String](
                  Field.Essential(
                    "name",
                    PrimSchema(JsonSchema.JsonString),
                    Person.name,
                    None
                  )
                ),
                FreeAp.lift[Field[Person, ?], Option[Role]](
                  Field.NonEssential(
                    "role",
                    Union(
                      NonEmptyList.nels[Branch[Role, _]](
                        Branch[Role, User](
                          "user",
                          RecordSchema[User](
                            FreeAp
                              .lift[Field[User, ?], Boolean](
                                Field.Essential(
                                  "active",
                                  PrimSchema(JsonSchema.JsonBool),
                                  Person.active,
                                  None
                                )
                              )
                              .map(User)
                          ),
                          Person.user
                        ),
                        Branch[Role, Admin](
                          "admin",
                          RecordSchema[Admin](
                            FreeAp
                              .lift[Field[Admin, ?], List[String]](
                                Field.Essential(
                                  "rights",
                                  SeqSchema(PrimSchema(JsonSchema.JsonString)),
                                  Person.rights,
                                  None
                                )
                              )
                              .map(Admin)
                          ),
                          Person.admin
                        )
                      )
                    ),
                    Person.role
                  )
                )
              )(Person.apply)
            ),
            Person.personToTupleIso
          )

        assert(personTupleSchema == expected)
      },
      test("imap on IsoSchema shouldn't add new layer") { () =>
        val adminToListIso  = Iso[Admin, List[String]](_.rights)(Admin.apply)
        def listToSeqIso[A] = Iso[List[A], Seq[A]](_.toSeq)(_.toList)

        val adminSchema = record[Admin](
          essentialField(
            "rights",
            seq(prim(JsonSchema.JsonString)),
            Person.rights,
            None
          ).map(Admin.apply)
        )

        adminSchema.imap(adminToListIso).imap(listToSeqIso) match {
          case IsoSchema(base, _) => assert(base == adminSchema)
          case _                  => assert(false)
        }
      }
    )
  }
}
