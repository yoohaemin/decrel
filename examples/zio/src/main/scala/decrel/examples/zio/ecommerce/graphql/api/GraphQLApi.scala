package decrel.examples.zio.ecommerce.graphql.api

import caliban.{ GraphQL, RootResolver, graphQL }
import caliban.schema.Schema
import decrel.examples.zio.ecommerce.graphql.protocol
import zio.{ URLayer, ZLayer }

final class GraphQLApi(queryImplementations: QueryImplementations) {
  import queryImplementations._

  final case class Queries(
    customer: String => protocol.TaskQuery[Option[protocol.Customer]],
    order: String => protocol.TaskQuery[Option[protocol.Order]],
    checkoutView: String => protocol.TaskQuery[Option[protocol.CheckoutView]],
    adminOrderView: String => protocol.TaskQuery[Option[protocol.AdminOrderView]]
  )

  object Queries {
    given Schema[Any, Queries] = Schema.gen
  }

  val api: GraphQL[Any] =
    graphQL(
      RootResolver(
        Queries(customer, order, checkoutView, adminOrderView)
      )
    )
}

object GraphQLApi {
  val live: URLayer[QueryImplementations, GraphQLApi] =
    ZLayer.derive[GraphQLApi]
}
