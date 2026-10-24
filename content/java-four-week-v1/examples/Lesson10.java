import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.math.*;
import java.io.*;

public class Lesson10 {
  public static void main(String[] args) throws Exception {
    record Score(String name,int value) {}
    var source = List.of(new Score("b",90),new Score("a",90),new Score("c",80));
    var sorted = source.stream().sorted(Comparator.comparingInt(Score::value).reversed().thenComparing(Score::name)).toList();
    if (!sorted.get(0).name().equals("a") || !source.get(0).name().equals("b")) throw new AssertionError();
    if (Integer.compare(Integer.MIN_VALUE,Integer.MAX_VALUE)>=0) throw new AssertionError();
    System.out.println("Lesson10 OK");
  }
}
