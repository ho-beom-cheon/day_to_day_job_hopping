import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.math.*;
import java.io.*;

public class Lesson14 {
  public static void main(String[] args) throws Exception {
    interface Lookup { Optional<String> find(String key); }
    Lookup fake = key -> Optional.ofNullable(Map.of("A","item").get(key));
    if (!fake.find("A").orElseThrow().equals("item")) throw new AssertionError();
    if (fake.find("missing").isPresent()) throw new AssertionError();
    Lookup broken = key -> { throw new IllegalStateException("temporary unavailable"); };
    try { broken.find("A"); throw new AssertionError(); }
    catch (IllegalStateException expected) { }
    System.out.println("Lesson14 OK");
  }
}
