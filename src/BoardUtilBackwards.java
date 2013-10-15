import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class BoardUtilBackwards {
    public static final String BOARD_FILE_NAME = "test.data";

    private static ArrayList<BoardStateBackwards> cachedBoards;

    public static ArrayList<BoardStateBackwards> getTestBoards() throws IOException {
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
            ArrayList<BoardStateBackwards> boards = new ArrayList<BoardStateBackwards>();

            ArrayList<String> lines = new ArrayList<String>();
            String line;
            in.readLine(); // Skip first line
            while ((line = in.readLine()) != null) {
                if (line.startsWith(";")) {
                    boards.add(new BoardStateBackwards(lines));
                    lines = new ArrayList<String>();
                } else {
                    lines.add(line);
                }
            }
            boards.add(new BoardStateBackwards(lines));
            cachedBoards = boards;
        }
        return cachedBoards;
    }

    public static BoardStateBackwards getTestBoard(int index) throws IOException {
        ArrayList<BoardStateBackwards> boards = getTestBoards();
        if (index <= 0 || index > boards.size()) return null;
        return boards.get(index - 1);
    }
}
