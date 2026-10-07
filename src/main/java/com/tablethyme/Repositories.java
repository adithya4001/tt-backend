package com.tablethyme;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface MenuRepository extends JpaRepository<MenuItem, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from MenuItem m where m.id=:id")
  Optional<MenuItem> lock(@Param("id") Long id);
}

interface TableRepository extends JpaRepository<DiningTable, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from DiningTable t where t.id=:id")
  Optional<DiningTable> lock(@Param("id") Long id);
}

interface OrderRepository extends JpaRepository<RestaurantOrder, Long> {
  Optional<RestaurantOrder> findByRequestKey(String key);

  List<RestaurantOrder> findAllByOrderByIdDesc();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from RestaurantOrder o where o.id=:id")
  Optional<RestaurantOrder> lock(@Param("id") Long id);
}

interface BillRepository extends JpaRepository<Bill, Long> {
  Optional<Bill> findByOrderId(Long orderId);

  List<Bill> findAllByOrderByIdDesc();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from Bill b where b.id=:id")
  Optional<Bill> lock(@Param("id") Long id);
}
