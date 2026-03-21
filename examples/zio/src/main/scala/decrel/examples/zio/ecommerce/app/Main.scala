package decrel.examples.zio.ecommerce.app

import decrel.examples.zio.ecommerce.api.rest.{ CustomerRoutes, OrderRoutes, ViewRoutes }
import decrel.examples.zio.ecommerce.graphql.api.{ GraphQLApi, QueryImplementations }
import decrel.examples.zio.ecommerce.graphql.transformers.Transformers
import decrel.examples.zio.ecommerce.infra.graphql.GraphQLInterpreter
import decrel.examples.zio.ecommerce.infra.http.{ GraphQLServer, RestServer }
import decrel.examples.zio.ecommerce.stores.implementation.{ InMemoryReadStore, ProofImpl }
import zio._
import zio.http._

object Main extends ZIOAppDefault {
  override val run =
    (for {
      _ <- Console.printLine("REST:     http://localhost:8080/api/orders/order-1/checkout-view")
      _ <- Console.printLine("GraphQL:  http://localhost:8080/api/graphql")
      _ <- Console.printLine("GraphiQL: http://localhost:8080/graphiql")
      rest <- ZIO.service[RestServer]
      gql <- ZIO.service[GraphQLServer]
      routes = rest.routes ++ gql.routes
      _ <- Server.serve(routes)
    } yield ()).provide(
      Server.default,
      InMemoryReadStore.live,
      ProofImpl.live,
      CustomerRoutes.live,
      OrderRoutes.live,
      ViewRoutes.live,
      RestServer.live,
      Transformers.live,
      QueryImplementations.live,
      GraphQLApi.live,
      GraphQLInterpreter.live,
      GraphQLServer.live
    )
}
