import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.math.*;
import java.io.*;

public class Lesson01 {
  public static void main(String[] args) throws Exception {
    String a = new String("java");
    String b = new String("java");
    if (a == b || !a.equals(b)) throw new AssertionError("reference/value");
    if (!Objects.equals(null, null)) throw new AssertionError("null");
    int[] x = {1, 2}; int[] y = {1, 2};
    if (x.equals(y) || !Arrays.equals(x, y)) throw new AssertionError("array");
    System.out.println("Lesson01 OK");
  }
}
