package org.example.support;

import org.example.matching.policy.FifoMatchingPolicy;
import org.example.matching.policy.MatchingPolicy;
import org.example.matching.policy.ProRataMatchingPolicy;

public enum MatchingImplementation {
    FIFO, PRO_RATA;

    public MatchingPolicy policy() {
        return this == FIFO ? new FifoMatchingPolicy() : new ProRataMatchingPolicy();
    }
}
