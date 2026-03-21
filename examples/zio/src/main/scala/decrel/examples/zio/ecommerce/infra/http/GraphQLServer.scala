package decrel.examples.zio.ecommerce.infra.http

import caliban.{ CalibanError, GraphQLInterpreter => CalibanInterpreter, QuickAdapter }
import zio.{ ZIO, ZLayer }
import zio.http._

final case class GraphQLServer(routes: Routes[Any, Response])

object GraphQLServer {
  val live: ZLayer[CalibanInterpreter[Any, CalibanError], Throwable, GraphQLServer] =
    ZLayer.fromZIO {
      ZIO.service[CalibanInterpreter[Any, CalibanError]].map { interpreter =>
        GraphQLServer(
          QuickAdapter(interpreter).routes(
            apiPath = "/api/graphql",
            graphiqlPath = Some("/graphiql")
          )
        )
      }
    }
}
