package com.tablethyme;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Separate relational entities. Prices are DECIMAL, never floating point.
@Entity
@Table(name = "menu_items")
class MenuItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public String name;

  @Column(nullable = false)
  public String category;

  public String description;

  @Column(nullable = false, precision = 12, scale = 2)
  public BigDecimal price;

  public boolean vegetarian;
  public boolean available = true;
  public int stock;
  public String emoji;
}

@Entity
@Table(name = "restaurant_tables")
class DiningTable {
  @Id public Long id;
  public int seats;
  public String zone;
  public String status = "AVAILABLE";
}

@Entity
@Table(name = "orders")
class RestaurantOrder {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, unique = true, length = 64)
  public String requestKey;

  @ManyToOne public DiningTable diningTable;
  public String orderType;
  public String customer;

  @Column(length = 500)
  public String notes;

  public String status = "RECEIVED";
  public LocalDateTime createdAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
  public List<OrderItem> items = new ArrayList<>();
}

@Entity
@Table(name = "order_items")
class OrderItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(optional = false)
  @com.fasterxml.jackson.annotation.JsonIgnore
  public RestaurantOrder order;

  @ManyToOne(optional = false)
  public MenuItem menuItem;

  public String name;
  public int quantity;

  @Column(precision = 12, scale = 2, nullable = false)
  public BigDecimal unitPrice;
}

@Entity
@Table(name = "bills")
class Bill {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @OneToOne(optional = false)
  @JoinColumn(unique = true)
  public RestaurantOrder order;

  @Column(precision = 12, scale = 2)
  public BigDecimal subtotal;

  @Column(precision = 12, scale = 2)
  public BigDecimal discount;

  @Column(precision = 12, scale = 2)
  public BigDecimal taxAmount;

  @Column(precision = 12, scale = 2)
  public BigDecimal total;

  public String paymentStatus = "PENDING";
  public String paymentMethod;
  public LocalDateTime generatedAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));
  public LocalDateTime paidAt;
}
