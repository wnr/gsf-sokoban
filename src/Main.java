import java.io.*;
import java.util.*;

public class Main {
    public static boolean debug = false;

    public static void main(String[] args) throws IOException {
        BoardState board = null;

        for(int i = 0; i < args.length; i++) {
            if(args[i].contains("debug") || args[i].contains("-d")) {
                Main.debug = true;
                break;
            }
        }

        if (args.length == 0) {
            ArrayList<String> lines = new ArrayList<String>();

            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

            String line;
            while((line = in.readLine()) != null) {
                if(line.contains(";")){
                    break;
                }
                lines.add(line);
            }

            board = new BoardState(lines);
        } else if (args.length == 1 || args.length == 2) {
            int boardNum = -1;
            try {
                boardNum = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Argument must be a board number");
                System.exit(0);
            }
            if(debug) System.out.println("Searching for board " + boardNum + "...");
            board = BoardUtil.getTestBoard(boardNum);
            if (board == null) {
                System.out.println("Invalid board number: " + boardNum);
                System.exit(0);
            }
        } else {
            System.out.println("Usage: java Main <index> [debug]");
            System.exit(0);
        }


        if(debug) System.out.println(board);

        String path = iddfs(board);
        if(debug) System.out.println("Path found: ");
        System.out.println(path);
        if(debug) System.out.println(isValidAnswer(board, path) ? "Path is VALID" : "Path is INVALID");
    }

    private static String res;
    private static int visitedStates;
    public static String iddfs(BoardState board) {
        res = null;
        for (int maxDepth = 1; maxDepth < 40; maxDepth++) {
            visitedStates = 0;
            if(debug) System.out.print("Trying depth " + maxDepth + "... ");
            boolean done = dfs(board, 0, maxDepth);
            if(debug) System.out.println("visited " + visitedStates + " states");
            if (done) return res;
        }
        return null;
    }

    private static boolean dfs(BoardState board, int depth, int maxDepth) {
        visitedStates++;
        if (board.isBoardSolved()) {
            res = board.backtrackPath();
            return true;
        }
        if (depth == maxDepth) return false;
        for (int dir = 0; dir < 4; dir++) {
            if ((board.movedBoxLastMove() || board.directionLastMove() != BoardState.getOppositeDirection(dir)) && board.isGoodMove(dir)) {
                board.performMove(dir);
                if (dfs(board, depth + 1, maxDepth)) return true;
                board.reverseMove();
            }
        }
        return false;
    }

    public static boolean isValidAnswer(BoardState board, String moves) {
        for (char ch : moves.toCharArray()) {
            switch (ch) {
                case 'U':
                    board.performMove(BoardState.UP); break;
                case 'R':
                    board.performMove(BoardState.RIGHT); break;
                case 'D':
                    board.performMove(BoardState.DOWN); break;
                case 'L':
                    board.performMove(BoardState.LEFT); break;
                default:
                    throw new RuntimeException("Invalid move: " + ch);
            }
        }
        boolean good = board.isBoardSolved();
        board.backtrackPath();
        return good;
    }
}