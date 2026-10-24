import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.math.*;
import java.io.*;

public class Lesson17 {
  public static void main(String[] args) throws Exception {
    try (var pool=Executors.newFixedThreadPool(2)) {
      Future<Integer> success=pool.submit(() -> 6*7);
      if (success.get()!=42) throw new AssertionError();
      Future<Integer> failure=pool.submit(() -> {throw new IllegalStateException("planned failure");});
      try { failure.get(); throw new AssertionError(); }
      catch (ExecutionException error) { if (!(error.getCause() instanceof IllegalStateException)) throw new AssertionError(); }
    }
    System.out.println("Lesson17 OK");
  }
}
