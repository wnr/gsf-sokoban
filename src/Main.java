import java.io.*;
import java.lang.reflect.Array;
import java.util.*;

public class Main {

    public static final int FORWARD  = 1;
    public static final int BACKWARD = 2;
    public static final int BI_DIR   = 3;

    public static final long MAX_RUNNING_TIME_DFS = 1000;
    public static final int MAX_VALUE_INCREASER = 2;
    public static final int NUM_SHUFFLING = 5;

    public static boolean debug              = false;
    public static boolean printPath          = false;
    public static int     forwardOrBackwards = BI_DIR;

    public static BoardStateLight testBoard;

    public static void main(String[] args) throws IOException {
        BoardState boardForward = null;
        BoardStateBackwards boardBackward = null;
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

        long startime = System.currentTimeMillis();

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

            testBoard = new BoardStateLight(lines);
            if (forwardOrBackwards != BACKWARD) { boardForward = new BoardState(lines); }
            if (forwardOrBackwards != FORWARD) { boardBackward = new BoardStateBackwards(lines); }
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

            testBoard = BoardUtil.getTestBoardLight(boardNum);
            if (forwardOrBackwards != BACKWARD) {boardForward = BoardUtil.getTestBoard(boardNum); }
            if (forwardOrBackwards != FORWARD) {boardBackward = BoardUtil.getTestBoardBackwards(boardNum);}

            if (boardForward == null && boardBackward == null) {
                System.out.println("Invalid board number: " + boardNum);
                System.exit(0);
            }
        } else {
            System.out.println("Usage: java Main <index> [debug]");
            System.exit(0);
        }

        if (debug) { System.out.print("Time before setup: " + (System.currentTimeMillis() - startime)); }

        if (forwardOrBackwards != BACKWARD) { boardForward.setup(); }
        if (forwardOrBackwards != FORWARD) { boardBackward.setup(); }

        if (debug) { System.out.println("Time after setup:" + (System.currentTimeMillis() - startime)); }

        //        System.out.println(board.goalDistToString(5));

        if (debug && forwardOrBackwards != BACKWARD) { System.out.println(boardForward); }
        if (debug && forwardOrBackwards != FORWARD) { System.out.println(boardBackward); }

        String path = null;

        if (forwardOrBackwards == FORWARD) {
            if (debug) {System.out.println("Using only Forward algorithms");}
            path = aggressiveSearch(boardForward);

            if (path == null) {
                if (debug) { System.out.println("Aggressive search failed, trying idA*"); }
                boardForward.clearCache();
                boardForward.analyzeBoard(false);
                boardForward.initializeBoxToGoalMapping();
                path = idAStar(boardForward);
            } else {
                if (debug) { System.out.println("Aggressive search succeeded!"); }
            }
        } else if (forwardOrBackwards == BACKWARD) {
            if (debug) {System.out.println("Using only Backward algorithms");}

            //                        path = aggressiveSearchBackwards(boardBackward);

            if (path == null) {
                if (debug) { System.out.println("Aggressive search failed, trying idA*"); }
                //                boardBackward.clearCache();
                //                boardBackward.analyzeBoard(false);
                //                boardBackward.initializeBoxToGoalMapping();
                path = idAStarBackwards(boardBackward);
            } else {
                if (debug) { System.out.println("Aggressive search succeeded!"); }
            }
        } else if (forwardOrBackwards == BI_DIR) {
            boardBackward.setBoardStateForwards(boardForward);
            boardForward.setBoardStateBackwards(boardBackward);
            if (debug) {System.out.println("Using Forward AND Backwards algorithms");}
            path = aggressiveSearch(boardForward);
            if (path == null) {
                if (debug) { System.out.println("Aggressive search failed, trying idA*"); }
                boardForward.clearCache();
                boardForward.analyzeBoard(false);
                boardForward.initializeBoxToGoalMapping();

                path = idAStarBi(boardForward, boardBackward);
                if (debug) {
                    System.out.println("Tried pathFromHash " + boardForward.pathFromHashCnt + "(" + boardForward.pathFromHashSuccessCnt + ") times");
                    System.out.println("Tried pathFromHash " + boardBackward.pathFromHashCnt + "(" + boardBackward.pathFromHashSuccessCnt + ") times");
                }
            } else {
                if (debug) { System.out.println("Aggressive search succeeded!"); }
            }
        }

        if (debug) { System.out.println("Path found: "); }
        System.out.println(path);
        if (debug) { System.out.println(investigatePath(testBoard, path, false) ? "Path is VALID" : "Path is INVALID"); }
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
            boolean done = dfs(board, 0, maxValue, false, -1);

            if (debug) {
                System.out.print("visited " + visitedStates + " states. ");
                System.out.println("Total time: " + (System.currentTimeMillis() - startTime) + " Relative time: " + (System.currentTimeMillis() - relativeTime));
            }
            if (done) { return res; }
        }
        return null;
    }

    public static String idAStarBi(BoardState boardForwards, BoardStateBackwards boardBackwards) {
        res = null;
        boolean done = false;
        int startValueForwards = boardForwards.getBoardValue();
        //        int startValueBackwards = boardBackwards.getBoardValue();
        int maxForwardsDepthValue = startValueForwards;
        int maxBackwardsDepthValueIncreaser = 0;

        long totalTimeBackwards = 0;
        long totalTimeForwards = 0;
        long relativeTimeForwards = 0;
        long relativeTimeBackwards = 0;
        int firstBackwardsStartPos = 0;

        int nextToGo = BI_DIR;
        while (true) {
            if ((nextToGo & FORWARD) == FORWARD) {

                visitedStates = 0;
                if (debug) { System.out.print("Trying maxValue using Forwards " + maxForwardsDepthValue + "... "); }
                long relativeStartTime = System.currentTimeMillis();

                done = dfs(boardForwards, 0, maxForwardsDepthValue, false, System.currentTimeMillis() + MAX_RUNNING_TIME_DFS);

                relativeTimeForwards = System.currentTimeMillis() - relativeStartTime;
                totalTimeForwards += relativeTimeForwards;
                maxForwardsDepthValue += MAX_VALUE_INCREASER;

                if (debug) {
                    System.out.print("visited " + visitedStates + " states. ");
                    System.out.println("Total time forwards: " + totalTimeForwards + " Relative time: " + relativeTimeForwards);
                }

                if (done) {
                    if (debug) {System.out.println("Total time taken: " + (totalTimeBackwards + totalTimeForwards));}
                    return res;
                }
            }

            if ((nextToGo & BACKWARD) == BACKWARD) {

                visitedStates = 0;
                if (debug) { System.out.print("Trying maxValue using Backwards " + maxBackwardsDepthValueIncreaser + "... "); }

                long relativeStartTime = System.currentTimeMillis();

                for (int possibleStartingPosIndex = firstBackwardsStartPos;;) {
                    boardBackwards.updateInitialStartingPos(possibleStartingPosIndex);
                    boardBackwards.analyzeBoard(false);
                    int initialBoardValue = boardBackwards.getBoardValue();
                    //                    System.out.println("Value for "+ possibleStartingPosIndex + " " + initialBoardValue);
                    int maxValue = maxBackwardsDepthValueIncreaser + initialBoardValue;
                    done = dfsBackwards(boardBackwards, 0, maxValue, false, relativeStartTime + MAX_RUNNING_TIME_DFS);
                    if(done){break;}
                    possibleStartingPosIndex = (possibleStartingPosIndex + 1) % boardBackwards.getPossibleStartingPos().size();
                    if(possibleStartingPosIndex == firstBackwardsStartPos){break;}
                }
                firstBackwardsStartPos = (firstBackwardsStartPos + 1) % boardBackwards.getPossibleStartingPos().size();
                relativeTimeBackwards = System.currentTimeMillis() - relativeStartTime;
                totalTimeBackwards += relativeTimeBackwards;

                maxBackwardsDepthValueIncreaser += MAX_VALUE_INCREASER;

                if (debug) {
                    System.out.print("visited " + visitedStates + " states. ");
                    System.out.println("Total time backwards: " + totalTimeBackwards + " Relative time: " + relativeTimeBackwards);
                }
                if (done) {
                    if (debug) {System.out.println("Total time taken: " + (totalTimeBackwards + totalTimeForwards));}
                    return res;
                }
            }

            //            MAX_RUNNING_TIME_DFS = 200000;

            // Update next one to run
            if (relativeTimeForwards > MAX_RUNNING_TIME_DFS) {
                if (relativeTimeBackwards > MAX_RUNNING_TIME_DFS) {
                    if (nextToGo == FORWARD) {
                        nextToGo = BACKWARD;
                    } else {
                        nextToGo = FORWARD;
                    }
                } else {
                    nextToGo = BACKWARD;
                }
            } else if (relativeTimeBackwards > MAX_RUNNING_TIME_DFS) {
                nextToGo = FORWARD;
            } else {
                // Noone did over time limit
                if (relativeTimeForwards <= relativeTimeBackwards) {
                    nextToGo = FORWARD;
                } else {
                    nextToGo = BACKWARD;
                }
            }
        }
    }


    public static String aggressiveSearch(BoardState board) {
        res = null;
        int startValue = board.getBoardValue();
        boolean done = dfs(board, 0, startValue, true, -1);
        if (done) { return res; }
        return null;
    }

    private static boolean dfs(BoardState board, int depth, int maxValue, boolean aggressive, long maxTime) {

        if (maxTime != -1 && System.currentTimeMillis() > maxTime) {
            return false;
        }
        visitedStates++;
        if (board.isBoardSolved()) {
            res = board.backtrackPath();
            return true;
        }
        board.analyzeBoard(aggressive);
        int[] moves = board.getPossibleBoxMoves();
        if (moves == null) { return false; }

        if (board.getBoardValue() > maxValue) {
            return false;
        }
        if (printPath) {
            System.out.println(board);
            System.out.println("Board value: " + board.getBoardValue());
            try {
                Thread.sleep(100);
            }
            catch (InterruptedException e) {

            }
        }

        if (!board.hashCurrentBoardState(maxValue)) {return false;}

        if (board.getPathWithBackwards() != null) {
            res = board.getPathWithBackwards();
            return true;
        }

        for (int move : moves) {
            board.performBoxMove(move);
            if (dfs(board, depth + 1, maxValue, aggressive, maxTime)) { return true; }
            board.reverseMove();
        }
        return false;
    }


    public static String idAStarBackwards(BoardStateBackwards board) {
        long startTime = System.currentTimeMillis();
        res = null;
        for (int increaser = 0; !debug || increaser < +500; increaser += 2) {
            long relativeStartTime = System.currentTimeMillis();
            if (debug) { System.out.print("Trying maxValue " + increaser + "... "); }
            boolean done = false;
            visitedStates = 0;

            for (int possibleStartingPosIndex = 0; !done && possibleStartingPosIndex < board.getPossibleStartingPos().size(); possibleStartingPosIndex++) {
                board.updateInitialStartingPos(possibleStartingPosIndex);
                board.analyzeBoard(false);
                int initialBoardValue = board.getBoardValue();
                int maxValue = increaser + initialBoardValue;
                done = dfsBackwards(board, 0, maxValue, false, -1);
            }
            long relativeTimeBackwards = System.currentTimeMillis() - relativeStartTime;


            if (debug) {
                System.out.print("visited " + visitedStates + " states. ");
                System.out.println("Total time: " + (System.currentTimeMillis() - startTime) + " Relative time: " + relativeTimeBackwards);
            }
            if (done) {
                return res;
            }
        }
        return null;
    }

    public static String aggressiveSearchBackwards(BoardStateBackwards board) {
        res = null;
        int startValue = board.getBoardValue();
        boolean done = dfsBackwards(board, 0, startValue, true, -1);
        if (done) { return res; }
        return null;
    }

    private static boolean dfsBackwards(BoardStateBackwards board, int depth, int maxValue, boolean aggressive, long maxTime) {

        if (maxTime != -1 && System.currentTimeMillis() > maxTime) {
            return false;
        }

        visitedStates++;
        if (!board.isDenseBoard()) {
            //            board.moveLatestBoxToGoalIfPossible();
        }
        board.analyzeBoard(aggressive);
        if (board.isBoardSolved()) {
            res = board.backtrackPath();
            return true;
        }
        int[] possibleBoxMoves = board.getPossibleBoxJumpMoves();
        if (possibleBoxMoves == null) { return false; }

        if (printPath) {
            System.out.println(board);
            System.out.println("Board value: " + board.getBoardValue());
            try {
                Thread.sleep(100);
            }
            catch (InterruptedException e) {

            }
        }
        if (board.getBoardValue() > maxValue) { return false; }

        if (!board.hashCurrentBoardState(maxValue)) { return false; }
        if (board.getPathWithForwards() != null) {
            res = board.getPathWithForwards();
            return true;
        }
        // First try and push a box from where we stand
        //        if (!board.isFirstStep()) {
        //            for (int dir = 0; dir < 4; dir++) {
        //                if (board.isBoxInDirection(BoardState.getOppositeDirection(dir)) && board.isGoodMove(dir)) {
        //                    int boxPos = board.getPosFromPlayerInDirection(BoardState.getOppositeDirection(dir));
        //                    board.performBoxMove(dir | boxPos << 2);
        //                    if (dfsBackwards(board, depth + 1, maxValue, aggressive, maxTime)) { return true; }
        //                    board.reverseMove();
        //                }
        //            }
        //        }

        // Now try moving first and then push
        for (int boxMove : possibleBoxMoves) {
            board.performBoxMove(boxMove);
            if (dfsBackwards(board, depth + 1, maxValue, aggressive, maxTime)) { return true; }
            board.reverseMove();
        }
        return false;
    }

    public static boolean investigatePath(String path) {
        return investigatePath(testBoard, path, false);
    }

    public static boolean investigatePath(BoardStateLight board, String path, boolean displaySteps) {
        if (path == null) { return false; }
        String pathTaken = "";
        for (char ch : path.toCharArray()) {
            boolean success = false;
            switch (ch) {
                case 'U':
                    success = board.performMove(BoardState.UP);
                    break;
                case 'R':
                    success = board.performMove(BoardState.RIGHT);
                    break;
                case 'D':
                    success = board.performMove(BoardState.DOWN);
                    break;
                case 'L':
                    success = board.performMove(BoardState.LEFT);
                    break;
                default:
                    throw new RuntimeException("Invalid move: " + ch);
            }
            if (displaySteps && !success) {
                System.out.println(pathTaken);
                return false;
            }
            pathTaken += "" + ch;
            if (displaySteps) {
                System.out.println(board);
                try {
                    Thread.sleep(100);
                }
                catch (InterruptedException e) {

                }
            }
        }
        boolean good = board.isBoardSolved();
        board.backtrackPath();
        return good;
    }
}