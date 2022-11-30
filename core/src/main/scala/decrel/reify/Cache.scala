package decrel.reify

import decrel.Relation

/**
 * Simple cache implementation that is reusable across various integrations
 */
case class Cache private (
  private[decrel] val entries: Map[Cache.Key[?, ?, ?], Cache.Entry[?, ?, ?]]
) {

  // Because Scala 3 dropped support for existential type,
  // consistency of key and value can only be enforced here.
  def add[Rel, A, B](relation: Rel & Relation[A, B], key: A, value: B): Cache = {
    val k = Cache.Key(relation, key)
    new Cache(entries + (k -> Cache.Entry(k, value)))
  }

}

object Cache {

  // TODO see if there's a way to allow customizing the underlying Map
  val empty: Cache = Cache(Map.empty)

  final case class Key[Rel, A, B](
    relation: Rel & Relation[A, B],
    key: A
  ) {
    type R      = Rel
    type RRel   = Rel & Relation[A, B]
    type Input  = A
    type Result = B

    private[decrel] def _relation: RRel                                    = relation
    private[decrel] def _key: Input                                        = key
    private[decrel] def relationEv: <:<[RRel, R & Relation[Input, Result]] = implicitly
  }

  final case class Entry[Rel, A, B](
    key: Key[Rel, A, B],
    value: B
  ) {
    type Result = B

    private[decrel] def _value: Result                   = value
    private[decrel] def valueEv: <:<[Result, key.Result] = implicitly
  }

}
