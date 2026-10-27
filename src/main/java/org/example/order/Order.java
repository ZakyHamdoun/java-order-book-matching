package org.example.order;

import java.util.Objects;

public final class Order {
    private final long id;
    private final OrderSide side;
    private final long price;
    private final long originalVolume;
    private long remainingVolume;
    private final long priority;

    public Order(
            long id,
            OrderSide side,
            long price,
            long originalVolume,
            long remainingVolume,
            long priority
    ) {
        if (price < 0 || originalVolume <= 0)
            throw new IllegalArgumentException("price must be >= 0 and originalVolume must be > 0!");
        if (remainingVolume < 0 || remainingVolume > originalVolume)
            throw new IllegalArgumentException("remainingVolume must be between 0 and originalVolume");

        this.id = id;
        this.side = Objects.requireNonNull(side);
        this.price = price;
        this.originalVolume = originalVolume;
        this.remainingVolume = remainingVolume;
        this.priority = priority;
    }

    public void fill(long fillingVolume) {
        if (fillingVolume <= 0)
            throw new IllegalArgumentException("fillingVolume must be > 0!");

        if (fillingVolume > remainingVolume)
            throw new IllegalArgumentException("fillingVolume must be <= remainingVolume!");

        remainingVolume -= fillingVolume;
    }

    public boolean isMarketOrder() { return this.price == 0; }
    public boolean isFilled() { return this.remainingVolume == 0; }

    // Getters.
    public long getId() { return this.id; }
    public OrderSide getSide() { return this.side; }
    public long getPrice() { return this.price; }
    public long getOriginalVolume(){ return this.originalVolume; }
    public long getRemainingVolume() { return this.remainingVolume; }
    public long getPriority() { return this.priority; }

    // toString() method (display Order).
    @Override
    public String toString() {

        return "Order[" +
                "id=" + id + ", " +
                "side=" + side.toString() + ", " +
                "price=" + price + ", " +
                "originalVolume=" + originalVolume + ", " +
                "remainingVolume=" + remainingVolume + ", " +
                "priority=" + priority +
                "]";
    }
}
