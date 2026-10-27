package org.example.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {
    @Test
    void testConstructorPreservesAllOrderFields() {
        Order order = new Order(42, OrderSide.SELL, 105, 20, 12, 7);
        assertAll(
                () -> assertEquals(42, order.getId()),
                () -> assertEquals(OrderSide.SELL, order.getSide()),
                () -> assertEquals(105, order.getPrice()),
                () -> assertEquals(20, order.getOriginalVolume()),
                () -> assertEquals(12, order.getRemainingVolume()),
                () -> assertEquals(7, order.getPriority()),
                () -> assertFalse(order.isFilled()));
    }

    @ParameterizedTest(name = "price={0}, original_volume={1}, remaining_volume={2}")
    @CsvSource({"-1,10,10", "100,0,0", "100,-1,0", "100,10,-1", "100,10,11"})
    void testInvalidPriceOrVolumeIsRejected(long price, long original, long remaining) {
        assertThrows(IllegalArgumentException.class,
                () -> new Order(1, OrderSide.BUY, price, original, remaining, 1));
    }

    @Test
    void testNullSideIsRejected() {
        assertThrows(NullPointerException.class, () -> new Order(1, null, 100, 10, 10, 1));
    }

    @ParameterizedTest
    @CsvSource({"0,true", "1,false", "9223372036854775807,false"})
    void testOnlyZeroPriceIdentifiesMarketOrder(long price, boolean market) {
        Order order = new Order(1, OrderSide.BUY, price, 10, 10, 1);
        assertEquals(price, order.getPrice());
        assertEquals(market, order.isMarketOrder());
    }

    @Test
    void testZeroRemainingVolumeIsAlreadyFilled() {
        assertTrue(new Order(1, OrderSide.BUY, 100, 10, 0, 1).isFilled());
    }

    @Test
    void testPartialThenFullFillPreservesOriginalVolume() {
        Order order = new Order(1, OrderSide.BUY, 100, 10, 10, 1);
        order.fill(4);
        assertEquals(6, order.getRemainingVolume());
        assertFalse(order.isFilled());
        order.fill(6);
        assertEquals(0, order.getRemainingVolume());
        assertTrue(order.isFilled());
        assertEquals(10, order.getOriginalVolume());
        assertThrows(IllegalArgumentException.class, () -> order.fill(1));
    }

    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, -1, 0, 11, Long.MAX_VALUE})
    void testInvalidFillLeavesOrderUnchanged(long volume) {
        Order order = new Order(1, OrderSide.BUY, 100, 10, 10, 1);
        assertThrows(IllegalArgumentException.class, () -> order.fill(volume));
        assertEquals(10, order.getRemainingVolume());
    }

    @Test
    void testMaximumLongVolumeCanBeFilledWithoutOverflow() {
        Order order = new Order(Long.MAX_VALUE, OrderSide.BUY, Long.MAX_VALUE,
                Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE);
        order.fill(Long.MAX_VALUE);
        assertTrue(order.isFilled());
    }

    @Test
    void testToStringContainsIdentitySidePriceQuantitiesAndPriority() {
        Order order = new Order(42, OrderSide.SELL, 105, 20, 12, 7);
        assertEquals("Order[id=42, side=SELL, price=105, originalVolume=20, remainingVolume=12, priority=7]",
                order.toString());
        order.fill(2);
        assertTrue(order.toString().contains("remainingVolume=10"));
    }
}
