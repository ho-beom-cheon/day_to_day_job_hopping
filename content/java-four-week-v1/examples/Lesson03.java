import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.math.*;
import java.io.*;

public class Lesson03 {
  public static void main(String[] args) throws Exception {
    record Team(List<String> members) {
      Team { members = List.copyOf(members); }
    }
    var input = new ArrayList<>(List.of("min"));
    var team = new Team(input); input.add("su");
    if (team.members().size()!=1) throw new AssertionError("alias");
    try { team.members().add("x"); throw new AssertionError(); }
    catch (UnsupportedOperationException expected) { }
    System.out.println("Lesson03 OK");
  }
}
