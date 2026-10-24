import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.math.*;
import java.io.*;

public class Lesson12 {
  public static void main(String[] args) throws Exception {
    var log = new ArrayList<String>();
    class Resource implements AutoCloseable {
      final String name; Resource(String n){name=n;}
      public void close(){log.add(name);}
    }
    try (var first = new Resource("first"); var second = new Resource("second")) { log.add("body"); }
    if (!log.equals(List.of("body","second","first"))) throw new AssertionError();
    System.out.println("Lesson12 OK");
  }
}
