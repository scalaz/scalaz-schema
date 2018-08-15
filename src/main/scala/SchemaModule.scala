package scalaz

package schema

import monocle.{ Getter, Prism }

trait SchemaModule {
  type Prim[A]
  type SumTermId
  type ProductTermId

  def prim[A](prim: Prim[A]): Schema[A]                                                = Schema.PrimSchema(prim)
  def record[A](field: Schema.Field[A, _], fields: Schema.Field[A, _]*): Schema[A]     = ???
  def union[A](branch: Schema.Branch[A, _], branches: Schema.Branch[A, _]*): Schema[A] = ???
  def seq[A](element: Schema[A]): Schema[List[A]]                                      = ???

  sealed trait Schema[A]

  object Schema {
    // Writing final here triggers a warning, using sealed instead achieves almost the same effect
    // without warning. See https://issues.scala-lang.org/browse/SI-4440
    sealed case class PrimSchema[A](prim: Prim[A])                      extends Schema[A]
    sealed case class Union[A](terms: NonEmptyList[Branch[A, _]])       extends Schema[A]
    sealed case class RecordSchema[A](terms: NonEmptyList[Field[A, _]]) extends Schema[A]
    sealed case class SeqSchema[A](element: Schema[A])                  extends Schema[List[A]]

    /**
     * A term of type `A0` in a sum of type `A`.
     */
    sealed trait Field[A, A0]

    object Field {
      sealed case class Essential[A, A0](
        id: ProductTermId,
        base: Schema[A0],
        getter: Getter[A, A0],
        default: Option[A0])
          extends Field[A, A0]
      sealed case class NonEssential[A, A0](
        id: ProductTermId,
        base: Schema[A0],
        getter: monocle.Optional[A, A0])
          extends Field[A, Option[A0]]
    }

    /**
     * A term of type `A0` in a sum of type `A`.
     * For example, the `Left` term of the `Either` sum.
     */
    sealed case class Branch[A, A0](id: SumTermId, base: Schema[A0], prism: Prism[A, A0])
  }
}
