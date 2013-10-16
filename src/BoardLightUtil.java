import java.util.*;
import java.io.*;

public class BoardLightUtil {
    public static final String BOARD_FILE_NAME = "test.data";

    private static ArrayList<BoardStateLight> cachedBoards;

    public static ArrayList<BoardStateLight> getTestBoards() throws IOException {
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
            ArrayList<BoardStateLight> boards = new ArrayList<BoardStateLight>();

            ArrayList<String> lines = new ArrayList<String>();
            String line;
            in.readLine(); // Skip first line
            while ((line = in.readLine()) != null) {
                if (line.startsWith(";")) {
                    boards.add(new BoardStateLight(lines));
                    lines = new ArrayList<String>();
                } else {
                    lines.add(line);
                }
            }
            boards.add(new BoardStateLight(lines));
            cachedBoards = boards;
        }
        return cachedBoards;
    }

    public static BoardStateLight getTestBoard(int index) throws IOException {
        ArrayList<BoardStateLight> boards = getTestBoards();
        if (index <= 0 || index > boards.size()) return null;
        return boards.get(index - 1);
    }
}
