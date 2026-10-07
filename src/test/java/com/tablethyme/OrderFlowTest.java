package com.tablethyme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:flow;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "app.gemini-key="
    })
@AutoConfigureMockMvc
@TestExecutionListeners(listeners = DependencyInjectionTestExecutionListener.class)
class OrderFlowTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  final HttpHarness http = new HttpHarness();

  class HttpHarness {
    JsonNode getForObject(String path, Class<JsonNode> type) {
      try {
        return mapper.readTree(
            mvc.perform(get(path)).andReturn().getResponse().getContentAsString());
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    JsonNode postForObject(String path, Object body, Class<JsonNode> type) {
      return postForEntity(path, body, type).getBody();
    }

    ResponseEntity<JsonNode> postForEntity(String path, Object body, Class<JsonNode> type) {
      try {
        var response =
            mvc.perform(
                    post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andReturn()
                .getResponse();
        return ResponseEntity.status(response.getStatus())
            .body(mapper.readTree(response.getContentAsString()));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Autowired RestaurantService service;
  @Autowired MenuRepository menus;
  @Autowired TableRepository tables;
  @Autowired OrderRepository orders;

  @Test
  void completeFlowHasAccurateMoneySnapshotsAndIdempotency() {
    JsonNode menu = http.getForObject("/api/menu", JsonNode.class);
    long id = menu.get(0).path("id").asLong();
    int stock = menu.get(0).path("stock").asInt();
    String key = UUID.randomUUID().toString();
    var payload =
        Map.of(
            "requestKey",
            key,
            "orderType",
            "TAKEAWAY",
            "items",
            List.of(Map.of("menuItemId", id, "quantity", 2)));
    JsonNode o = http.postForObject("/api/orders", payload, JsonNode.class);
    long oid = o.path("id").asLong();
    assertThat(oid).isPositive();
    assertThat(http.postForObject("/api/orders", payload, JsonNode.class).path("id").asLong())
        .isEqualTo(oid);
    assertThat(
            http.postForEntity(
                    "/api/bills/generate/" + oid, Map.of("discountPercent", 10), JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    var original = menus.findById(id).orElseThrow();
    assertThat(original.stock).isEqualTo(stock - 2);
    service.saveMenu(
        id,
        new Dtos.MenuInput(
            original.name,
            original.category,
            original.description,
            new java.math.BigDecimal("999"),
            original.vegetarian,
            true,
            original.stock,
            original.emoji));
    service.status(oid, "PREPARING");
    service.status(oid, "SERVED");
    JsonNode bill =
        http.postForObject(
            "/api/bills/generate/" + oid, Map.of("discountPercent", 10), JsonNode.class);
    assertThat(bill.path("subtotal").decimalValue()).isEqualByComparingTo("560.00");
    assertThat(bill.path("discount").decimalValue()).isEqualByComparingTo("56.00");
    assertThat(bill.path("taxAmount").decimalValue()).isEqualByComparingTo("25.20");
    assertThat(bill.path("total").decimalValue()).isEqualByComparingTo("529.20");
    long bid = bill.path("id").asLong();
    assertThat(
            http.postForObject(
                    "/api/bills/generate/" + oid, Map.of("discountPercent", 0), JsonNode.class)
                .path("id")
                .asLong())
        .isEqualTo(bid);
    http.postForObject("/api/bills/" + bid + "/pay", Map.of("method", "CASH"), JsonNode.class);
    http.postForObject("/api/bills/" + bid + "/pay", Map.of("method", "CARD"), JsonNode.class);
    var paid = http.getForObject("/api/bills", JsonNode.class).get(0);
    assertThat(paid.path("paymentMethod").asText()).isEqualTo("CASH");
    assertThat(paid.path("order").path("status").asText()).isEqualTo("COMPLETED");
    assertThat(
            http.getForObject("/api/analytics/sales-summary", JsonNode.class)
                .path("paidOrders")
                .asInt())
        .isEqualTo(1);
    service.saveMenu(
        id,
        new Dtos.MenuInput(
            original.name,
            original.category,
            original.description,
            new java.math.BigDecimal("280"),
            original.vegetarian,
            true,
            original.stock,
            original.emoji));
  }

  @Test
  void invalidOrderRollsBackTableAndStockAndCancellationRestoresInventory() {
    var menu = menus.findAll();
    var m = menu.get(1);
    int before = m.stock;
    var invalid =
        Map.of(
            "requestKey",
            UUID.randomUUID().toString(),
            "orderType",
            "DINE_IN",
            "tableId",
            1,
            "items",
            List.of(Map.of("menuItemId", m.id, "quantity", 99)));
    assertThat(http.postForEntity("/api/orders", invalid, JsonNode.class).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(tables.findById(1L).orElseThrow().status).isEqualTo("AVAILABLE");
    assertThat(menus.findById(m.id).orElseThrow().stock).isEqualTo(before);
    var valid =
        Map.of(
            "requestKey",
            UUID.randomUUID().toString(),
            "orderType",
            "DINE_IN",
            "tableId",
            1,
            "items",
            List.of(Map.of("menuItemId", m.id, "quantity", 1)));
    var o = http.postForObject("/api/orders", valid, JsonNode.class);
    long id = o.path("id").asLong();
    assertThat(tables.findById(1L).orElseThrow().status).isEqualTo("OCCUPIED");
    var another = new HashMap<String, Object>(valid);
    another.put("requestKey", UUID.randomUUID().toString());
    assertThat(http.postForEntity("/api/orders", another, JsonNode.class).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    service.status(id, "CANCELLED");
    service.status(id, "CANCELLED");
    assertThat(tables.findById(1L).orElseThrow().status).isEqualTo("AVAILABLE");
    assertThat(menus.findById(m.id).orElseThrow().stock).isEqualTo(before);
    assertThat(
            http.postForEntity(
                    "/api/orders",
                    Map.of(
                        "requestKey",
                        "bad",
                        "orderType",
                        "TAKEAWAY",
                        "items",
                        List.of(Map.of("menuItemId", m.id, "quantity", -1))),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void localAssistantParsesMenuNamesWithoutCreatingOrders() {
    long before = orders.count();
    var reply =
        http.postForObject(
            "/api/assistant",
            Map.of("text", "two Hyderabadi Biryani and one Mango Lassi", "itemIds", List.of()),
            JsonNode.class);
    assertThat(reply.path("mode").asText()).isEqualTo("LOCAL_RULES");
    assertThat(reply.path("items").size()).isEqualTo(2);
    assertThat(orders.count()).isEqualTo(before);
  }
}
