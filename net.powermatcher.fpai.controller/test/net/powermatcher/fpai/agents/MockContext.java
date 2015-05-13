package net.powermatcher.fpai.agents;

import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.measure.Measurable;
import javax.measure.quantity.Duration;
import javax.measure.unit.SI;

import net.powermatcher.api.data.PricePoint;

import org.flexiblepower.context.FlexiblePowerContext;

public class MockContext
                        implements FlexiblePowerContext {

    private long now;

    public MockContext(Date initialDate) {
        now = initialDate.getTime();
    }

    public MockContext(long initialDate) {
        now = initialDate;
    }

    @Override
    public long currentTimeMillis() {
        return now;
    }

    public void jump(long ms) {
        now += ms;
    }

    @Override
    public Date currentTime() {
        return new Date(now);
    }

    private Runnable task;
    private long updateRate;
    protected MockFuture mockFuture;
    private long scheduleTime;

    public class MockFuture
                           implements ScheduledFuture<String> {

        private boolean cancelled = false;

        /**
         * {@inheritDoc}
         */
        @Override
        public long getDelay(TimeUnit arg0) {
            return 0;
        }

        /**
         * Compares this object with the specified object for order. Returns a negative integer, zero, or a positive
         * integer as this object is less than, equal to, or greater than the specified object.
         *
         * This mock method will always return 0. This method had to be overridden, but it is never used.
         *
         * @param that
         *            The {@link PricePoint} instance you want to compare with this one.
         *
         * @return a negative integer, zero, or a positive integer as this object is less than, equal to, or greater
         *         than the specified object.
         */
        @Override
        public int compareTo(Delayed that) {
            return 0;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object obj) {
            return super.equals(obj);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return super.hashCode();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean cancel(boolean arg0) {
            cancelled = true;
            return cancelled;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String get() throws InterruptedException, ExecutionException {
            return "mock get()";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String get(long arg0, TimeUnit arg1) throws InterruptedException, ExecutionException, TimeoutException {
            return "mock get(long, TimeUnit)";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isDone() {
            return task != null;
        }

    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, Measurable<Duration> delay) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, Measurable<Duration> delay) {
        task = command;
        scheduleTime = currentTimeMillis() + delay.longValue(SI.MILLI(SI.SECOND));
        mockFuture = new MockFuture();
        return mockFuture;
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
                                                  Measurable<Duration> initialDelay,
                                                  Measurable<Duration> period) {
        task = command;
        scheduleTime = currentTimeMillis() + initialDelay.longValue(SI.MILLI(SI.SECOND));
        updateRate = period.longValue(SI.SECOND);
        mockFuture = new MockFuture();
        return mockFuture;
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command,
                                                     Measurable<Duration> initialDelay,
                                                     Measurable<Duration> delay) {
        return scheduleAtFixedRate(command, initialDelay, delay);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<?> submit(Runnable task) {
        this.task = task;
        scheduleTime = currentTimeMillis();
        mockFuture = new MockFuture();
        return mockFuture;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the current value of updateRate.
     */
    public int getUpdateRate() {
        return (int) updateRate;
    }

    /**
     * @return the current value of mockFuture.
     */
    public MockFuture getMockFuture() {
        return mockFuture;
    }

    public long getScheduleTime() {
        return scheduleTime;
    }

    public void doTaskOnce() {
        if (task != null) {
            task.run();
        }
    }

    public void doTaskIfTimeIsRight() {
        if (task != null && scheduleTime <= now) {
            task.run();
            if (updateRate > 0) {
                scheduleTime += updateRate;
            } else {
                task = null;
            }
        }
    }
}
