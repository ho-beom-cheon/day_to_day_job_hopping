import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.math.*;
import java.io.*;

public class Lesson02 {
  public static void main(String[] args) throws Exception {
    record Key(String tenant, String id) {}
    Map<Key, String> map = new HashMap<>();
    map.put(new Key("team", "1"), "saved");
    if (!"saved".equals(map.get(new Key("team", "1")))) throw new AssertionError();
    if (new HashSet<>(List.of(new Key("t","1"), new Key("t","1"))).size()!=1) throw new AssertionError();
    System.out.println("Lesson02 OK");
  }
}
