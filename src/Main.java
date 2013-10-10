import java.io.*;
import java.lang.reflect.Array;
import java.util.*;

public class Main {

    public static boolean debug            = false;
    public static boolean printPath = false;

    public static void main(String[] args) throws IOException {
        BoardState board = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].contains("debug") || args[i].contains("-d")) {
                Main.debug = true;
                args = removeArrayElement(args, i);
                break;
            }
        }
        for (int i = 0; i < args.length; i++) {
            if (args[i].contains("print") || args[i].contains("-p")) {
                Main.printPath = true;
                args = removeArrayElement(args, i);
                break;
            }
        }


        if (args.length == 0) {
            ArrayList<String> lines = new ArrayList<String>();

            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

            String line;
            while ((line = in.readLine()) != null) {
                if (line.contains(";")) {
                    break;
                }
                lines.add(line);
            }

            board = new BoardState(lines);
        } else if (args.length == 1 || args.length == 2) {
            int boardNum = -1;
            try {
                boardNum = Integer.parseInt(args[0]);
            }
            catch (NumberFormatException e) {
                System.err.println("Argument must be a board number");
                System.exit(0);
            }
            if (debug) { System.out.println("Searching for board " + boardNum + "..."); }
            board = BoardUtil.getTestBoard(boardNum);
            if (board == null) {
                System.out.println("Invalid board number: " + boardNum);
                System.exit(0);
            }
        } else {
            System.out.println("Usage: java Main <index> [debug]");
            System.exit(0);

        }

        board.setup();
        //System.out.println(board.goalDistToString(0));

        if (debug) { System.out.println(board); }
        String path = aggressiveSearch(board);
        if (path == null) {
            if (debug) System.out.println("Aggressive search failed, trying idA*");
//            board.initializeBoxToGoalMapping();
            board.analyzeBoard(false);
            path = idAStar(board);
        } else {
            if (debug) System.out.println("Aggressive search succeeded!");
        }
        if (debug) { System.out.println("Path found: "); }
        System.out.println(path);
        if (debug) { System.out.println(investigatePath(board, path, false) ? "Path is VALID" : "Path is INVALID"); }

    }


    public static <T> T[] removeArrayElement(T[] array, int... elementIndexes) {
        for (int e : elementIndexes) {
            array = removeArrayElement(array, e);
        }
        return array;
    }

    @SuppressWarnings({ "unchecked" })
    public static <T> T[] removeArrayElement(T[] array, int elementIndex) {
        T[] returnArray = (T[]) Array.newInstance(array[0].getClass(), array.length - 1);
        System.arraycopy(array, 0, returnArray, 0, elementIndex);
        System.arraycopy(array, elementIndex + 1, returnArray, elementIndex, array.length - elementIndex - 1);
        return returnArray;
    }

    private static String res;
    private static long   visitedStates;

    public static String idAStar(BoardState board) {
        long startTime = System.currentTimeMillis();
        res = null;
        int startValue = board.getBoardValue();
        for (int maxValue = startValue; !debug || maxValue < startValue + 500; maxValue += 2) {
            long relativeTime = System.currentTimeMillis();
            visitedStates = 0;
            if (debug) { System.out.print("Trying maxValue " + maxValue + "... "); }
            boolean done = dfs(board, 0, maxValue, false);
            if (debug) {
                System.out.print("visited " + visitedStates + " states. ");
                System.out.println("Total time: " + (System.currentTimeMillis() - startTime) + " Relative time: " + (System.currentTimeMillis() - relativeTime));
            }
            if (done) { return res; }
        }
        return null;
    }

    public static String aggressiveSearch(BoardState board) {
        res = null;
        int startValue = board.getBoardValue();
        boolean done = dfs(board, 0, startValue, true);
        if (done) { return res; }
        return null;
    }

    private static boolean dfs(BoardState board, int depth, int maxValue, boolean aggressive) {
        visitedStates++;
        if (!board.isDenseBoard()) {
//            board.moveLatestBoxToGoalIfPossible();
        }
        if (board.isBoardSolved()) {
            res = board.backtrackPath();
            return true;
        }
        board.analyzeBoard(aggressive);
        int[] jumps = board.getPossibleJumpPositions();
        if (jumps == null) return false;
        if (board.getBoardValue() > maxValue) { return false; }

        if (printPath) {
            System.out.println(board);
            System.out.println("Board value: " + board.getBoardValue());
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {

            }
        }

        if (!board.hashCurrentBoardState(depth, maxValue)) { return false; }
        // First try and push a box from where we stand
        for (int dir = 0; dir < 4; dir++) {
            if (board.isBoxInDirection(dir) && board.isGoodMove(dir)) {
                board.performMove(dir);
                if (dfs(board, depth + 1, maxValue, aggressive)) { return true; }
                board.reverseMove();
            }
        }

        // Now try moving first and then push
        for (int jump : jumps) {
            board.performJump(jump);
            for (int dir = 0; dir < 4; dir++) {
                if (board.isBoxInDirection(dir) && board.isGoodMove(dir)) {
                    board.performMove(dir);
                    if (dfs(board, depth + 1, maxValue, aggressive)) { return true; }
                    board.reverseMove();
                }
            }
            board.reverseMove();
        }
        return false;
    }

    public static boolean investigatePath(BoardState board, String path, boolean displaySteps) {
        if (path == null) return false;
        for (char ch : path.toCharArray()) {
            switch (ch) {
                case 'U':
                    board.performMove(BoardState.UP);
                    break;
                case 'R':
                    board.performMove(BoardState.RIGHT);
                    break;
                case 'D':
                    board.performMove(BoardState.DOWN);
                    break;
                case 'L':
                    board.performMove(BoardState.LEFT);
                    break;
                default:
                    throw new RuntimeException("Invalid move: " + ch);
            }
            if (displaySteps) {
                System.out.println(board);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {

                }
            }
        }
        boolean good = board.isBoardSolved();
        board.backtrackPath();
        return good;
    }
}