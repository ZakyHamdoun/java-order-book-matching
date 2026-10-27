package org.example.trade;

public record Trade(
    long buyOrderId,
    long sellOrderId,
    long price,
    long volume
) {}
