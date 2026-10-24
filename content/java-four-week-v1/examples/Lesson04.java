import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.math.*;
import java.io.*;

public class Lesson04 {
  public static void main(String[] args) throws Exception {
    var a = new BigDecimal("1.0"); var b = new BigDecimal("1.00");
    if (a.equals(b) || a.compareTo(b)!=0) throw new AssertionError();
    var third = BigDecimal.ONE.divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);
    if (!third.equals(new BigDecimal("0.33"))) throw new AssertionError();
    var sum = new BigDecimal("0.1").add(new BigDecimal("0.2"));
    if (sum.compareTo(new BigDecimal("0.3"))!=0) throw new AssertionError();
    System.out.println("Lesson04 OK");
  }
}
