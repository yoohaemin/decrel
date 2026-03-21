package decrel.examples.zio.ecommerce.infra.http

import decrel.examples.zio.ecommerce.api.rest.{ CustomerRoutes, OrderRoutes, ViewRoutes }
import zio.{ URLayer, ZLayer }
import zio.http._

final class RestServer(
  customerRoutes: CustomerRoutes,
  orderRoutes: OrderRoutes,
  viewRoutes: ViewRoutes
) {
  val routes: Routes[Any, Response] =
    customerRoutes.routes ++ orderRoutes.routes ++ viewRoutes.routes
}

object RestServer {
  val live: URLayer[CustomerRoutes & OrderRoutes & ViewRoutes, RestServer] =
    ZLayer.derive[RestServer]
}
