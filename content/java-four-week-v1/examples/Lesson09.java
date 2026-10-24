import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.math.*;
import java.io.*;

public class Lesson09 {
  public static void main(String[] args) throws Exception {
    var result = List.of(1,2,3,4).stream().filter(n -> n%2==0).map(n -> n*10).toList();
    if (!result.equals(List.of(20,40))) throw new AssertionError();
    int sum = List.of(1,2,3).stream().reduce(0,Integer::sum);
    if (sum!=6) throw new AssertionError();
    try { result.add(60); throw new AssertionError(); }
    catch (UnsupportedOperationException expected) { }
    System.out.println("Lesson09 OK");
  }
}
