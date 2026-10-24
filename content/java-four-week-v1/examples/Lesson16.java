import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.math.*;
import java.io.*;

public class Lesson16 {
  public static void main(String[] args) throws Exception {
    var count = new AtomicInteger();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first=pool.submit(() -> {for(int i=0;i<1000;i++)count.incrementAndGet();});
      var second=pool.submit(() -> {for(int i=0;i<1000;i++)count.incrementAndGet();});
      first.get(); second.get();
    }
    if (count.get()!=2000) throw new AssertionError("lost update");
    System.out.println("Lesson16 OK");
  }
}
