package decrel.reify.monofunctor

import scala.collection.{BuildFrom, IterableOps}

trait reifiedRelation { this: access =>

  /**
   * Defines the behavior of Proofs.
   * The "Composed" cases embody the patterns in which the reifiedRelations can
   * actually be composed. Composition of `ReifiedRelation` is orthogonal to the
   * composition of Proofs.
   *
   * Notice that there is no `Rel` type parameter. This is because it's not relevant
   * when the relations are already reified.
   */
  sealed abstract class ReifiedRelation[-In, Out] {

    def apply(in: In): Access[Out]

    def applyMultiple[
      Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]
    ](
      in: Coll[In]
    ): Access[Coll[Out]]

  }

  object ReifiedRelation {

    /**
     * Reification of a Single Relation
     */
    abstract class Defined[In, Out] extends ReifiedRelation[In, Out] {

      def apply(in: In): Access[Out] =
        map(applyMultiple(List(in)))(_.head)

    }

    private[monofunctor] class ComposedSingle[LeftIn, LeftOut, RightIn, RightOut](
      left: ReifiedRelation[LeftIn, LeftOut],
      right: ReifiedRelation[RightIn, RightOut]
    )(implicit
      ev: LeftOut <:< RightIn
    ) extends ReifiedRelation[LeftIn, RightOut] {

      override def apply(in: LeftIn): Access[RightOut] =
        left
          .apply(in)
          .flatMap { leftOut =>
            right
              .apply(ev(leftOut))
          }

      override def applyMultiple[
        Coll[+T] <: Iterable[T] & IterableOps[T, Coll, Coll[T]]
      ](
        in: Coll[LeftIn]
      ): Access[Coll[RightOut]] =
        left
          .applyMultiple(in)
          .flatMap { leftOuts =>
            right
              .applyMultiple(ev.liftCo(leftOuts))
          }

    }

    private[monofunctor] class ComposedOptional[LeftIn, LeftOut, RightIn, RightOut](
      left: ReifiedRelation[LeftIn, Option[LeftOut]],
      right: ReifiedRelation[RightIn, RightOut]
    )(implicit
      ev: LeftOut <:< RightIn
    ) extends ReifiedRelation[LeftIn, Option[RightOut]] {

      override def apply(in: LeftIn): Access[Option[RightOut]] =
        left
          .apply(in)
          .flatMap { leftOut =>
            ev.liftCo(leftOut) match {
              case Some(rightIn) =>
                right
                  .apply(rightIn)
                  .map(Some(_))
              case None =>
                succeed(None)
            }
          }

      override def applyMultiple[
        Coll[+T] <: Iterable[T] & IterableOps[T, Coll, Coll[T]]
      ](
        in: Coll[LeftIn]
      ): Access[Coll[Option[RightOut]]] =
        left
          .applyMultiple(in)
          .flatMap { (leftOuts: Coll[Option[LeftOut]]) =>
            type X[+A] = Coll[Option[A]]
            val inputs: Coll[Option[RightIn]] = ev.liftCo[X](leftOuts)
            val flat: Iterable[RightIn]       = inputs.flatten
            val results: Access[Iterable[RightOut]] =
              right.applyMultiple(flat)
            results.map { resultsIterable =>
              val it = resultsIterable.iterator
              inputs.map {
                case Some(_) => Some(it.next())
                case None    => None
              }
            }
          }
    }

    private[monofunctor] class ComposedMany[
      LeftIn,
      LeftOut,
      RightIn,
      CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]],
      RightOut
    ](
      left: ReifiedRelation[LeftIn, CC[LeftOut]],
      right: ReifiedRelation[RightIn, RightOut]
    )(implicit
      ev: LeftOut <:< RightIn,
      bf: BuildFrom[CC[RightIn], RightOut, CC[RightOut]]
    ) extends ReifiedRelation[LeftIn, CC[RightOut]] {

      override def apply(in: LeftIn): Access[CC[RightOut]] =
        left
          .apply(in)
          .flatMap { leftOut =>
            val rightIns = ev.liftCo[CC](leftOut)
            right
              .applyMultiple[CC](rightIns)
              .map(_.to(bf.toFactory(rightIns)))
          }

      override def applyMultiple[Coll[+T] <: Iterable[T] & IterableOps[T, Coll, Coll[T]]](
        in: Coll[LeftIn]
      ): Access[Coll[CC[RightOut]]] =
        left
          .applyMultiple(in)
          .flatMap { leftOuts =>
            type X[+A] = Coll[CC[A]]
            val inputs: Coll[CC[RightIn]] = ev.liftCo[X](leftOuts)
            val flattened                 = inputs.flatten

            val results = right.applyMultiple(flattened)
            results.map { resultsIterable =>
              val it = resultsIterable.iterator
              inputs.map(input => it.take(input.size).to(bf.toFactory(input)))
            }
          }
    }
  }

}
