package decrel.reify.bifunctor

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
  sealed abstract class ReifiedRelation[-In, +E, Out] {

    def apply(in: In): Access[E, Out]

    def applyMultiple[
      Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]
    ](
      in: Coll[In]
    ): Access[E, Coll[Out]]

  }

  object ReifiedRelation {

    /**
     * Reification of a Single Relation
     */
    abstract class Custom[In, +E, Out] extends ReifiedRelation[In, E, Out]

    private[bifunctor] class FromFunction[In, Out](
      f: In => Out
    ) extends ReifiedRelation[In, Nothing, Out] {

      override def apply(in: In): Access[Nothing, Out] =
        succeed(f(in))

      override def applyMultiple[
        Coll[+T] <: Iterable[T] & IterableOps[T, Coll, Coll[T]]
      ](
        in: Coll[In]
      ): Access[Nothing, Coll[Out]] =
        succeed(in.map(f))

    }

    private[bifunctor] class Transformed[In, CC[_], +E, DD[_], Out](
      reifiedRelation: ReifiedRelation[In, E, CC[Out]],
      transform: CC[Out] => DD[Out]
    ) extends ReifiedRelation[In, E, DD[Out]] {

      override def apply(in: In): Access[E, DD[Out]] =
        reifiedRelation.apply(in).map(transform)

      override def applyMultiple[
        Coll[+T] <: Iterable[T] & IterableOps[T, Coll, Coll[T]]
      ](
        in: Coll[In]
      ): Access[E, Coll[DD[Out]]] =
        reifiedRelation.applyMultiple(in).map(_.map(transform))

    }

    private[bifunctor] class ComposedSingle[
      LeftIn,
      LeftE <: RightE,
      LeftOut,
      RightIn,
      RightE,
      RightOut
    ](
      left: ReifiedRelation[LeftIn, LeftE, LeftOut],
      right: ReifiedRelation[RightIn, RightE, RightOut]
    )(implicit
      ev: LeftOut <:< RightIn,
      e: LeftE <:< RightE
    ) extends ReifiedRelation[LeftIn, RightE, RightOut] {

      override def apply(in: LeftIn): Access[RightE, RightOut] =
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
      ): Access[RightE, Coll[RightOut]] =
        left
          .applyMultiple(in)
          .flatMap { leftOuts =>
            right
              .applyMultiple(ev.liftCo(leftOuts))
          }

    }

    private[bifunctor] class ComposedOptional[
      LeftIn,
      LeftE <: RightE,
      LeftOut,
      RightIn,
      RightE,
      RightOut
    ](
      left: ReifiedRelation[LeftIn, LeftE, Option[LeftOut]],
      right: ReifiedRelation[RightIn, RightE, RightOut]
    )(implicit
      ev: LeftOut <:< RightIn,
      e: LeftE <:< RightE
    ) extends ReifiedRelation[LeftIn, RightE, Option[RightOut]] {

      override def apply(in: LeftIn): Access[RightE, Option[RightOut]] =
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
      ): Access[RightE, Coll[Option[RightOut]]] =
        left
          .applyMultiple(in)
          .flatMap { (leftOuts: Coll[Option[LeftOut]]) =>
            type X[+A] = Coll[Option[A]]
            val inputs: Coll[Option[RightIn]] = ev.liftCo[X](leftOuts)
            val flat: Iterable[RightIn]       = inputs.flatten
            val results: Access[RightE, Iterable[RightOut]] =
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

    private[bifunctor] class ComposedMany[
      LeftIn,
      LeftE <: RightE,
      LeftOut,
      RightIn,
      RightE,
      CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]],
      RightOut
    ](
      left: ReifiedRelation[LeftIn, LeftE, CC[LeftOut]],
      right: ReifiedRelation[RightIn, RightE, RightOut]
    )(implicit
      ev: LeftOut <:< RightIn,
      bf: BuildFrom[CC[RightIn], RightOut, CC[RightOut]]
    ) extends ReifiedRelation[LeftIn, RightE, CC[RightOut]] {

      override def apply(in: LeftIn): Access[RightE, CC[RightOut]] =
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
      ): Access[RightE, Coll[CC[RightOut]]] =
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

    private[bifunctor] class Zipped[
      LeftIn,
      LeftE <: RightE,
      LeftOut,
      RightIn,
      RightE,
      RightOut,
      Zipped
    ](
      left: ReifiedRelation[LeftIn, LeftE, LeftOut],
      right: ReifiedRelation[RightIn, RightE, RightOut]
    )(implicit
      ev: LeftIn <:< RightIn,
      zippable: Zippable.Out[LeftOut, RightOut, Zipped]
    ) extends ReifiedRelation[LeftIn, RightE, Zipped] {

      override def apply(in: LeftIn): Access[RightE, Zipped] =
        foreach(
          List[Access[RightE, Any]](
            left.apply(in),
            right.apply(ev(in))
          )
        )(identity).map {
          case List(leftOut, rightOut) =>
            zippable.zip(
              leftOut.asInstanceOf[LeftOut],
              rightOut.asInstanceOf[RightOut]
            )
          case _ =>
            throw new RuntimeException(
              "foreach has returned more or less elements than 2 during composedZippedProof.apply"
            )
        }

      override def applyMultiple[Coll[+T] <: Iterable[T] & IterableOps[T, Coll, Coll[T]]](
        in: Coll[LeftIn]
      ): Access[RightE, Coll[Zipped]] =
        foreach(
          List[Access[RightE, Iterable[Any]]](
            left.applyMultiple(in),
            right.applyMultiple(ev.liftCo(in))
          )
        )(identity).map {
          case List(leftOuts, rightOuts) =>
            val leftOutsIt  = leftOuts.asInstanceOf[Iterable[LeftOut]].iterator
            val rightOutsIt = rightOuts.asInstanceOf[Iterable[RightOut]].iterator
            in.map(_ => zippable.zip(leftOutsIt.next(), rightOutsIt.next()))
          case _ =>
            throw new RuntimeException(
              "foreach has returned more or less elements than 2 during composedZippedProof.applyMultiple"
            )
        }

    }

  }
}
