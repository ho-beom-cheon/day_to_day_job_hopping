import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.math.*;
import java.io.*;

public class Lesson08 {
  public static void main(String[] args) throws Exception {
    List<Integer> ints = List.of(1,2,3);
    List<? extends Number> producer = ints;
    double sum = 0; for (Number n : producer) sum += n.doubleValue();
    List<? super Integer> consumer = new ArrayList<Number>();
    consumer.add(4); Object value = consumer.get(0);
    if (sum!=6 || !value.equals(4)) throw new AssertionError();
    System.out.println("Lesson08 OK");
  }
}
