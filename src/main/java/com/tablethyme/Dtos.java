package com.tablethyme;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public class Dtos {
  public record Line(@NotNull Long menuItemId, @Min(1) @Max(99) int quantity) {}

  public record PlaceOrder(
      @NotBlank @Size(max = 64) String requestKey,
      Long tableId,
      @NotBlank @Pattern(regexp = "DINE_IN|TAKEAWAY") String orderType,
      @Size(max = 80) String customer,
      @Size(max = 500) String notes,
      @NotEmpty @Size(max = 50) List<@Valid Line> items) {}

  public record Status(@NotBlank String status) {}

  public record Checkout(@NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal discountPercent) {}

  public record Payment(@NotBlank @Pattern(regexp = "CASH|UPI|CARD") String method) {}

  public record MenuInput(
      @NotBlank @Size(max = 80) String name,
      @NotBlank @Pattern(regexp = "Starters|Mains|Breads|Beverages|Desserts") String category,
      @Size(max = 250) String description,
      @NotNull @DecimalMin("1") @DecimalMax("100000") BigDecimal price,
      boolean vegetarian,
      boolean available,
      @Min(0) @Max(100000) int stock,
      @NotBlank @Size(max = 12) String emoji) {}

  public record AssistantInput(@Size(max = 1000) String text, @Size(max = 50) List<Long> itemIds) {}
}
