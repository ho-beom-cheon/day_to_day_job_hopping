import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.math.*;
import java.io.*;

public class Lesson11 {
  public static void main(String[] args) throws Exception {
    try {
      try { Integer.parseInt("oops"); }
      catch (NumberFormatException cause) { throw new IllegalArgumentException("invalid quantity", cause); }
    } catch (IllegalArgumentException error) {
      if (!(error.getCause() instanceof NumberFormatException)) throw new AssertionError();
    }
    if (Integer.parseInt("12")!=12) throw new AssertionError();
    System.out.println("Lesson11 OK");
  }
}
