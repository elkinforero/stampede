package com.torodb.poc.backend.mocks;

import javax.annotation.Nonnull;

/**
 *
 */
public interface NamedToroIndex extends ToroIndex {

    @Nonnull
    public String getName();
    
}
