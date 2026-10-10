package app.remotessh;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/** Separate from original-file downloads. New visible work precedes old queued work. */
final class ThumbnailLoader {
  private final AtomicLong sequence = new AtomicLong();
  private final ThreadPoolExecutor workers;
  ThumbnailLoader(int count) {
    workers = new ThreadPoolExecutor(count, count, 30, TimeUnit.SECONDS,
        new PriorityBlockingQueue<>(), r -> {Thread t=new Thread(r,"visible-image");t.setDaemon(true);return t;});
    workers.allowCoreThreadTimeOut(true);
  }
  final class Job extends FutureTask<Void> implements Comparable<Job>, BooleanSupplier {
    final long order;
    Job(long order, Consumer<BooleanSupplier> task) {
      super(() -> {task.accept(() -> !Thread.currentThread().isInterrupted());return null;});
      this.order=order;
    }
    public int compareTo(Job other) {return Long.compare(other.order,order);}
    public boolean getAsBoolean() {return !isCancelled();}
    void discard() {cancel(true);workers.remove(this);}
  }
  Job submit(Consumer<BooleanSupplier> task) {
    Job job=new Job(sequence.incrementAndGet(),task);workers.execute(job);return job;
  }
  void close() {workers.shutdownNow();}
}
