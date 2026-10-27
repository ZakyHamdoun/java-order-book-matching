package org.example.order;

public enum OrderSide {
    BUY("BUY"),
    SELL("SELL");

    private final String sideString;

    OrderSide(String sideString) {
        this.sideString = sideString;
    }

    @Override
    public String toString() {
        return this.sideString;
    }
}
