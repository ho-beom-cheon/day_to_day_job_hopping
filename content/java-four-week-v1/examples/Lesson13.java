import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.math.*;
import java.io.*;

public class Lesson13 {
  public static void main(String[] args) throws Exception {
    var clock = java.time.Clock.fixed(java.time.Instant.parse("2026-09-20T15:00:00Z"), java.time.ZoneId.of("Asia/Seoul"));
    if (!java.time.LocalDate.now(clock).equals(java.time.LocalDate.of(2026,9,21))) throw new AssertionError("Seoul date");
    for (int n : new int[]{1,100}) if (!(n>=1 && n<=100)) throw new AssertionError("valid boundary");
    for (int n : new int[]{0,101}) if (n>=1 && n<=100) throw new AssertionError("invalid boundary");
    System.out.println("Lesson13 OK");
  }
}
