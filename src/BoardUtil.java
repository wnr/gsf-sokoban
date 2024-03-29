import java.util.*;
import java.io.*;

public class BoardUtil {
    public static final String BOARD_FILE_NAME = "test.data";

    public static Random generator = new Random();

    private static ArrayList<ArrayList<String>> cachedBoards;

    public static int[] shuffleListToArray(int[] list, int movesCount, int shuffle) {
        int[] array = new int[movesCount];

        System.arraycopy(list,0, array, 0, movesCount);

        if(array.length < 2) {
            return array;
        }

        while(shuffle > 0) {
            shuffle--;

            int i1 = generator.nextInt(array.length);
            int i2 = i1;

            while((i2 = generator.nextInt(array.length)) == i1);

            int tmp = array[i1];
            array[i1] = array[i2];
            array[i2] = tmp;

        }

        return array;
    }

    public static ArrayList<ArrayList<String>> getTestBoards() throws IOException {
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
            cachedBoards = new ArrayList<ArrayList<String>>();

            ArrayList<String> lines = new ArrayList<String>();
            String line;
            in.readLine(); // Skip first line
            while ((line = in.readLine()) != null) {
                if (line.startsWith(";")) {
                    cachedBoards.add(lines);
                    lines = new ArrayList<String>();
                } else {
                    lines.add(line);
                }
            }
            cachedBoards.add(lines);
        }
        return cachedBoards;
    }

    public static BoardState getTestBoard(int index) throws IOException {
        ArrayList<ArrayList<String>> boards = getTestBoards();
        if (index <= 0 || index > boards.size()) return null;
        return new BoardState(boards.get(index - 1));
    }

    public static BoardStateBackwards getTestBoardBackwards(int index) throws IOException {
        ArrayList<ArrayList<String>> boards = getTestBoards();
        if (index <= 0 || index > boards.size()) return null;
        return new BoardStateBackwards(boards.get(index - 1));
    }

    public static BoardStateLight getTestBoardLight(int index) throws IOException {
        ArrayList<ArrayList<String>> boards = getTestBoards();
        if (index <= 0 || index > boards.size()) return null;
        return new BoardStateLight(boards.get(index - 1));
    }
}
