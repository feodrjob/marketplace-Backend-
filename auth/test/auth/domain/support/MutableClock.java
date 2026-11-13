package auth.domain.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

//Часы, которые тест сдвигает сам
public final class MutableClock extends Clock {

    private Instant current;

    public MutableClock(Instant current) {
        this.current = current;
    }

    public void advance(Duration duration) {
        current = current.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return current;
    }
}
