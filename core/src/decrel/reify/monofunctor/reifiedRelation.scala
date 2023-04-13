package decrel.reify.monofunctor

import decrel.Zippable

import scala.collection.{ BuildFrom, IterableOps }

trait reifiedRelation { this: access =>

  /**
   * Defines the behavior of Proofs.
   * The below cases embody the patterns in which the reifiedRelations can
   * actually be composed. Composition patterns of of `ReifiedRelation` is orthogonal
   * to the composition patterns of Proofs.
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
    abstract class Custom[In, Out] extends ReifiedRelation[In, Out]

    private[monofunctor] class FromFunction[In, Out](
      f: In => Out
    ) extends ReifiedRelation[In, Out] {

      override def apply(in: In): Access[Out] =
        succeed(f(in))

      override def applyMultiple[
        Coll[+T] <: Iterable[T] & IterableOps[T, Coll, Coll[T]]
      ](
        in: Coll[In]
      ): Access[Coll[Out]] =
        succeed(in.map(f))

    }

    private[monofunctor] class Transformed[In, CC[_], DD[_], Out](
      reifiedRelation: ReifiedRelation[In, CC[Out]],
      transform: CC[Out] => DD[Out]
    ) extends ReifiedRelation[In, DD[Out]] {

      override def apply(in: In): Access[DD[Out]] =
        reifiedRelation.apply(in).map(transform)

      override def applyMultiple[
        Coll[+T] <: Iterable[T] & IterableOps[T, Coll, Coll[T]]
      ](
        in: Coll[In]
      ): Access[Coll[DD[Out]]] =
        reifiedRelation.applyMultiple(in).map(_.map(transform))

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

    private[monofunctor] class Zipped[
      LeftIn,
      LeftOut,
      RightIn,
      RightOut,
      Zipped
    ](
      left: ReifiedRelation[LeftIn, LeftOut],
      right: ReifiedRelation[RightIn, RightOut]
    )(implicit
      ev: LeftIn <:< RightIn,
      zippable: Zippable.Out[LeftOut, RightOut, Zipped]
    ) extends ReifiedRelation[LeftIn, Zipped] {

      override def apply(in: LeftIn): Access[Zipped] =
        left
          .apply(in)
          .flatMap { leftOut =>
            right
              .apply(ev(in))
              .map { rightOut =>
                zippable.zip(leftOut, rightOut)
              }
          }

      override def applyMultiple[Coll[+T] <: Iterable[T] & IterableOps[T, Coll, Coll[T]]](
        in: Coll[LeftIn]
      ): Access[Coll[Zipped]] =
        left
          .applyMultiple(in)
          .flatMap { leftOut =>
            right
              .applyMultiple(ev.liftCo(in))
              .map { rightOut =>
                leftOut.zip(rightOut).map(p => zippable.zip(p._1, p._2))
              }
          }

    }

  }

}
