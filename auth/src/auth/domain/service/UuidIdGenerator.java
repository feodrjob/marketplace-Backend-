package auth.domain.service;

import java.util.UUID;

public final class UuidIdGenerator implements IdGenerator {

    @Override
    public UUID nextId() {
        return UUID.randomUUID();
    }
}
