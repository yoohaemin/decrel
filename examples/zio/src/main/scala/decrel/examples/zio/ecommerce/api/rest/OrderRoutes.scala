package decrel.examples.zio.ecommerce.api.rest

import decrel.examples.zio.ecommerce.data
import decrel.examples.zio.ecommerce.stores.interface.ExampleError
import decrel.examples.zio.ecommerce.stores.interface.Proofs
import decrel.syntax._
import zio.{ IO, URLayer, ZIO, ZLayer }
import zio.http._
import zio.json._

object OrderRoutes {
  val live: URLayer[Proofs, OrderRoutes] =
    ZLayer.derive[OrderRoutes]
}

final class OrderRoutes(proofs: Proofs) {
  import proofs.given

  private def fetchOrder(id: data.Order.Id): IO[ExampleError, Option[data.Order]] =
    data.Order.fetch.toZIO(id).map(Some(_)).catchSome {
      case _: ExampleError.NotFound => ZIO.none
    }

  private def toResponse[A](effect: IO[ExampleError, Option[A]])(encode: A => Response) =
    effect.fold(
      {
        case _: ExampleError.NotFound => Response.status(Status.NotFound)
      },
      {
        case Some(value) => encode(value)
        case None        => Response.status(Status.NotFound)
      }
    )

  val routes: Routes[Any, Response] =
    Routes(
      Method.GET / "api" / "orders" / string("id") -> handler { (id: String, _: Request) =>
        toResponse(fetchOrder(data.Order.Id(id))) { order =>
          Response.json(Order.from(order).toJson)
        }
      }
    )
}
