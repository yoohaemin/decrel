/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel

/**
 * A _declaration_ of a `Relation` object by extending one of `Relation.Single`, `Relation.Optional` or
 * `Relation.Many` can be thought of as an edge in the directed graph that is your entire domain model.
 *
 * An expression with a type of `Relation[A, B]` represents a traversal of the domain model graph starting from `A`
 * into `B`, where `B` can be a result of accumulation of an arbitrary number of nodes.
 *
 * @tparam In **When declaring**: starting node of an edge. **When in an expression**: starting node of a traversal
 * @tparam Out **When declaring**: ending node of an edge. **When in an expression**: accumulated nodes of a traversal
 */
sealed trait Relation[-In, +Out]

object Relation {

  sealed trait Declared[-In, +Out] extends Relation[In, Out]

  trait Single[-In, +Out] extends Relation.Declared[In, Out]

  trait Optional[-In, +Out] extends Relation.Declared[In, Option[Out]]

  trait Many[-In, +Collection[+_], +Out] extends Relation.Declared[In, Collection[Out]]

  /**
   * Pass through relation
   */
  trait Self[A] extends Relation.Single[A, A]
  object Self {
    private val _self     = new Self[Any] {}
    def apply[A]: Self[A] = _self.asInstanceOf[Self[A]]
  }

  /**
   * Creates a relation on top of an existing relation value.
   */
  final case class Custom[Tree, In, Out](
    relation: Tree & Relation[In, Out]
  ) extends Relation[In, Out]

  sealed trait Composed[
    LeftTree,
    LeftIn,
    LeftOut,
    RightTree,
    RightIn,
    RightOut,
    Out
  ] extends Relation[LeftIn, Out]

  object Composed {
    case class Zipped[
      LeftTree,
      LeftIn,
      LeftOut,
      RightTree,
      RightIn,
      RightOut,
      ZippedOut
    ](
      left: LeftTree & Relation[LeftIn, LeftOut],
      right: RightTree & Relation[RightIn, RightOut]
    )(implicit
      zippedEv: LeftIn <:< RightIn
    ) extends Composed[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut, ZippedOut]

    case class Single[
      LeftTree,
      LeftIn,
      LeftOut,
      RightTree,
      RightIn,
      RightOut
    ](
      left: LeftTree,
      right: RightTree
    )(implicit
      composeOneEv: LeftOut <:< RightIn,
      leftRel: LeftTree <:< Relation.Single[LeftIn, LeftOut],
      rightRel: RightTree <:< Relation[RightIn, RightOut]
    ) extends Composed[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut, RightOut]

    case class Optional[
      LeftTree,
      LeftIn,
      LeftOut,
      RightTree,
      RightIn,
      RightOut
    ](
      left: LeftTree,
      right: RightTree
    )(implicit
      composeOneEv: LeftOut <:< RightIn,
      leftRel: LeftTree <:< Relation.Optional[LeftIn, LeftOut],
      rightRel: RightTree <:< Relation[RightIn, RightOut]
    ) extends Composed[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut, Option[RightOut]]

    case class Many[
      LeftTree,
      LeftIn,
      LeftOut,
      RightTree,
      RightIn,
      RightOut,
      CC[+A]
    ](
      left: LeftTree,
      right: RightTree
    )(implicit
      composeOneEv: LeftOut <:< RightIn,
      leftRel: LeftTree <:< Relation.Many[LeftIn, CC, LeftOut],
      rightRel: RightTree <:< Relation[RightIn, RightOut]
    ) extends Composed[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut, CC[RightOut]]
  }
}
