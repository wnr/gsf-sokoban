import java.io.*;
import java.util.*;

public class Main {

    public static void main(String[] args) throws IOException {
        ArrayList<String> board = new ArrayList<String>();

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

        String line;
        while((line = in.readLine()) != null) {
            board.add(line);
        }

        // Access
        //char = board.get(row).charAt(col);

        System.out.println("U R R U");
    }
}