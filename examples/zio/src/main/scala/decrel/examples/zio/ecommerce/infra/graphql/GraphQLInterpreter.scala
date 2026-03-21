package decrel.examples.zio.ecommerce.infra.graphql

import caliban.{ CalibanError, GraphQLInterpreter => CalibanInterpreter }
import decrel.examples.zio.ecommerce.graphql.api.GraphQLApi
import zio.{ ZIO, ZLayer }

object GraphQLInterpreter {
  val live: ZLayer[GraphQLApi, CalibanError, CalibanInterpreter[Any, CalibanError]] =
    ZLayer.scoped {
      ZIO.serviceWithZIO[GraphQLApi](_.api.interpreter)
    }
}
