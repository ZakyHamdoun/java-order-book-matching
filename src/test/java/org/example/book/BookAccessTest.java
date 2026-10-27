package org.example.book;

import org.example.order.Order;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Member;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BookAccessTest {
    @Test
    void testLevelMutationsAndNodeCreationRemainPackagePrivate() throws ReflectiveOperationException {
        assertPackagePrivate(PriceLevel.class.getDeclaredMethod("addOrder", Order.class));
        assertPackagePrivate(PriceLevel.class.getDeclaredMethod("popOrder"));
        assertPackagePrivate(PriceLevel.class.getDeclaredMethod("removeOrder", OrderNode.class));
        assertPackagePrivate(PriceLevel.class.getDeclaredMethod("fillOrder", OrderNode.class, long.class));
        assertPackagePrivate(OrderNode.class.getDeclaredConstructor(
                Order.class, PriceLevel.class, OrderNode.class, OrderNode.class, boolean.class));
        assertPackagePrivate(OrderNode.class.getDeclaredMethod("unlink"));
        assertPackagePrivate(OrderNode.class.getDeclaredMethod("setPrev", OrderNode.class));
        assertPackagePrivate(OrderNode.class.getDeclaredMethod("setNext", OrderNode.class));
    }

    private void assertPackagePrivate(Member member) {
        int accessModifiers = Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE;
        assertEquals(0, member.getModifiers() & accessModifiers, member.toString());
    }
}
