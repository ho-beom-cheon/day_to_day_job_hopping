import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.math.*;
import java.io.*;

public class Lesson19 {
  public static void main(String[] args) throws Exception {
    var retained=new ArrayList<byte[]>();
    retained.add(new byte[1024]); retained.add(new byte[1024]);
    if(retained.size()!=2)throw new AssertionError();
    retained.clear();
    if(!retained.isEmpty())throw new AssertionError();
    long start=System.nanoTime();
    long total=0;for(int i=0;i<1000;i++)total+=i;
    long elapsed=System.nanoTime()-start;
    if(total!=499500 || elapsed<0)throw new AssertionError();
    System.out.println("Observed elapsed ns: "+elapsed);
    System.out.println("Lesson19 OK");
  }
}
