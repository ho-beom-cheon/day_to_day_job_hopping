import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.math.*;
import java.io.*;

public class Lesson15 {
  public static void main(String[] args) throws Exception {
    class Counter {
      Map<String,Integer> count(List<String> inputs) {
        for (String s:inputs) if (s==null || s.isBlank()) throw new IllegalArgumentException("blank");
        var out=new HashMap<String,Integer>();
        for (String s:inputs) out.merge(s.strip(),1,Integer::sum);
        return Map.copyOf(out);
      }
    }
    var counter=new Counter();
    if (!counter.count(List.of(" a ","a")).equals(Map.of("a",2))) throw new AssertionError();
    try { counter.count(Arrays.asList("a",null)); throw new AssertionError(); }
    catch (IllegalArgumentException expected) { }
    if (!counter.count(List.of()).isEmpty()) throw new AssertionError();
    System.out.println("Lesson15 OK");
  }
}
