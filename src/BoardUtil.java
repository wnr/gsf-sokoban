import java.util.*;
import java.io.*;

public class BoardUtil {
    public static final String BOARD_FILE_NAME = "test.data";

    private static ArrayList<BoardState> cachedBoards;

    public static ArrayList<BoardState> getTestBoards() throws IOException {
        if (cachedBoards == null) {
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
            cachedBoards = boards;
        }
        return cachedBoards;
    }

    public static BoardState getTestBoard(int index) throws IOException {
        ArrayList<BoardState> boards = getTestBoards();
        if (index <= 0 || index > boards.size()) return null;
        return boards.get(index - 1);
    }
}
