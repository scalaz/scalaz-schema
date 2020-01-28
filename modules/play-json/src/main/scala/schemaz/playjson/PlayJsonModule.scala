package schemaz.playjson

import schemaz._, recursion._
import scalaz.{ -\/, Applicative, \/-, ~> }
import scalaz.Scalaz._
import scalaz.Liskov.<~<
import play.api.libs.json._
import play.api.libs.functional.syntax._

trait PlayJsonModule[R <: Realisation] extends SchemaModule[R] {

  import SchemaF._

  type LabelledSchema[A] = (Boolean, Schema[A])

  private def ascribeWith(
    label: Boolean
  ): Schema ~> LabelledSchema =
    new (Schema ~> LabelledSchema) {

      def apply[A](fschema: Schema[A]): LabelledSchema[A] =
        (label, fschema)
    }

  final val labelRecordFields: HCoalgebra[HEnvT[Boolean, RSchema, ?[_], ?], LabelledSchema] =
    new (LabelledSchema ~> HEnvT[Boolean, RSchema, LabelledSchema, ?]) {

      def apply[A](seed: LabelledSchema[A]): HEnvT[Boolean, RSchema, LabelledSchema, A] =
        seed match {
          case (x, Fix(r @ RecordF(_))) => HEnvT(x, r.hmap(ascribeWith(true)))
          case (x, Fix(ProdF(left, right))) =>
            HEnvT(
              x,
              ProdF(
                ascribeWith(false)(left),
                ascribeWith(x)(right)
              )
            )
          case (x, s) => HEnvT(x, s.unFix.hmap(ascribeWith(x)))
        }
    }

  // This is needed in order to use `traverse` in `reads`.
  implicit private val readsApplicative = new Applicative[JsResult] {

    def point[A](a: => A): JsResult[A] = JsSuccess(a)

    def ap[A, B](fa: => JsResult[A])(f: => JsResult[A => B]): JsResult[B] = (f, fa) match {
      // Shamelessly copied from the play-json library
      case (JsSuccess(f, _), JsSuccess(a, _)) => JsSuccess(f(a))
      case (JsError(e1), JsError(e2))         => JsError(JsError.merge(e1, e2))
      case (JsError(e), _)                    => JsError(e)
      case (_, JsError(e))                    => JsError(e)

    }
  }

  // This is needed to allow undefined optional fields to be treated as `None`.
  final private def undefinedAsNull[A](field: String, r: Reads[A]): Reads[A] = Reads { json =>
    ((json \ field) match {
      case JsDefined(v) => r.reads(v)
      case _            => r.reads(JsNull)
    }).repath(JsPath \ field)
  }

  final private val labellingSeed =
    new (Schema ~> LabelledSchema) {

      def apply[A](fSchema: Schema[A]): LabelledSchema[A] =
        (false, fSchema)
    }

  implicit object ReadsTransform extends Transform[Reads] {
    def apply[A, B](fa: Reads[A], p: NIso[A, B]): Reads[B] = fa.map(p.f)
  }

  implicit final def readsInterpreter(
    implicit primNT: R.Prim ~> Reads,
    branchLabel: R.SumTermId <~< String,
    fieldLabel: R.ProductTermId <~< String
  ): RInterpreter[Reads] =
    Interpreter
      .hylo(
        labelRecordFields,
        new (HEnvT[Boolean, RSchema, Reads, ?] ~> Reads) {

          def apply[A](schema: HEnvT[Boolean, RSchema, Reads, A]): Reads[A] = schema.fa match {
            case One() =>
              Reads {
                case JsNull => JsSuccess(())
                case _      => JsError(Seq(JsPath -> Seq(JsonValidationError("error.expected.null"))))
              }
            case SumF(left, right) =>
              Reads(
                json =>
                  right
                    .reads(json)
                    .fold(
                      el =>
                        left.reads(json).map(-\/.apply) match {
                          case JsError(er) => JsError(JsError.merge(el, er))
                          case x           => x
                        },
                      a => JsSuccess(\/-(a))
                    )
              )
            case p: Prod[Reads, a, b] =>
              if (schema.ask)
                p.left.and(p.right)((x: a, y: b) => (x, y))
              else
                (JsPath \ "_1")
                  .read(p.left)
                  .and((JsPath \ "_2").read(p.right))((x: a, y: b) => (x, y))
            case PrimSchemaF(p)      => primNT(p)
            case BranchF(id, schema) => undefinedAsNull(branchLabel(id), schema)
            case u: Union[Reads, a] =>
              u.choices
            case FieldF(id, schema) => undefinedAsNull(fieldLabel(id), schema)
            case r: Record[Reads, a] =>
              r.fields
            case SeqF(elem) =>
              Reads {
                case JsArray(elems) =>
                  elems.toList.traverse(elem.reads _)
                case _ => JsError(Seq(JsPath -> Seq(JsonValidationError("error.expected.jsarray"))))
              }
            case ref @ SelfReference(_, _) => Reads(ref.unroll.reads)
          }
        }
      )
      .compose(labellingSeed)

  implicit object WritesTransform extends Transform[Writes] {
    def apply[A, B](fa: Writes[A], p: NIso[A, B]): Writes[B] = fa.contramap(p.g)
  }

  implicit final def writesInterpreter(
    implicit primNT: R.Prim ~> Writes,
    branchLabel: R.SumTermId <~< String,
    fieldLabel: R.ProductTermId <~< String
  ): RInterpreter[Writes] =
    Interpreter
      .hylo(
        labelRecordFields,
        new (HEnvT[Boolean, RSchema, Writes, ?] ~> Writes) {

          def apply[A](env: HEnvT[Boolean, RSchema, Writes, A]): Writes[A] = env.fa match {
            case One()             => Writes(_ => JsNull)
            case SumF(left, right) => Writes(_.fold(left.writes, right.writes))
            case ProdF(left, right) =>
              if (env.ask)
                Writes(
                  pair =>
                    (left.writes(pair._1), right.writes(pair._2)) match {
                      case (l @ JsObject(_), r @ JsObject(_)) => l ++ r
                      // the following case is impossible, but scalac cannot know that.
                      case (l, r) => Json.obj("_1" -> l, "_2" -> r)
                    }
                )
              else
                Writes(
                  pair => Json.obj("_1" -> left.writes(pair._1), "_2" -> right.writes(pair._2))
                )
            case PrimSchemaF(p)            => primNT(p)
            case BranchF(id, s)            => Writes(a => Json.obj(branchLabel(id) -> s.writes(a)))
            case u: Union[Writes, a]       => u.choices
            case FieldF(id, s)             => Writes(a => Json.obj(fieldLabel(id) -> s.writes(a)))
            case r: Record[Writes, a]      => r.fields
            case SeqF(elem)                => Writes(seq => JsArray(seq.map(elem.writes(_))))
            case ref @ SelfReference(_, _) => Writes(ref.unroll.writes)
          }

        }
      )
      .compose(labellingSeed)

}
