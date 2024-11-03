import java.util.concurrent.atomic.AtomicInteger;

public class Lamport {
    private AtomicInteger time;

    public Lamport() {
        this.time = new AtomicInteger(0);
    }


    public void tick() {
        time.incrementAndGet();
    }


    public void adjust(int timeStampt) {
        time.updateAndGet(current -> {
            System.out.println("Current: " + current + ", Received: " + timeStampt);
            return Math.max(current, timeStampt + 1);
        });
    }

    public int getTime() {
        return time.get();
    }

    public void setClock(int newTime) {
        time.set(newTime);
    }


}
