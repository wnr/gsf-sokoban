import java.util.*;
import java.io.*;

public class BoardUtil {
    public static final String BOARD_FILE_NAME = "test.data";

    public static ArrayList<BoardState> readTestBoards() throws IOException {
        BufferedReader in;
        try {
            in = new BufferedReader(new FileReader(BOARD_FILE_NAME));
        } catch (FileNotFoundException e) {
            // Running from terminal when using IntelliJ project structure
            try {
                in = new BufferedReader(new FileReader("../../../" + BOARD_FILE_NAME));
            } catch (FileNotFoundException f) {
                // Last trying eclipse project structure
                in = new BufferedReader(new FileReader("../" + BOARD_FILE_NAME));
            }
        }
        ArrayList<BoardState> boards = new ArrayList<BoardState>();

        ArrayList<String> lines = new ArrayList<String>();
        String line;
        in.readLine(); // Skip first line
        while ((line = in.readLine()) != null) {
            if (line.startsWith(";")) {
                boards.add(new BoardState(lines));
                lines = new ArrayList<String>();
            } else {
                lines.add(line);
            }
        }
        boards.add(new BoardState(lines));
        return boards;
    }
}
