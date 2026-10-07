package com.tablethyme;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RestaurantService {
  final MenuRepository menus;
  final TableRepository tables;
  final OrderRepository orders;
  final BillRepository bills;

  RestaurantService(MenuRepository m, TableRepository t, OrderRepository o, BillRepository b) {
    menus = m;
    tables = t;
    orders = o;
    bills = b;
  }

  static ResponseStatusException bad(String text) {
    return new ResponseStatusException(HttpStatus.CONFLICT, text);
  }

  static <T> T required(Optional<T> value) {
    return value.orElseThrow(
        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found"));
  }

  static BigDecimal money(BigDecimal n) {
    return n.setScale(2, RoundingMode.HALF_UP);
  }

  @Transactional(readOnly = true)
  public List<MenuItem> menu() {
    return menus.findAll();
  }

  @Transactional(readOnly = true)
  public List<DiningTable> tableList() {
    return tables.findAll();
  }

  @Transactional(readOnly = true)
  public List<RestaurantOrder> orderList() {
    return orders.findAllByOrderByIdDesc();
  }

  @Transactional(readOnly = true)
  public List<Bill> billList() {
    return bills.findAllByOrderByIdDesc();
  }

  @Transactional
  public RestaurantOrder place(Dtos.PlaceOrder input) {
    var existing = orders.findByRequestKey(input.requestKey());
    if (existing.isPresent()) return existing.get();
    RestaurantOrder o = new RestaurantOrder();
    o.requestKey = input.requestKey();
    o.orderType = input.orderType();
    o.customer = input.customer();
    o.notes = input.notes();
    if (input.orderType().equals("DINE_IN")) {
      if (input.tableId() == null) throw bad("Choose a table for dine-in");
      o.diningTable = required(tables.lock(input.tableId()));
      if (!o.diningTable.status.equals("AVAILABLE"))
        throw bad("This table already has an active order");
      o.diningTable.status = "OCCUPIED";
    }
    // Merge duplicate lines and lock inventory in stable ID order to prevent overselling/deadlocks.
    Map<Long, Integer> quantities = new TreeMap<>();
    for (var line : input.items())
      quantities.merge(line.menuItemId(), line.quantity(), Integer::sum);
    for (var entry : quantities.entrySet()) {
      MenuItem m = required(menus.lock(entry.getKey()));
      int q = entry.getValue();
      if (q > 99 || !m.available || m.stock < q)
        throw bad("Insufficient stock or unavailable: " + m.name);
      m.stock -= q;
      OrderItem line = new OrderItem();
      line.order = o;
      line.menuItem = m;
      line.name = m.name;
      line.unitPrice = m.price;
      line.quantity = q;
      o.items.add(line);
    }
    return orders.save(o);
  }

  @Transactional
  public RestaurantOrder status(Long id, String next) {
    var o = required(orders.lock(id));
    if (o.status.equals(next)) return o;
    if (next.equals("CANCELLED")) {
      if (!Set.of("RECEIVED", "PREPARING").contains(o.status))
        throw bad("Only received or preparing orders can be cancelled");
      o.items.stream()
          .sorted(Comparator.comparing(i -> i.menuItem.id))
          .forEach(
              i -> {
                var m = required(menus.lock(i.menuItem.id));
                m.stock += i.quantity;
              });
      if (o.diningTable != null) required(tables.lock(o.diningTable.id)).status = "AVAILABLE";
    } else if (!((o.status.equals("RECEIVED") && next.equals("PREPARING"))
        || (o.status.equals("PREPARING") && next.equals("SERVED"))))
      throw bad("Invalid transition: " + o.status + " to " + next);
    o.status = next;
    return o;
  }

  @Transactional
  public Bill generate(Long id, BigDecimal percent) {
    var o = required(orders.lock(id));
    var existing = bills.findByOrderId(id);
    if (existing.isPresent()) return existing.get();
    if (!o.status.equals("SERVED")) throw bad("Serve the order before generating its bill");
    Bill b = new Bill();
    b.order = o;
    b.subtotal =
        money(
            o.items.stream()
                .map(i -> i.unitPrice.multiply(BigDecimal.valueOf(i.quantity)))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    b.discount = money(b.subtotal.multiply(percent).divide(new BigDecimal("100")));
    b.taxAmount = money(b.subtotal.subtract(b.discount).multiply(new BigDecimal("0.05")));
    b.total = money(b.subtotal.subtract(b.discount).add(b.taxAmount));
    o.status = "BILLING";
    if (o.diningTable != null) required(tables.lock(o.diningTable.id)).status = "BILLING";
    return bills.save(b);
  }

  @Transactional
  public Bill pay(Long id, String method) {
    var b = required(bills.lock(id));
    if (b.paymentStatus.equals("PAID")) return b;
    var o = required(orders.lock(b.order.id));
    b.paymentStatus = "PAID";
    b.paymentMethod = method;
    b.paidAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
    o.status = "COMPLETED";
    if (o.diningTable != null) required(tables.lock(o.diningTable.id)).status = "AVAILABLE";
    return b;
  }

  @Transactional
  public MenuItem saveMenu(Long id, Dtos.MenuInput v) {
    var m = id == null ? new MenuItem() : required(menus.lock(id));
    m.name = v.name();
    m.category = v.category();
    m.description = v.description();
    m.price = money(v.price());
    m.vegetarian = v.vegetarian();
    m.available = v.available();
    m.stock = v.stock();
    m.emoji = v.emoji();
    return menus.save(m);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> analytics() {
    var paid =
        bills.findAllByOrderByIdDesc().stream()
            .filter(b -> b.paymentStatus.equals("PAID"))
            .toList();
    var today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
    var todayBills = paid.stream().filter(b -> b.paidAt.toLocalDate().equals(today)).toList();
    var revenue = paid.stream().map(b -> b.total).reduce(BigDecimal.ZERO, BigDecimal::add);
    Map<String, Integer> popular = new TreeMap<>();
    for (var b : paid) for (var i : b.order.items) popular.merge(i.name, i.quantity, Integer::sum);
    List<Map<String, Object>> trend = new ArrayList<>();
    for (int n = 6; n >= 0; n--) {
      var date = today.minusDays(n);
      var sum =
          paid.stream()
              .filter(b -> b.paidAt.toLocalDate().equals(date))
              .map(b -> b.total)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      trend.add(Map.of("date", date.toString(), "revenue", sum));
    }
    var top =
        popular.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(5)
            .map(e -> Map.of("name", e.getKey(), "quantity", e.getValue()))
            .toList();
    return Map.of(
        "revenue",
        revenue,
        "paidOrders",
        paid.size(),
        "todayRevenue",
        todayBills.stream().map(b -> b.total).reduce(BigDecimal.ZERO, BigDecimal::add),
        "todayOrders",
        todayBills.size(),
        "averageBill",
        paid.isEmpty()
            ? BigDecimal.ZERO
            : revenue.divide(BigDecimal.valueOf(paid.size()), 2, RoundingMode.HALF_UP),
        "trend",
        trend,
        "popular",
        top,
        "lowStock",
        menus.findAll().stream().filter(m -> m.stock < 10).toList());
  }
}
