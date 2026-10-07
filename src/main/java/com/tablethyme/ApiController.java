package com.tablethyme;

import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiController {
  final RestaurantService service;
  final AssistantService assistant;

  ApiController(RestaurantService s, AssistantService a) {
    service = s;
    assistant = a;
  }

  @GetMapping("/health")
  public Object health() {
    return Map.of("status", "UP", "database", "MySQL", "service", "Table & Thyme");
  }

  @GetMapping("/menu")
  public Object menu() {
    return service.menu();
  }

  @PostMapping("/menu")
  public Object add(@Valid @RequestBody Dtos.MenuInput v) {
    return service.saveMenu(null, v);
  }

  @PutMapping("/menu/{id}")
  public Object edit(@PathVariable Long id, @Valid @RequestBody Dtos.MenuInput v) {
    return service.saveMenu(id, v);
  }

  @GetMapping("/tables")
  public Object tables() {
    return service.tableList();
  }

  @GetMapping("/orders")
  public Object orders() {
    return service.orderList();
  }

  @GetMapping("/orders/active")
  public Object active() {
    return service.orderList().stream()
        .filter(o -> !Set.of("COMPLETED", "CANCELLED").contains(o.status))
        .toList();
  }

  @PostMapping("/orders")
  public Object place(@Valid @RequestBody Dtos.PlaceOrder v) {
    return service.place(v);
  }

  @PatchMapping("/orders/{id}/status")
  public Object status(@PathVariable Long id, @Valid @RequestBody Dtos.Status v) {
    return service.status(id, v.status());
  }

  @GetMapping("/bills")
  public Object bills() {
    return service.billList();
  }

  @PostMapping("/bills/generate/{id}")
  public Object bill(@PathVariable Long id, @Valid @RequestBody Dtos.Checkout v) {
    return service.generate(id, v.discountPercent());
  }

  @PostMapping("/bills/{id}/pay")
  public Object pay(@PathVariable Long id, @Valid @RequestBody Dtos.Payment v) {
    return service.pay(id, v.method());
  }

  @GetMapping("/analytics/sales-summary")
  public Object analytics() {
    return service.analytics();
  }

  @PostMapping("/assistant")
  public Object assistant(@Valid @RequestBody Dtos.AssistantInput v) {
    return assistant.reply(v);
  }
}
