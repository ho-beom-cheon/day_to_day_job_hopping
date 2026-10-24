import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.math.*;
import java.io.*;

public class Lesson20 {
  public static void main(String[] args) throws Exception {
    record Line(String code,int quantity) {
      Line { Objects.requireNonNull(code);code=code.strip();if(code.isEmpty() || quantity<0)throw new IllegalArgumentException("invalid line"); }
    }
    var input=List.of(new Line(" A ",2),new Line("A",3),new Line("B",1));
    var totals=new TreeMap<String,Integer>();
    for(var line:input)totals.merge(line.code(),line.quantity(),Math::addExact);
    Map<String,Integer> result=Collections.unmodifiableMap(new TreeMap<>(totals));
    if(!result.equals(Map.of("A",5,"B",1)))throw new AssertionError();
    try { Math.addExact(Integer.MAX_VALUE,1);throw new AssertionError(); }
    catch(ArithmeticException expected) { }
    System.out.println("Lesson20 OK");
  }
}
