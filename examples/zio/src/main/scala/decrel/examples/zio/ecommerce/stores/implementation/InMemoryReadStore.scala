package decrel.examples.zio.ecommerce.stores.implementation

import decrel.examples.zio.ecommerce.data._
import decrel.examples.zio.ecommerce.stores.interface.{ ReadStore, StoreCall }
import decrel.examples.zio.ecommerce.stores.interface.ReadStore.{ ItemFilter, OrderFilter }
import zio.{ Chunk, IO, Ref, UIO, URLayer, ZIO, ZLayer }

object InMemoryReadStore {
  val live: URLayer[Any, ReadStore] =
    ZLayer.fromZIO {
      Ref.make(Chunk.empty[StoreCall]).map { ref =>
        new Live(ref, SampleData.data)
      }
    }

  private final class Live(
    ref: Ref[Chunk[StoreCall]],
    data: SampleData
  ) extends ReadStore {
    import Error._

    override def customers(ids: Chunk[Customer.Id]): IO[Error, Chunk[(Customer.Id, Customer)]] =
      fetch("customers", ids.map(_.value), ids, data.customers, "Customer")

    override def loyaltyTiers(ids: Chunk[LoyaltyTier.Id]): IO[Error, Chunk[(LoyaltyTier.Id, LoyaltyTier)]] =
      fetch("loyaltyTiers", ids.map(_.value), ids, data.loyaltyTiers, "LoyaltyTier")

    override def orders(ids: Chunk[Order.Id]): IO[Error, Chunk[(Order.Id, Order)]] =
      fetch("orders", ids.map(_.value), ids, data.orders, "Order")

    override def products(ids: Chunk[Product.Id]): IO[Error, Chunk[(Product.Id, Product)]] =
      fetch("products", ids.map(_.value), ids, data.products, "Product")

    override def prices(ids: Chunk[Price.Id]): IO[Error, Chunk[(Price.Id, Price)]] =
      fetch("prices", ids.map(_.value), ids, data.prices, "Price")

    override def shipments(ids: Chunk[Shipment.Id]): IO[Error, Chunk[(Shipment.Id, Shipment)]] =
      fetch("shipments", ids.map(_.value), ids, data.shipments, "Shipment")

    override def listOrders(
      cursor: Option[Order.Id],
      maxItems: Int,
      filter: OrderFilter
    ): UIO[Chunk[Order]] =
      record(
        "listOrders",
        filter.idsIn.getOrElse(Chunk.empty).map(_.value) ++
          filter.customerIdsIn.getOrElse(Chunk.empty).map(_.value)
      ).as {
        data.orders.values.filter { order =>
          filter.idsIn.forall(_.contains(order.id)) &&
          filter.customerIdsIn.forall(_.contains(order.customerId))
        }.to(Chunk).take(maxItems)
      }

    override def listItems(
      cursor: Option[Item.Id],
      maxItems: Int,
      filter: ItemFilter
    ): UIO[Chunk[Item]] =
      record(
        "listItems",
        filter.idsIn.getOrElse(Chunk.empty).map(_.value) ++
          filter.orderIdsIn.getOrElse(Chunk.empty).map(_.value)
      ).as {
        data.items.values.filter { item =>
          filter.idsIn.forall(_.contains(item.id)) &&
          filter.orderIdsIn.forall(_.contains(item.orderId))
        }.to(Chunk).take(maxItems)
      }

    override def calls: UIO[Chunk[StoreCall]] =
      ref.get

    override def clearCalls: UIO[Unit] =
      ref.set(Chunk.empty)

    private def record(name: String, keys: Chunk[String]): UIO[Unit] =
      ref.update(_.appended(StoreCall(name, keys.distinct)))

    private def fetch[Id, A](
      name: String,
      keys: Chunk[String],
      ids: Chunk[Id],
      source: Map[Id, A],
      entity: String
    ): IO[Error, Chunk[(Id, A)]] =
      record(name, keys) *>
        ZIO.foreach(ids.distinct) { id =>
          source.get(id) match {
            case Some(value) => ZIO.succeed(id -> value)
            case None        => ZIO.fail(NotFound(entity, id.toString))
          }
        }
  }
}

final case class SampleData(
  customers: Map[Customer.Id, Customer],
  loyaltyTiers: Map[LoyaltyTier.Id, LoyaltyTier],
  orders: Map[Order.Id, Order],
  items: Map[Item.Id, Item],
  products: Map[Product.Id, Product],
  prices: Map[Price.Id, Price],
  shipments: Map[Shipment.Id, Shipment]
)

object SampleData {
  val data: SampleData = {
    val gold = LoyaltyTier(LoyaltyTier.Id("tier-gold"), "Gold", 15)
    val silver = LoyaltyTier(LoyaltyTier.Id("tier-silver"), "Silver", 5)

    val customerA = Customer(Customer.Id("cust-1"), "Ada Lovelace", Some(gold.id))
    val customerB = Customer(Customer.Id("cust-2"), "Grace Hopper", Some(silver.id))
    val customerC = Customer(Customer.Id("cust-3"), "Linus Torvalds", None)

    val shipmentA = Shipment(Shipment.Id("ship-1"), "DHL", "TRACK-1", "InTransit")
    val shipmentB = Shipment(Shipment.Id("ship-2"), "FedEx", "TRACK-2", "Delivered")

    val orderA = Order(Order.Id("order-1"), customerA.id, Some(shipmentA.id), "checkout-001")
    val orderB = Order(Order.Id("order-2"), customerA.id, None, "history-002")
    val orderC = Order(Order.Id("order-3"), customerB.id, Some(shipmentB.id), "ops-003")

    val productA = Product(Product.Id("prod-1"), "sku-book", "Functional Programming Book")
    val productB = Product(Product.Id("prod-2"), "sku-mug", "ZIO Mug")
    val productC = Product(Product.Id("prod-3"), "sku-sticker", "Scala Sticker Pack")

    val priceA = Price(Price.Id("price-1"), BigDecimal(49), "USD")
    val priceB = Price(Price.Id("price-2"), BigDecimal(19), "USD")
    val priceC = Price(Price.Id("price-3"), BigDecimal(7), "USD")

    val itemA = Item(Item.Id("item-1"), orderA.id, productA.id, priceA.id, 1)
    val itemB = Item(Item.Id("item-2"), orderA.id, productB.id, priceB.id, 2)
    val itemC = Item(Item.Id("item-3"), orderB.id, productC.id, priceC.id, 3)
    val itemD = Item(Item.Id("item-4"), orderC.id, productA.id, priceA.id, 1)

    SampleData(
      customers = Map(customerA.id -> customerA, customerB.id -> customerB, customerC.id -> customerC),
      loyaltyTiers = Map(gold.id -> gold, silver.id -> silver),
      orders = Map(orderA.id -> orderA, orderB.id -> orderB, orderC.id -> orderC),
      items = Map(itemA.id -> itemA, itemB.id -> itemB, itemC.id -> itemC, itemD.id -> itemD),
      products = Map(productA.id -> productA, productB.id -> productB, productC.id -> productC),
      prices = Map(priceA.id -> priceA, priceB.id -> priceB, priceC.id -> priceC),
      shipments = Map(shipmentA.id -> shipmentA, shipmentB.id -> shipmentB)
    )
  }
}
