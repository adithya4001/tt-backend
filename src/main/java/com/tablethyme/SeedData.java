package com.tablethyme;

import java.math.BigDecimal;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class SeedData implements CommandLineRunner {
  final MenuRepository menu;
  final TableRepository tables;

  SeedData(MenuRepository m, TableRepository t) {
    menu = m;
    tables = t;
  }

  @Override
  @Transactional
  public void run(String... args) {
    if (tables.count() == 0)
      for (long i = 1; i <= 8; i++) {
        var t = new DiningTable();
        t.id = i;
        t.seats = i % 3 == 0 ? 6 : 4;
        t.zone = i <= 4 ? "Garden room" : "Main hall";
        tables.save(t);
      }
    if (menu.count() > 0) return;
    add(
        "Hyderabadi Biryani",
        "Mains",
        "Slow-cooked chicken, fragrant basmati & cooling raita",
        280,
        false,
        40,
        "🍛");
    add(
        "Paneer Butter Masala",
        "Mains",
        "Tandoori paneer in a velvety tomato gravy",
        240,
        true,
        35,
        "🥘");
    add(
        "Garden Veg Biryani",
        "Mains",
        "Seasonal vegetables, saffron rice & whole spices",
        220,
        true,
        30,
        "🍚");
    add(
        "Chicken 65",
        "Starters",
        "Crisp chicken, curry leaves & a little fire",
        210,
        false,
        25,
        "🍗");
    add(
        "Paneer Tikka",
        "Starters",
        "Charred cottage cheese with mint chutney",
        190,
        true,
        25,
        "🍢");
    add(
        "Crispy Corn",
        "Starters",
        "Golden corn tossed with pepper & fresh herbs",
        140,
        true,
        30,
        "🌽");
    add("Garlic Naan", "Breads", "Tandoor-baked bread with garlic butter", 60, true, 60, "🫓");
    add("Butter Roti", "Breads", "Whole wheat flatbread, finished with butter", 40, true, 60, "🥙");
    add(
        "Mango Lassi",
        "Beverages",
        "Alphonso mango, cultured yogurt & cardamom",
        90,
        true,
        40,
        "🥭");
    add(
        "Fresh Lime Soda",
        "Beverages",
        "Fresh lime, sparkling water & a refreshing finish",
        70,
        true,
        45,
        "🍋");
    add("Cold Coffee", "Beverages", "Cold-brewed coffee with a creamy swirl", 110, true, 25, "☕");
    add("Gulab Jamun", "Desserts", "Warm milk dumplings in rose-scented syrup", 80, true, 30, "🍮");
    add(
        "Chocolate Brownie",
        "Desserts",
        "Rich chocolate, a soft centre & vanilla ice cream",
        130,
        true,
        20,
        "🍫");
    add(
        "Double Ka Meetha",
        "Desserts",
        "A Hyderabadi classic with saffron & toasted nuts",
        100,
        true,
        8,
        "🍯");
  }

  void add(String n, String c, String d, int p, boolean v, int s, String e) {
    var m = new MenuItem();
    m.name = n;
    m.category = c;
    m.description = d;
    m.price = BigDecimal.valueOf(p);
    m.vegetarian = v;
    m.stock = s;
    m.emoji = e;
    menu.save(m);
  }
}
