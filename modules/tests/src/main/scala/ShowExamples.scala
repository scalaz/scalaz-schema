package scalaz

package schema

package tests

import testz._
import generic._

object ShowExamples {

  import JsonSchema._

  val showModule = new TestModule with ShowModule[JsonSchema.type] {

    val primToShowNT = new (JsonSchema.Prim ~> Show) {

      def apply[A](fa: JsonSchema.Prim[A]): Show[A] =
        fa match {
          case JsonNumber => Show.showFromToString[BigDecimal]
          case JsonBool   => Show.showFromToString[Boolean]
          case JsonString => Show.shows[String](s => s""""$s"""")
          case JsonNull   => Show.shows[Null](_ => "null")
        }
    }
  }

  def tests[T](harness: Harness[T]): T = {
    import harness._
    import showModule._

    section("Generating Show Instances")(
      test("commons Show Instance") { () =>
        {

          implicit val alg = showAlgebra(primToShowNT, identity[String], identity[String])

          val testCases: List[(Person, String)] = List(
            Person(null, None)                                          -> """(name = ("null"), role = (()))""",
            Person("Alfred", None)                                      -> """(name = ("Alfred"), role = (()))""",
            Person("Alfred the Second", Some(User(true)))               -> """(name = ("Alfred the Second"), role = (user = (active = (true))))""",
            Person("Alfred the Third", Some(Admin(List("sys", "dev")))) -> """(name = ("Alfred the Third"), role = (admin = (rights = (["sys","dev"]))))"""
          )

          val show = person.to[Show]

          testCases.foldLeft[Result](Succeed)(
            (res, testCase) =>
              (res, testCase) match {
                case (Succeed, (data, expected)) => {

                  val showS = show.shows(data)

                  if (showS == expected) Succeed
                  else Fail(List(Right(s"got $showS expected $expected")))

                }

                case (fail: testz.Fail, (data, expected)) => {
                  val showS = show.shows(data)

                  if (showS == expected) fail
                  else Fail(Right(s"got $showS expected $expected") :: fail.failures)

                }

              }
          )

        }
      }
    )
  }
}
