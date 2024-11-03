import java.util.concurrent.atomic.AtomicInteger;

public class LamportClock {
    private AtomicInteger time;

    public LamportClock() {
        this.time = new AtomicInteger(0);
    }

    public int getTime() {
        return time.get();
    }

    public void update(int receivedTime) {
        time.set(Math.max(time.get(), receivedTime) + 1);
    }

    public int tick() {
        return time.incrementAndGet();
    }
}