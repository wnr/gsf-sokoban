import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;

public class BoardStateBackwards {

    public static final int    INF               = 100000000;
    public static final double DENSE_BOARD_LIMIT = 0.15;

    public static final char FREE_SPACE_CHAR     = ' ';
    public static final char GOAL_CHAR           = '.';
    public static final char WALL_CHAR           = '#';
    public static final char PLAYER_CHAR         = '@';
    public static final char PLAYER_ON_GOAL_CHAR = '+';
    public static final char BOX_CHAR            = '$';
    public static final char BOX_ON_GOAL_CHAR    = '*';

    public static final int UP    = 0;
    public static final int RIGHT = 1;
    public static final int DOWN  = 2;
    public static final int LEFT  = 3;

    private static final int FREE_SPACE     = 0;
    private static final int WALL           = 1;
    private static final int GOAL           = 2;
    private static final int PLAYER         = 4;
    private static final int BOX            = 8;
    private static final int PLAYER_ON_GOAL = PLAYER | GOAL;
    private static final int BOX_ON_GOAL    = BOX | GOAL;
    private static final int NOT_FREE       = WALL | BOX;

    private static final int NOT_TUNNEL = 0;
    private static final int TUNNEL     = 1;
    private static final int OPENING    = 3;
    private static final int DEAD_END   = 5;
    private static final int ROOM       = 8;

    private char[] boardCharacters = { FREE_SPACE_CHAR, WALL_CHAR, GOAL_CHAR, 0, PLAYER_CHAR, 0, PLAYER_ON_GOAL_CHAR, 0, BOX_CHAR, 0, BOX_ON_GOAL_CHAR };
    private static HashMap<Character, Integer> characterMapping;

    static {
        characterMapping = new HashMap<Character, Integer>();
        characterMapping.put(FREE_SPACE_CHAR, FREE_SPACE);
        characterMapping.put(GOAL_CHAR, BOX);
        characterMapping.put(WALL_CHAR, WALL);
        characterMapping.put(PLAYER_CHAR, PLAYER);
        characterMapping.put(PLAYER_ON_GOAL_CHAR, BOX);
        characterMapping.put(BOX_CHAR, GOAL);
        characterMapping.put(BOX_ON_GOAL_CHAR, BOX_ON_GOAL);
    }

    // Vectors corresponding to the moves {up, right, down, left}
    private static int[]  dr                  = { -1, 0, 1, 0 };
    private static int[]  dc                  = { 0, 1, 0, -1 };
    private static char[] directionCharacters = { 'U', 'R', 'D', 'L' };

    // New awesome move vector
    private int[] dx;

    private int width, height, totalSize;
    private int playerPos;
    public  int goalCnt, boxCnt;

    private StackEntry previousMove;
    private int[]      board;
    private int[]      boxCells;
    private int[]      goalCells;
    private boolean[]  trappingCells;
    //    private boolean[]  temporaryWall;
    private int[]      matchedGoal;
    private int[]      matchedBox;
    private int[]      possibleBoxJumpMoves;
    private int[]      initialPossibleJumpPositions;
    private int[]      tunnels;
    private int[]      goalsInPrioOrder;
    private int[]      prioForGoal;
    private int        movedBoxesCnt;
    private int[][]    goalSideDist;
    private int[]      boxReachableSideIndex;
    private int[]      currentReachableBoxDir;

    private double boardDensity;

    private long startingPositionHash;
    private int  startingPlayerPos;

    // TODO Add method moveBoxToGoalIfPossible, needs changes in reverseMove


    private int[]                playerAndBoxesHashCells;
    private HashMap<Long, int[]> gameStateHash;
    private BoardState           boardStateForwards;

    private String pathWithForwards;


    int mostUpLeftPos;

    public BoardStateBackwards(List<String> lines) {
        height = lines.size();
        width = 0;
        for (String line : lines) {
            width = Math.max(width, line.length());
        }
        totalSize = width * height;
        dx = new int[]{ -width, 1, width, -1 };
        board = new int[totalSize];
        int row = 0;
        List<Integer> tempGoalCells = new ArrayList<Integer>();
        List<Integer> tempBoxCells = new ArrayList<Integer>();
        for (String line : lines) {
            int col = 0;
            for (char cell : line.toCharArray()) {
                board[row * width + col] = characterMapping.get(cell);
                if (isPlayer(row * width + col) || cell == PLAYER_ON_GOAL_CHAR) {
                    playerPos = row * width + col;
                }
                if (isGoal(row * width + col)) {
                    tempGoalCells.add(row * width + col);
                    goalCnt++;
                }
                if (isBox(row * width + col)) {
                    tempBoxCells.add(row * width + col);
                    board[row * width + col] |= boxCnt << 4;
                    boxCnt++;
                }
                col++;
            }
            row++;
        }


        startingPlayerPos = playerPos;
        mostUpLeftPos = playerPos;
        int boardSections[] = new int[totalSize];
        analyzeBoardDfsOneTimeUse(playerPos, boardSections);
        int[] tempPlayerAndBoxesHashCells = new int[boxCnt + 1];
        tempPlayerAndBoxesHashCells[boxCnt] = totalSize + mostUpLeftPos;
        oneTimeUseLocateGoalCellsInOrder(tempPlayerAndBoxesHashCells);
        startingPositionHash = getHashCode(tempPlayerAndBoxesHashCells, BoardState.HASH_PRIMES[0]);


        boolean[] visited = new boolean[totalSize];
        setOutsideSpaceDFS(playerPos, visited);
        for (int pos = 0; pos < totalSize; pos++) {
            if (isFree(pos) && !visited[pos]) {
                board[pos] = WALL;
            }
        }

        double freeCellCount = 0;
        // Calculate board density
        for (int pos = 0; pos < totalSize; pos++) {
            if (isFree(pos)) {
                freeCellCount++;
            }
        }
        boardDensity = boxCnt / (boxCnt + freeCellCount);

        gameStateHash = new HashMap<Long, int[]>();
        playerAndBoxesHashCells = new int[boxCnt + 1];

        boxCells = new int[boxCnt];
        goalCells = new int[goalCnt];
        for (int i = 0; i < boxCells.length; i++) {
            boxCells[i] = tempBoxCells.get(i);
        }
        for (int i = 0; i < goalCells.length; i++) {
            goalCells[i] = tempGoalCells.get(i);
        }
        movedBoxesCnt = 0;
    }

    public void analyzeBoard(boolean aggressive) {
        int boardSections[] = new int[totalSize];

        locateBoxes();

        mostUpLeftPos = playerPos;
        analyzeBoardDfs(playerPos, boardSections);

        playerAndBoxesHashCells[boxCnt] = mostUpLeftPos;

        int lastMovedBoxIndex = -1;
        int lastMovedBoxPos = -1;
        if (movedBoxLastMove()) {
            int dir = directionLastMove();
            lastMovedBoxPos = playerPos + dx[dir];
            lastMovedBoxIndex = getBoxNumber(lastMovedBoxPos);


            //TODO: Check other deadlocks for backwards
            //            boolean checkDeadlock = false;
            //            if (!isGoal(lastMovedBoxPos)) {
            //                for (int d = 0; d < 4; d++) {
            //                    int adjacentBoxPos = lastMovedBoxPos + dx[d];
            //                    if (isFree(adjacentBoxPos) && trappingCells[adjacentBoxPos]) {
            //                        checkDeadlock = true;
            //                    }
            //                }
            //            }
            //            if (checkDeadlock && isDeadLock()) {
            //                possibleBoxJumpMoves = null;
            //                return;
            //            }

            //            addTemporaryWallsDfs(lastMovedBoxPos);
            updateMatchingForBox(lastMovedBoxIndex);

            //            if (temporaryWall[lastMovedBoxPos]) {
            //                if (!checkIfGoalsStillReachable()) {
            //                    possibleBoxJumpMoves = null;
            //                    return;
            //                }
            //            }
        }

        playerAndBoxesHashCells[boxCnt] = totalSize + mostUpLeftPos;

        if ((tunnels[playerPos] & TUNNEL) == TUNNEL) {
            int dir = directionLastMove();

            if (movedBoxLastMove()) {
                int boxPos = playerPos + dx[dir];
                if ((tunnels[boxPos] & TUNNEL) == TUNNEL || tunnels[boxPos] == ROOM) { //TODO: Maybe should pull box out of tunnel
                    if (!isGoal(boxPos)) {
                        possibleBoxJumpMoves = new int[0];
                        return;
                    }
                }
            }
        }
        LinkedList<Integer> boxMoves = new LinkedList<Integer>();
        for (int i = 0; i < goalCnt; i++) {
            int goal = goalsInPrioOrder[i];
            int box = matchedBox[goal];
            int boxPos = boxCells[box];
            if (aggressive && lastMovedBoxIndex != -1 && box != lastMovedBoxIndex && getGoalSideDistValue(boxCells[lastMovedBoxIndex], matchedGoal[lastMovedBoxIndex]) != 0) {
                continue;
            }

            //Altered for backwards
            for (int dir = 0; dir < 4; dir++) {
                int newPos = boxPos + dx[dir];
                if (isFree(newPos)) {
                    int newPos2 = newPos + dx[dir];
                    if (isFree(newPos2)) {
                        if ((1 == boardSections[newPos] && playerPos != newPos) || previousMove == null) {
                            boxMoves.add(dir | boxPos << 2);
                        }
                    }
                }
            }
        }

        //TODO: Test ignore convert to array and just go with arraylist
        possibleBoxJumpMoves = new int[boxMoves.size()];
        int i = 0;
        for (int move : boxMoves) {
            possibleBoxJumpMoves[i++] = move;
        }
    }
    //
    //    public boolean isDeadLock() {
    //        boolean[] reachable = new boolean[totalSize];
    //        checkDeadlockDfs(playerPos, reachable);
    //        boolean deadLock = false;
    //        for (int box = 0; box < boxCnt; box++) {
    //            int boxPos = boxCells[box];
    //            int goal = matchedGoal[box];
    //            if (!isGoal(boxPos)) {
    //                boolean good = false;
    //                for (int dir = 0; dir < 4; dir++) {
    //                    int pos = boxPos + dx[dir];
    //                    int oppPos = boxPos + dx[getOppositeDirection(dir)];
    //                    if (reachable[pos] && !trappingCells[oppPos] && (isFree(oppPos) || isBox(oppPos) && reachable[oppPos])) {
    //                        good = true;
    //                    }
    //                }
    //                if (!good) { deadLock = true; }
    //            }
    //        }
    //        return deadLock;
    //    }

    private void checkDeadlockDfs(int pos, boolean[] reachable) {
        if (reachable[pos]) { return; }
        reachable[pos] = true;
        for (int dir = 0; dir < 4; dir++) {
            int newPos = pos + dx[dir];
            if (isFree(newPos)) {
                checkDeadlockDfs(newPos, reachable);
            } else if (isBox(newPos)) {
                int newPos2 = newPos + dx[dir];
                if ((isFree(newPos2) || reachable[newPos2]) && !trappingCells[newPos2]) {
                    checkDeadlockDfs(newPos, reachable);
                }
            }
        }
    }

    //    public boolean moveLatestBoxToGoalIfPossible() {
    //        if (!movedBoxLastMove()) { return false; }
    //        int boxPos = playerPos + dx[directionLastMove()];
    //        int boxIndex = getBoxNumber(boxPos);
    //        int goal = matchedGoal[boxIndex];
    //        if (getGoalSideDistValue(boxPos, goal) == 0) { return false; }
    //        LinkedList<Integer> moves = new LinkedList<Integer>();
    //        board[boxPos] &= ~BOX;
    //        boolean possible = moveBoxToGoalDfs(playerPos, directionLastMove(), goal, moves);
    //        board[boxPos] |= BOX;
    //        if (possible) {
    //            for (int move : moves) {
    //                if ((move & 8) != 0) {
    //                    performJump(move >>> 4);
    //                } else {
    //                    performMove(move & 3, true); //TODO: Check if it should be true
    //                }
    //            }
    //            previousMove.val |= moves.size() << 22;
    //            return true;
    //        }
    //        return false;
    //    }

    //    private boolean moveBoxToGoalDfs(int pos, int forward, int goal, LinkedList<Integer> moves) {
    //        int boxPos = pos + dx[forward];
    //        if (getGoalSideDistValue(boxPos, goal) == 0) { return true; }
    //        int left = forward == 0 ? 3 : forward - 1;
    //        int right = (forward + 1) & 3;
    //        int backward = (forward + 2) & 3;
    //        boolean[] reachable = new boolean[4];
    //        reachable[backward] = true;
    //
    //        // Try walking to the left of the box
    //        if (checkIfFreePath(pos, new int[]{ left, forward })) {
    //            reachable[left] = true;
    //            if (checkIfFreePath(boxPos + dx[left], new int[]{ forward, right })) {
    //                reachable[forward] = true;
    //                if (checkIfFreePath(boxPos + dx[forward], new int[]{ right, backward })) {
    //                    reachable[right] = true;
    //                }
    //            }
    //        }
    //
    //        // Try walking to the right of the box
    //        if (checkIfFreePath(pos, new int[]{ right, forward })) {
    //            reachable[right] = true;
    //            if (checkIfFreePath(boxPos + dx[right], new int[]{ forward, left })) {
    //                reachable[forward] = true;
    //                if (checkIfFreePath(boxPos + dx[forward], new int[]{ left, backward })) {
    //                    reachable[left] = true;
    //                }
    //            }
    //        }
    //        for (int i = 0; i < 4; i++) {
    //            if (reachable[i]) {
    //                int moveDir = (i + 2) & 3;
    //                int newBoxPos = boxPos + dx[moveDir];
    //                if (isFree(newBoxPos) && goalSideDist[4 * newBoxPos + i][goal] < goalSideDist[4 * boxPos + i][goal]) {
    //                    boolean possible = moveBoxToGoalDfs(boxPos, moveDir, goal, moves);
    //                    if (possible) {
    //                        moves.addFirst(4 | moveDir);
    //                        if (i != backward) {
    //                            moves.addFirst(8 | (boxPos + dx[i]) << 4);
    //                        }
    //                    }
    //                    return possible;
    //                }
    //            }
    //        }
    //        return false;
    //    }

    private boolean checkIfFreePath(int pos, int[] moves) {
        if (!isFree(pos)) { return false; }
        for (int move : moves) {
            pos += dx[move];
            if (!isFree(pos)) { return false; }
        }
        return true;
    }

    //    private boolean checkIfGoalsStillReachable() {
    //        boolean[] reachable = new boolean[totalSize];
    //        for (int box = 0; box < boxCnt; box++) {
    //            int boxPos = boxCells[box];
    //            if (!reachable[boxPos]) {
    //                checkIfGoalsStillReachableDfs(boxPos, reachable);
    //            }
    //        }
    //        for (int goal = 0; goal < goalCnt; goal++) {
    //            if (!reachable[goalCells[goal]]) { return false; }
    //        }
    //        return true;
    //    }

    //    private void checkIfGoalsStillReachableDfs(int pos, boolean[] visited) {
    //        visited[pos] = true;
    //        for (int dir = 0; dir < 4; dir++) {
    //            int newPos = pos + dx[dir];
    //            if (!isWallOrTemporaryWall(newPos) && !visited[newPos]) {
    //                int oppPos = pos + dx[getOppositeDirection(dir)];
    //                if (!isWallOrTemporaryWall(oppPos)) {
    //                    checkIfGoalsStillReachableDfs(newPos, visited);
    //                }
    //            }
    //        }
    //    }

    private void locateBoxes() {
        int boxIndex = 0;
        for (int pos = 0; pos < totalSize; pos++) {
            if (isBox(pos)) {
                int boxNum = getBoxNumber(pos);
                boxCells[boxNum] = pos;
                playerAndBoxesHashCells[boxIndex] = pos;
                boxIndex++;
            }
        }
    }

    private void oneTimeUseLocateGoalCellsInOrder(int[] hashCells) {
        int goalIndex = 0;
        for (int pos = 0; pos < totalSize; pos++) {
            if (isGoal(pos)) {
                hashCells[goalIndex] = pos;
                goalIndex++;
            }
        }
    }

    private void analyzeBoardDfs(int pos, int[] boardSections) {
        boardSections[pos] = 1;
        for (int dir = 0; dir < 4; dir++) {
            int newPos = pos + dx[dir];
            if (isFree(newPos) && boardSections[newPos] == 0) {
                if (newPos < mostUpLeftPos) {
                    mostUpLeftPos = newPos;
                }
                analyzeBoardDfs(newPos, boardSections);
            }
        }
    }

    private void analyzeBoardDfsOneTimeUse(int pos, int[] boardSections) {
        boardSections[pos] = 1;
        for (int dir = 0; dir < 4; dir++) {
            int newPos = pos + dx[dir];
            if (((isFree(newPos) && !isGoal(newPos)) || (isBox(newPos) && !isGoal(newPos))) && boardSections[newPos] == 0) {
                if (newPos < mostUpLeftPos) {
                    mostUpLeftPos = newPos;
                }
                analyzeBoardDfsOneTimeUse(newPos, boardSections);
            }
        }
    }

    private void setOutsideSpaceDFS(int pos, boolean[] visited) {
        visited[pos] = true;
        for (int dir = 0; dir < 4; dir++) {
            int newPos = pos + dx[dir];
            if (!isWall(newPos) && !visited[newPos]) {
                setOutsideSpaceDFS(newPos, visited);
            }
        }
    }

    public void setup() {
        currentReachableBoxDir = new int[boxCnt];
        goalSideDist = new int[totalSize * 4][goalCnt];
        boxReachableSideIndex = new int[totalSize * 4];

        Arrays.fill(boxReachableSideIndex, -1);
        for (int pos = 0; pos < totalSize * 4; pos++) {
            Arrays.fill(goalSideDist[pos], INF);
        }

        for (int pos = 0; pos < totalSize; pos++) {
            computeReachableSideIndexBFS(pos);
        }

        for (int i = 0; i < goalCells.length; i++) {
            int goalPos = goalCells[i];

            for (int dir = 0; dir < 4; dir++) {
                goalSideDist[goalPos * 4 + dir][i] = 0;
            }
            LinkedList<Integer> q = new LinkedList<Integer>();
            q.add(goalPos);
            while (!q.isEmpty()) {
                int pos = q.removeFirst();
                for (int dir = 0; dir < 4; dir++) {
                    int newPos = pos + dx[dir];
                    int d = goalSideDist[pos * 4 + getOppositeDirection(dir)][i] + 1;
                    if (!isWall(newPos) && d < goalSideDist[newPos * 4 + getOppositeDirection(dir)][i]) {
                        int newPos2 = pos + dx[getOppositeDirection(dir)];
                        if (!isWall(newPos2)) {
                            int boxSideZoneIndex = boxReachableSideIndex[newPos * 4 + getOppositeDirection(dir)];
                            for (int boxSide = 0; boxSide < 4; boxSide++) {
                                if (boxReachableSideIndex[newPos * 4 + boxSide] == boxSideZoneIndex) {
                                    goalSideDist[newPos * 4 + boxSide][i] = d;
                                }
                            }
                            q.add(newPos);
                        }
                    }
                }
            }
        }

        // Trapping cells
        trappingCells = new boolean[totalSize];
        for (int pos = 0; pos < totalSize; pos++) {
            trappingCells[pos] = true;
            for (int goal = 0; goal < goalCnt; goal++) {
                trappingCells[pos] &= getMinimumGoalSideDistValue(pos, goal) == INF;
            }
        }
        //        temporaryWall = new boolean[totalSize];
        //        for (int pos = 0; pos < totalSize; pos++) {
        //            if (isBox(pos) && !temporaryWall[pos]) {
        //                addTemporaryWallsDfs(pos);
        //            }
        //        }

        computeTunnels();
        initializeBoxToGoalMapping();
        analyzeBoard(false);
    }

    public String goalDistToString(int goal) {
        StringBuilder sb = new StringBuilder();
        sb.append("Goal distance matrices for goal: " + goal + '\n');
        //        for (int dir = 0; dir < 4; dir++) {
        for (int pos = 0; pos < board.length; pos++) {
            //                int value = goalSideDist[pos * 4 + dir][goal];
            int value = getMinimumGoalSideDistValue(pos, goal);
            if (isWall(pos)) {
                sb.append(WALL_CHAR);
            } else if (value == INF) {
                sb.append(' ');
            } else {
                sb.append(intToIntOrAscii(value));
            }
            if (pos % width == width - 1) {
                sb.append('\n');
            }
        }
        sb.append('\n');
        //        }
        return sb.toString();
    }

    private Object intToIntOrAscii(int value) {
        Object returnValue;
        if (value > 35) {
            returnValue = (char) (value + 61);
        } else if (value > 9) {
            returnValue = (char) (value + 55);
        } else {
            returnValue = value;
        }
        return returnValue;
    }

    public String replaceBoxWithGoalValueToString(int goal) {
        StringBuilder sb = new StringBuilder();
        for (int pos = 0; pos < totalSize; pos++) {
            if (isBox(pos)) {
                int value = getGoalSideDistValue(pos, goal);
                sb.append(intToIntOrAscii(value));
            } else {
                sb.append(boardCharacters[board[pos] & 15]);
            }
            if (pos % width == width - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }


    private int getGoalSideDistValue(int pos, int dir, int goal) {
        return goalSideDist[pos * 4 + dir][goal];
    }

    private int getGoalSideDistValue(int boxPos, int goal) {
        return getGoalSideDistValue(boxPos, currentReachableBoxDir[getBoxNumber(boxPos)], goal);
    }

    private int getMinimumGoalSideDistValue(int pos, int goal) {
        int minimumValue = goalSideDist[pos * 4 + 3][goal];
        for (int dir = 0; dir < 3; dir++) {
            int newValue = goalSideDist[pos * 4 + dir][goal];
            if (newValue < minimumValue) {
                minimumValue = newValue;
            }
        }
        return minimumValue;
    }


    //TODO: Could be optimized, check if its a problem of some size on big maps
    private void computeReachableSideIndexBFS(int startPos) {

        if (isWall(startPos)) { return;}
        int zoneIndex = 0;
        boolean[] visitedCells = new boolean[board.length];
        for (int startDir = 0; startDir < 4; startDir++) {
            int sidePos = startPos + dx[startDir];
            if (!visitedCells[sidePos]) {
                boxReachableSideIndex[startPos * 4 + startDir] = zoneIndex;
                if (!isWall(sidePos)) {
                    visitedCells[sidePos] = true;
                    LinkedList<Integer> q = new LinkedList<Integer>();
                    q.add(sidePos);
                    while (!q.isEmpty()) {
                        int pos = q.removeFirst();
                        if (isPlayer(pos) && isBox(startPos)) {
                            currentReachableBoxDir[getBoxNumber(startPos)] = startDir;
                        }
                        for (int dir = 0; dir < 4; dir++) {
                            int newPos = pos + dx[dir];
                            if (!isWall(newPos) && !visitedCells[newPos]) {
                                if (newPos == startPos) {
                                    boxReachableSideIndex[startPos * 4 + ((dir + 2) & 3)] = zoneIndex;
                                } else {
                                    visitedCells[newPos] = true;
                                    q.add(newPos);
                                }
                            }
                        }
                    }
                }
            }
            zoneIndex++;
        }
    }

    public void computeTunnels() {
        tunnels = new int[totalSize];

        ArrayList<Integer> deads = new ArrayList<Integer>();

        //Iterate over board, but do not check outer rows or cols.
        for (int pos = width; pos < totalSize - width; pos++) {
            if (onBorder(pos)) { continue; }
            if ((board[pos] & WALL) == 0) {
                boolean u = (board[pos - width] & WALL) == WALL;
                boolean d = (board[pos + width] & WALL) == WALL;
                boolean l = (board[pos - 1] & WALL) == WALL;
                boolean r = (board[pos + 1] & WALL) == WALL;
                boolean v = u && d;
                boolean h = l && r;
                boolean dead = (v && (l || h)) || (h && (u || d));

                if (v || h) {
                    tunnels[pos] |= TUNNEL;

                    if (dead) {
                        tunnels[pos] |= DEAD_END;
                        deads.add(pos);
                    }
                }
            }
        }

        for (int pos : deads) {
            updateTunnels(pos);
        }


        for (int pos = width; pos < totalSize - width; pos++) {
            if (onBorder(pos)) { continue; }
            if ((tunnels[pos] & TUNNEL) == TUNNEL && (tunnels[pos] & DEAD_END) != DEAD_END) {
                computeRoom(pos);
            }
        }
    }

    //    private void addTemporaryWallsDfs(int pos) {
    //        if (checkIfTemporaryWall(pos)) {
    //            temporaryWall[pos] = true;
    //            for (int dir = 0; dir < 4; dir++) {
    //                int newPos = pos + dx[dir];
    //                if (isBox(newPos) && !temporaryWall[newPos]) {
    //                    addTemporaryWallsDfs(newPos);
    //                }
    //            }
    //        }
    //    }
    //
    //    private void removeTemporaryWallsDfs(int pos) {
    //        if (!checkIfTemporaryWall(pos)) {
    //            temporaryWall[pos] = false;
    //            for (int dir = 0; dir < 4; dir++) {
    //                int newPos = pos + dx[dir];
    //                if (isBox(newPos) && temporaryWall[newPos]) {
    //                    removeTemporaryWallsDfs(newPos);
    //                }
    //            }
    //        }
    //    }

    //    private boolean checkIfTemporaryWall(int pos) {
    //        if (!isBox(pos)) { return false; }
    //        if (isBlockedRectangle(pos) || isBlockedRectangle(pos - 1) || isBlockedRectangle(pos - width) || isBlockedRectangle(pos - width - 1)) {
    //            return true;
    //        }
    //        int blockedSides = 0;
    //        for (int dir = 0; dir < 2; dir++) {
    //            int oppDir = dir + 2;
    //            if (isWallOrTemporaryWall(pos + dx[dir]) || isWallOrTemporaryWall(pos + dx[oppDir])) {
    //                blockedSides++;
    //                int dir2 = dir + 1;
    //                int oppDir2 = (dir2 + 2) & 3;
    //                int otherPos = pos + dx[dir2];
    //                if (isBox(otherPos) && (isWallOrTemporaryWall(otherPos + dx[dir]) || isWallOrTemporaryWall(otherPos + dx[oppDir]))) {
    //                    return true;
    //                }
    //                otherPos = pos + dx[oppDir2];
    //                if (isBox(otherPos) && (isWallOrTemporaryWall(otherPos + dx[dir]) || isWallOrTemporaryWall(otherPos + dx[oppDir]))) {
    //                    return true;
    //                }
    //            }
    //        }
    //        return blockedSides == 2;
    //    }

    private boolean isBlockedRectangle(int pos) {
        return !isFree(pos) && !isFree(pos + 1) && !isFree(pos + width) && !isFree(pos + width + 1);
    }

    private void computeRoom(int pos) {
        ArrayList<Integer> cells1 = new ArrayList<Integer>();
        ArrayList<Integer> cells2 = new ArrayList<Integer>();

        tunnels[pos] |= ROOM;

        boolean room1 = false;
        boolean room2 = false;

        if (isWall(pos - 1)) {
            //Test going up and down.
            room1 = computeRoomDfs(pos - width, cells1);
            room2 = computeRoomDfs(pos + width, cells2);
        } else {
            //Test going left and right.
            room1 = computeRoomDfs(pos - 1, cells1);
            room2 = computeRoomDfs(pos + 1, cells2);
        }

        if (!room1) {
            for (int cell : cells1) {
                tunnels[cell] &= ~ROOM;
            }
        }
        if (!room2) {
            for (int cell : cells2) {
                tunnels[cell] &= ~ROOM;
            }
        }

        tunnels[pos] &= ~ROOM;
    }

    private boolean computeRoomDfs(int pos, ArrayList<Integer> cells) {
        if ((tunnels[pos] & ROOM) == ROOM) {
            return true;
        }

        if ((tunnels[pos] & TUNNEL) == TUNNEL && (tunnels[pos] & DEAD_END) != DEAD_END) {
            return false;
        }

        if (!isWall(pos)) {
            tunnels[pos] = tunnels[pos] | ROOM;

            cells.add(pos);

            boolean res = true;
            for (int dir = 0; dir < 4; dir++) {
                res &= computeRoomDfs(pos + dx[dir], cells);
            }
            return res;
        }
        return true;
    }

    private void updateTunnels(int pos) {
        if ((tunnels[pos] & DEAD_END) == DEAD_END) {
            for (int dir = 0; dir < 4; dir++) {
                int newPos = pos + dx[dir];
                int cell = tunnels[newPos];
                if ((cell & TUNNEL) == TUNNEL && (cell & DEAD_END) != DEAD_END) {
                    tunnels[newPos] |= DEAD_END;
                    updateTunnels(newPos);
                }
            }
        }
    }

    public void initializeBoxToGoalMapping() {
        PriorityQueue<int[]> goalsWithLeastCost = new PriorityQueue<int[]>(goalCnt, new Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                if (a[1] > b[1]) { return -1; }
                if (a[1] < b[1]) { return 1; }
                return 0;
            }
        });

        for (int goal = 0; goal < goalCnt; goal++) {
            double distSum = 0;
            int distCnt = 0;
            for (int box = 0; box < boxCnt; box++) {
                if (getMinimumGoalSideDistValue(boxCells[box], goal) < INF) {
                    distCnt++;
                    distSum += getMinimumGoalSideDistValue(boxCells[box], goal);
                }
            }
            goalsWithLeastCost.add(new int[]{ goal, (int) (distSum / distCnt + 0.5) });
        }
        goalsInPrioOrder = new int[goalCnt];
        prioForGoal = new int[goalCnt];
        matchedGoal = new int[boxCnt];
        matchedBox = new int[goalCnt];
        Arrays.fill(matchedGoal, -1);
        Arrays.fill(matchedBox, -1);
        for (int goalIndex = 0; goalIndex < goalCnt; goalIndex++) {
            int goal = goalsWithLeastCost.poll()[0];
            prioForGoal[goal] = goalIndex;
            goalsInPrioOrder[goalIndex] = goal;
            int bestBox = -1;
            for (int box = 0; box < boxCnt; box++) {
                if (matchedGoal[box] == -1) {
                    if (bestBox == -1 || getMinimumGoalSideDistValue(boxCells[box], goal) < getMinimumGoalSideDistValue(boxCells[bestBox], goal)) {
                        bestBox = box;
                    }
                }
            }
            if (getMinimumGoalSideDistValue(boxCells[bestBox], goal) < INF) {
                matchedGoal[bestBox] = goal;
                matchedBox[goal] = bestBox;
            }
        }

        boolean[] visited = new boolean[goalCnt];
        for (int box = 0; box < boxCnt; box++) {
            int boxPos = boxCells[box];
            if (matchedGoal[box] == -1) {
                Arrays.fill(visited, false);
                for (int goal = 0; goal < goalCnt; goal++) {
                    if (getMinimumGoalSideDistValue(boxPos, goal) < INF) {
                        if (match(goal, visited)) {
                            matchedGoal[box] = goal;
                            matchedBox[goal] = box;
                            break;
                        }
                    }
                }
            }
        }

        for (int box = 0; box < boxCnt; box++) {
            initialUpdateMatchingForBox(box);
        }
    }

    private void updateMatchingForBox(int box) {
        int boxPos = boxCells[box];
        for (int otherBox = 0; otherBox < boxCnt; otherBox++) {
            if (box == otherBox) { continue; }
            int g = matchedGoal[box];
            int boxPos2 = boxCells[otherBox];
            int g2 = matchedGoal[otherBox];
            int oldDist = getGoalSideDistValue(boxPos, g) + getGoalSideDistValue(boxPos2, g2);
            int newDist = getGoalSideDistValue(boxPos, g2) + getGoalSideDistValue(boxPos2, g);
            if (newDist < oldDist) {// || newDist == oldDist && prioForGoal[g2] < prioForGoal[g] && goalDist[boxPos][g2] < goalDist[boxPos2][g2]) {
                matchedGoal[box] = g2;
                matchedGoal[otherBox] = g;
            }
        }
    }

    private void initialUpdateMatchingForBox(int box) {
        int boxPos = boxCells[box];
        for (int otherBox = 0; otherBox < boxCnt; otherBox++) {
            if (box == otherBox) { continue; }
            int g = matchedGoal[box];
            int boxPos2 = boxCells[otherBox];
            int g2 = matchedGoal[otherBox];
            int oldDist = getMinimumGoalSideDistValue(boxPos, g) + getMinimumGoalSideDistValue(boxPos2, g2);
            int newDist = getMinimumGoalSideDistValue(boxPos, g2) + getMinimumGoalSideDistValue(boxPos2, g);
            if (newDist < oldDist) {// || newDist == oldDist && prioForGoal[g2] < prioForGoal[g] && goalDist[boxPos][g2] < goalDist[boxPos2][g2]) {
                matchedGoal[box] = g2;
                matchedGoal[otherBox] = g;
            }
        }
    }


    private boolean match(int goal, boolean[] visited) {
        if (matchedBox[goal] == -1) { return true; }
        if (visited[goal]) { return false; }
        visited[goal] = true;
        int matchingBox = matchedBox[goal];
        int boxPos = boxCells[matchingBox];
        for (int newGoal = 0; newGoal < goalCnt; newGoal++) {
            if (getMinimumGoalSideDistValue(boxPos, newGoal) < INF) {
                if (match(newGoal, visited)) {
                    matchedBox[newGoal] = matchingBox;
                    matchedGoal[matchingBox] = newGoal;
                    return true;
                }
            }
        }
        return false;
    }

    public boolean performBoxMove(int boxMove) {
        int dir = boxMove & 3;
        int oldBoxPos = boxMove >> 2;
        int newBoxPos = oldBoxPos + dx[dir];
        int newPlayerPos = newBoxPos + dx[dir];

        if (!isBox(oldBoxPos)) {
            throw new RuntimeException("Should be a box position");
        }
        moveBox(oldBoxPos, newBoxPos);
        movedBoxesCnt++;
        previousMove = new StackEntry(dir | oldBoxPos << 2, previousMove);
        currentReachableBoxDir[getBoxNumber(newBoxPos)] = dir;
        movePlayer(newPlayerPos);
        return true;
    }


    public int[] getPossibleBoxJumpMoves() {
        return possibleBoxJumpMoves;
    }

    /*
     * Teleports the player to the given position. No error checking done!
     * Should only use positions given by getPossibleBoxJumpMoves()
     */
    public boolean performJump(int pos) {
        //        previousMove = new StackEntry(8 | playerPos << 4, previousMove);
        movePlayer(pos);
        return true;
    }

    /*
     * Determines if the move does not create an unsolvable situation
     * TODO Check for s-formation:
     *           $#
     *          #$
     */
    public boolean isGoodMove(int direction) {
        int newPos = playerPos + dx[direction];
        return isFree(newPos);
        //        boolean good = false;
        //        if (isBox(newPos)) {
        //            int newPos2 = newPos + dx[direction];
        //            if (isFree(newPos2) && !isTrappingCell(newPos2)) {
        //                good = true;
        //                moveBox(newPos, newPos2);
        //                good &= checkIfValidBox(newPos2 - width - 1);
        //                good &= checkIfValidBox(newPos2 - width);
        //                good &= checkIfValidBox(newPos2 - 1);
        //                good &= checkIfValidBox(newPos2);
        //                moveBox(newPos2, newPos);
        //            }
        //        }
        //        return good;
    }

    /*
     * Checks if the 2x2 box with top-left corner at (row, col) is valid, that is that it isn't
     * completely filled with walls/boxes or that every box is at a goal
     */
    private boolean checkIfValidBox(int pos) {
        boolean unmatchedBox = false;
        for (int posDiff1 = 0; posDiff1 <= width; posDiff1 += width) {
            for (int posDiff2 = 0; posDiff2 <= 1; posDiff2++) {
                if (isFree(pos + posDiff1 + posDiff2)) { return true; }
                unmatchedBox |= (board[pos + posDiff1 + posDiff2] & 15) == BOX;
            }
        }
        return !unmatchedBox;
    }

    /*
     * previousMove has the following format (bits 0-indexed):
     * Bits 0 and 1 together contain the direction of the move
     * Bit 2 decides whether a board was pushed by the move or not
     * If bit 3 is set, the move was a jump, and bits 0-2 can be ignored.
     * Bits 4-21 contain the position that the jump was made from
     * Bits 22 and up determine how many more moves that should be reversed at the same time
     */
    public boolean reverseMove() {
        if (previousMove == null) { return false; }
        int prevBoxPos = previousMove.val >>> 2;
        int dir = previousMove.val & 3;
        int currentBoxPos = prevBoxPos + dx[dir];

        StackEntry nextPrev = previousMove.prev;
        if (nextPrev != null) {
            int nextPrevBoxPos = nextPrev.val >>> 2;
            int nextPrevDir = nextPrev.val & 3;
            int prevPlayerPos = nextPrevBoxPos + dx[nextPrevDir] + dx[nextPrevDir];
            movePlayer(prevPlayerPos);
        }

        moveBox(currentBoxPos, prevBoxPos);

        currentReachableBoxDir[getBoxNumber(prevBoxPos)] = dir;
        movedBoxesCnt--;
        previousMove = nextPrev;
        return true;
    }

    public boolean reverseMove(int[] board, int prevMoveVal) {
        int prevBoxPos = prevMoveVal >>> 2;
        int dir = prevMoveVal & 3;
        int currentBoxPos = prevBoxPos + dx[dir];

        //        int nextPrevBoxPos = prevPrevMoveVal >>> 2;
        //        int nextPrevDir = prevPrevMoveVal & 3;
        //        int prevPlayerPos = nextPrevBoxPos + dx[nextPrevDir] + dx[nextPrevDir];
        //        movePlayer(board,prevPlayerPos);

        moveBox(board, currentBoxPos, prevBoxPos);

        //        previousMove = nextPrev;
        return true;
    }

    public int directionLastMove() {
        if (previousMove == null) { return -1; }
        return previousMove.val & 3;
    }

    public boolean movedBoxLastMove() {
        if (previousMove == null) { return false; }
        return (previousMove.val & 4) != 0;
    }

    /*
     * Resets the board to the starting position and returns the path that was taken
     */
    public String backtrackPath() {

        int endingPlayerPos = playerPos;
        String firstJumpMoves = findFirstJumpMovesDFS(new boolean[totalSize], startingPlayerPos, endingPlayerPos);
        StringBuilder sb = new StringBuilder();
        if (firstJumpMoves != null) {
            sb.append(firstJumpMoves);
        }
        while (previousMove != null) {
            int prevBoxPos = previousMove.val >>> 2;
            int prevDir = previousMove.val & 3;
            sb.append(directionCharacters[getOppositeDirection(prevDir)]);

            int prevPlayerPos = prevBoxPos + dx[prevDir];
            int nextPrevPlayerPos = -1;

            StackEntry nextPrev = previousMove.prev;
            if (nextPrev != null) {
                int nextPrevBoxPos = nextPrev.val >>> 2;
                int nextPrevDir = nextPrev.val & 3;
                nextPrevPlayerPos = nextPrevBoxPos + dx[nextPrevDir] + dx[nextPrevDir];
            }
            reverseMove();


            if (nextPrevPlayerPos != -1) {
                int startPos = nextPrevPlayerPos;
                int endPos = prevPlayerPos;
                backtrackPathBFS(board, startPos, endPos, sb);
            }
        }
        return sb.toString();
    }

    private void backtrackPathBFS(int[] board, int startPos, int endPos, StringBuilder sb) {

        int[] prev = new int[totalSize];
        Arrays.fill(prev, -2);
        LinkedList<Integer> q = new LinkedList<Integer>();
        q.add(startPos);
        prev[startPos] = -1;
        while (!q.isEmpty()) {
            int pos = q.removeFirst();
            if (pos == endPos) {
                int tempPlayerPos = endPos;
                while (prev[tempPlayerPos] != -1) {
                    int dir = prev[tempPlayerPos];
                    sb.append(directionCharacters[getOppositeDirection(dir)]);
                    tempPlayerPos += dx[getOppositeDirection(dir)];
                }
                break;
            }
            for (int dir = 0; dir < 4; dir++) {
                int newPos = pos + dx[dir];
                if (isFree(board, newPos) && prev[newPos] == -2) {
                    prev[newPos] = dir;
                    q.add(newPos);
                }
            }
        }
    }

    public String backtrackPathFromHash(int[] board, long prime) {
        long hashCode = boardStateForwards.getHashForBoard(board, prime, dx);
        int[] keyValues = gameStateHash.get(hashCode);
        StringBuilder sb = new StringBuilder();
        int previousMoveVal = keyValues[2];
        int startPos = -1;
        int endPos = -1;
        while (previousMoveVal != -1) {

            int prevBoxPos = previousMoveVal >>> 2;
            int prevDir = previousMoveVal & 3;
            int prevPlayerPos = prevBoxPos + dx[prevDir];
            startPos = prevPlayerPos + dx[prevDir];

            if (endPos != -1) {
                backtrackPathBFS(board, startPos, endPos, sb);
            }
            sb.append(directionCharacters[getOppositeDirection(prevDir)]);
            endPos = prevPlayerPos;

            reverseMove(board, previousMoveVal);
            for (int i = 0; i < board.length; i++) {
                board[i] &= ~PLAYER;
            }
            board[prevPlayerPos] |= PLAYER;

            hashCode = boardStateForwards.getHashForBoard(board, prime, dx);
            keyValues = gameStateHash.get(hashCode);
            if (keyValues != null) {
                previousMoveVal = keyValues[2];
            } else {
                previousMoveVal = -1;
            }
            //HASH!
        }
        return sb.toString();
    }


    private String findFirstJumpMovesDFS(boolean[] visitedCells, int currentPlayerPos, int endingPlayerPos) {
        String result = null;
        for (int dir = 0; dir < 4; dir++) {
            int newPos = currentPlayerPos + dx[dir];
            //note that all boxes are on goals here. So goal cells = box cells = not free cells
            if (isFree(newPos) && !visitedCells[newPos]) {
                visitedCells[newPos] = true;
                char currentStep = directionCharacters[dir];
                if (newPos == endingPlayerPos) {
                    return String.valueOf(currentStep);
                } else {
                    result = findFirstJumpMovesDFS(visitedCells, newPos, endingPlayerPos);
                    if (result != null) {
                        return currentStep + result;
                    }
                }
            }
        }
        return result;
    }

    /*
     * Helper method that does not do error checking
     */
    private void moveBox(int oldPos, int newPos) {
        board[oldPos] &= ~BOX;
        board[newPos] |= BOX;
        board[newPos] |= -16 & board[oldPos];
        board[oldPos] &= 15;
    }

    private void moveBox(int[] board, int oldPos, int newPos) {
        board[oldPos] &= ~BOX;
        board[newPos] |= BOX;
        board[newPos] |= -16 & board[oldPos];
        board[oldPos] &= 15;
    }

    /*
     * Helper method that does not do error checking
     */
    private void movePlayer(int newPos) {
        board[playerPos] &= ~PLAYER;
        board[newPos] |= PLAYER;
        playerPos = newPos;
    }

    public boolean hashCurrentBoardState(int currentIteration) {
        boolean good = false;
        for (long prime : BoardState.HASH_PRIMES) {
            long hashCode = getHashCode(playerAndBoxesHashCells, prime);

            int savedPreviousMove = -1;
            if (previousMove != null) {
                savedPreviousMove = previousMove.val;
            }

            int[] cashedDepthInfo = gameStateHash.get(hashCode);
            if (cashedDepthInfo != null) {
                int minMovedBoxes = cashedDepthInfo[0];
                int prevIteration = cashedDepthInfo[1];
                if (minMovedBoxes > movedBoxesCnt || minMovedBoxes == movedBoxesCnt && currentIteration != prevIteration) {
                    // We have been here before but with a bigger depth or in a previous iteration
                    cashedDepthInfo[0] = movedBoxesCnt;
                    cashedDepthInfo[1] = currentIteration;
                    cashedDepthInfo[2] = savedPreviousMove;
                    good = true;
                }
            } else {
                gameStateHash.put(hashCode, new int[]{ movedBoxesCnt, currentIteration, savedPreviousMove });
                good = true;
            }
        }
        return good;
    }

    private long getHashCode(int[] array, long prime) {
        long hash = 0;
        for (int i = 0; i < array.length; i++) {
            hash = hash * prime + array[i];
        }
        return hash;
    }

    // TODO This should be updated while moving (maybe)
    public int getBoardValue() {
        int res = movedBoxesCnt;
        for (int box = 0; box < boxCnt; box++) {
            if (matchedGoal[box] == -1) { return INF; }
            res += getGoalSideDistValue(boxCells[box], matchedGoal[box]);
        }
        return res;
    }

    public static int getOppositeDirection(int direction) {
        return (direction + 2) & 3;
    }

    public boolean onBorder(int pos) {
        return pos < width || pos >= totalSize - width || pos % width == 0 || pos % width == width - 1;
    }

    public boolean isTrappingCell(int pos) {
        return trappingCells[pos];
    }

    public boolean isWall(int pos) {
        return board[pos] == WALL;
    }

    //    public boolean isWallOrTemporaryWall(int pos) {
    //        return board[pos] == WALL || temporaryWall[pos];
    //    }

    public boolean isGoal(int pos) {
        return (board[pos] & GOAL) != 0;
    }

    public boolean isBox(int pos) {
        return (board[pos] & BOX) != 0;
    }

    public boolean isBoxInDirection(int direction) {
        return isBox(playerPos + dx[direction]);
    }

    public int getBoxIndexInDirection(int direction) {
        return getBoxNumber(playerPos + dx[direction]);
    }

    public boolean isPlayer(int pos) {
        return (board[pos] & PLAYER) == PLAYER;
    }

    public boolean isFree(int pos) {
        return (board[pos] & NOT_FREE) == 0;
    }

    private static boolean isFree(int[] board, int pos) {
        return (board[pos] & NOT_FREE) == 0;
    }

    public boolean isDenseBoard() {
        return boardDensity > DENSE_BOARD_LIMIT;
    }

    public double getBoardDensity() {
        return boardDensity;
    }

    // TODO this should be updated while moving
    public boolean isBoardSolved() {
        for (int goal : goalCells) {
            if (!((board[goal] & 15) == BOX_ON_GOAL)) {
                return false;
            }
        }
        return getHashCode(playerAndBoxesHashCells, BoardState.HASH_PRIMES[0]) == startingPositionHash;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int pos = 0; pos < totalSize; pos++) {
            //            if ((tunnels[pos] & DEAD_END) == DEAD_END) {
            //                sb.append("\033[41m");
            //            } else if ((tunnels[pos] & TUNNEL) == TUNNEL) {
            //                sb.append("\033[43m");
            //            } else if ((tunnels[pos] & ROOM) == ROOM) {
            //                sb.append("\033[42m");
            //            }
            sb.append(boardCharacters[board[pos] & 15]);
            //            if ((tunnels[pos] & TUNNEL) == TUNNEL || (tunnels[pos] & ROOM) == ROOM) {
            //                sb.append("\033[0m");
            //            }
            if (pos % width == width - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    public String temporaryWallsToString() {
        StringBuilder sb = new StringBuilder();
        for (int pos = 0; pos < totalSize; pos++) {
            //            if (temporaryWall[pos]) {
            //                sb.append('T');

            sb.append(boardCharacters[board[pos] & 15]);
            //            }
            if (pos % width == width - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    public boolean isFirstStep() {
        return previousMove == null;
    }

    public int getPosFromPlayerInDirection(int dir) {
        return playerPos + dx[dir];
    }

    static class StackEntry {
        int        val;
        StackEntry prev;

        public StackEntry(int val, StackEntry prev) {
            this.val = val;
            this.prev = prev;
        }
    }

    public int getBoxNumber(int pos) {
        return board[pos] >>> 4;
    }

    public HashMap<Long, int[]> getGameStateHash() {
        return gameStateHash;
    }

    public void setBoardStateForwards(BoardState boardStateForwards) {
        this.boardStateForwards = boardStateForwards;
    }

    public String getPathWithForwards() {
        return pathWithForwards;
    }
}
