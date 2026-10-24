import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.math.*;
import java.io.*;

public class Lesson06 {
  public static void main(String[] args) throws Exception {
    var values = new ArrayList<>(List.of(1, 2, 1));
    values.remove(Integer.valueOf(1));
    if (!values.equals(List.of(2, 1))) throw new AssertionError();
    values.remove(1);
    if (!values.equals(List.of(2))) throw new AssertionError();
    values.add(3); values.removeIf(n -> n % 2 == 0);
    if (!values.equals(List.of(3))) throw new AssertionError();
    System.out.println("Lesson06 OK");
  }
}
