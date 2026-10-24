import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.math.*;
import java.io.*;

public class Lesson07 {
  public static void main(String[] args) throws Exception {
    Map<String,Integer> counts = new LinkedHashMap<>();
    for (String word : List.of("red","blue","red")) counts.merge(word,1,Integer::sum);
    if (counts.get("red")!=2 || counts.size()!=2) throw new AssertionError();
    if (!new ArrayList<>(counts.keySet()).equals(List.of("red","blue"))) throw new AssertionError();
    if (new HashSet<>(List.of("a","a","b")).size()!=2) throw new AssertionError();
    System.out.println("Lesson07 OK");
  }
}
