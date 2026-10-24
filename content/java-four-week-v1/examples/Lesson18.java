import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.math.*;
import java.io.*;

public class Lesson18 {
  public static void main(String[] args) throws Exception {
    var counts=new ConcurrentHashMap<String,Integer>();
    try(var pool=Executors.newFixedThreadPool(2)) {
      var tasks=new ArrayList<Future<?>>();
      for(int t=0;t<2;t++) tasks.add(pool.submit(() -> {for(int i=0;i<1000;i++)counts.merge("item",1,Integer::sum);}));
      for(var task:tasks)task.get();
    }
    if(counts.get("item")!=2000)throw new AssertionError();
    if(counts.putIfAbsent("item",99)!=2000)throw new AssertionError();
    System.out.println("Lesson18 OK");
  }
}
