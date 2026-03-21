package decrel.examples.zio.ecommerce.api.rest

import decrel.examples.zio.ecommerce.data
import decrel.examples.zio.ecommerce.stores.interface.ExampleError
import decrel.examples.zio.ecommerce.stores.interface.Proofs
import decrel.syntax._
import zio.{ IO, URLayer, ZIO, ZLayer }
import zio.http._
import zio.json._

object CustomerRoutes {
  val live: URLayer[Proofs, CustomerRoutes] =
    ZLayer.derive[CustomerRoutes]
}

final class CustomerRoutes(proofs: Proofs) {
  import proofs.given

  private def fetchCustomer(id: data.Customer.Id): IO[ExampleError, Option[data.Customer]] =
    data.Customer.fetch.toZIO(id).map(Some(_)).catchSome {
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
      Method.GET / "api" / "customers" / string("id") -> handler { (id: String, _: Request) =>
        toResponse(fetchCustomer(data.Customer.Id(id))) { customer =>
          Response.json(Customer.from(customer).toJson)
        }
      }
    )
}
