package decrel.examples.zio.ecommerce.api.rest

import decrel.examples.zio.ecommerce.data
import decrel.examples.zio.ecommerce.stores.interface.ExampleError
import decrel.examples.zio.ecommerce.stores.interface.Proofs
import decrel.syntax._
import zio.{ IO, URLayer, ZIO, ZLayer }
import zio.http._
import zio.json._

object ViewRoutes {
  val live: URLayer[Proofs, ViewRoutes] =
    ZLayer.derive[ViewRoutes]
}

final class ViewRoutes(proofs: Proofs) {
  import proofs.given

  private def fetchOrder(id: data.Order.Id): IO[ExampleError, Option[data.Order]] =
    data.Order.fetch.toZIO(id).map(Some(_)).catchSome {
      case _: ExampleError.NotFound => ZIO.none
    }

  private def checkoutView(id: data.Order.Id): IO[ExampleError, Option[CheckoutView]] =
    fetchOrder(id).flatMap {
      case Some(order) =>
        ((data.Order.customer <>: data.Customer.loyaltyTier) &
          (data.Order.items <>: (data.Item.product & data.Item.price)) &
          data.Order.shipment).toZIO(order).map {
          case (customer, loyaltyTier, lines, shipment) =>
            Some(CheckoutView.from(order, customer, loyaltyTier, lines, shipment))
        }
      case None =>
        ZIO.none
    }

  private def adminOrderView(id: data.Order.Id): IO[ExampleError, Option[AdminOrderView]] =
    fetchOrder(id).flatMap {
      case Some(order) =>
        ((data.Order.customer <>: data.Customer.orders) &
          (data.Order.items <>: (data.Item.product & data.Item.price)) &
          data.Order.shipment).toZIO(order).map {
          case (customer, customerOrders, lines, shipment) =>
            Some(AdminOrderView.from(order, customer, customerOrders, lines, shipment))
        }
      case None =>
        ZIO.none
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
      Method.GET / "api" / "orders" / string("id") / "checkout-view" -> handler { (id: String, _: Request) =>
        toResponse(checkoutView(data.Order.Id(id))) { view =>
          Response.json(view.toJson)
        }
      },
      Method.GET / "api" / "orders" / string("id") / "admin-view" -> handler { (id: String, _: Request) =>
        toResponse(adminOrderView(data.Order.Id(id))) { view =>
          Response.json(view.toJson)
        }
      }
    )
}
