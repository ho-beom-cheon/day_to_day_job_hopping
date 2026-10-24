import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.math.*;
import java.io.*;

public class Lesson05 {
  public static void main(String[] args) throws Exception {
    record ProductCode(String value) {
      ProductCode {
        Objects.requireNonNull(value);
        value = value.strip();
        if (value.isEmpty()) throw new IllegalArgumentException("blank code");
      }
    }
    if (!new ProductCode(" A1 ").equals(new ProductCode("A1"))) throw new AssertionError();
    try { new ProductCode("  "); throw new AssertionError(); }
    catch (IllegalArgumentException expected) { }
    System.out.println("Lesson05 OK");
  }
}
