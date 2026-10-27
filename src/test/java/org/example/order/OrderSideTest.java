package org.example.order;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderSideTest {
    @ParameterizedTest
    @EnumSource(OrderSide.class)
    void testSideStringIsTheReadableEnumName(OrderSide side) {
        assertEquals(side.name(), side.toString());
        assertEquals(side, OrderSide.valueOf(side.toString()));
    }
}
