package decrel.examples.zio.ecommerce.stores.implementation

import decrel.examples.zio.ecommerce.data._
import decrel.examples.zio.ecommerce.stores.interface._
import zio.{ Chunk, URLayer, ZLayer }

object ProofImpl {
  val live: URLayer[ReadStore, Proofs] =
    ZLayer.derive[Impl]

  final class Impl(store: ReadStore) extends Proofs {
    override lazy val customerFetchProof =
      implementSingleDatasource(Customer.fetch)(store.customers)

    override lazy val loyaltyTierFetchProof =
      implementSingleDatasource(LoyaltyTier.fetch)(store.loyaltyTiers)

    override lazy val orderFetchProof =
      implementSingleDatasource(Order.fetch)(store.orders)

    override lazy val productFetchProof =
      implementSingleDatasource(Product.fetch)(store.products)

    override lazy val priceFetchProof =
      implementSingleDatasource(Price.fetch)(store.prices)

    override lazy val shipmentFetchProof =
      implementSingleDatasource(Shipment.fetch)(store.shipments)

    override lazy val orderCustomerProof =
      customerFetchProof.contramap(Order.customer)(_.customerId)

    override lazy val orderItemsProof =
      implementManyDatasource(Order.items) { orders =>
        store
          .listItems(
            cursor = None,
            maxItems = 1000,
            filter = ReadStore.ItemFilter(orderIdsIn = Some(orders.map(_.id)))
          )
          .map { items =>
            orders.map { order =>
              order -> items.filter(_.orderId == order.id)
            }
          }
        }

    override lazy val orderShipmentProof =
      shipmentFetchProof.contramapOptional(Order.shipment)(_.shipmentId)

    override lazy val customerLoyaltyTierProof =
      loyaltyTierFetchProof.contramapOptional(Customer.loyaltyTier)(_.loyaltyTierId)

    override lazy val customerOrdersProof =
      implementManyDatasource(Customer.orders) { customers =>
        store
          .listOrders(
            cursor = None,
            maxItems = 1000,
            filter = ReadStore.OrderFilter(customerIdsIn = Some(customers.map(_.id)))
          )
          .map { orders =>
            customers.map { customer =>
              customer -> orders.filter(_.customerId == customer.id)
            }
          }
        }

    override lazy val itemProductProof =
      productFetchProof.contramap(Item.product)(_.productId)

    override lazy val itemPriceProof =
      priceFetchProof.contramap(Item.price)(_.priceId)
  }
}
