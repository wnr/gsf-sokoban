import java.util.*;

public class BoardState {

    public static final int    INF               = 100000000;
    public static final double DENSE_BOARD_LIMIT = 0.13;

    public static long[] HASH_PRIMES= {47, 6719};

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
        characterMapping.put(GOAL_CHAR, GOAL);
        characterMapping.put(WALL_CHAR, WALL);
        characterMapping.put(PLAYER_CHAR, PLAYER);
        characterMapping.put(PLAYER_ON_GOAL_CHAR, PLAYER_ON_GOAL);
        characterMapping.put(BOX_CHAR, BOX);
        characterMapping.put(BOX_ON_GOAL_CHAR, BOX_ON_GOAL);
    }

    // Vectors corresponding to the moves {up, right, down, left}
    private static int[]  dr                  = { -1, 0, 1, 0 };
    private static int[]  dc                  = { 0, 1, 0, -1 };
    private static char[] directionCharacters = { 'U', 'R', 'D', 'L' };

    // New awesome move vector
    private int[] dx;

    private int width, height, totalSize;
    private int playerPos, initialPlayerPos;
    public int goalCnt, boxCnt;
    private int freeCellCount;

    private StackEntry previousMove;
    private int[]      board;
    private int[]      boxCells;
    private int[]      goalCells;
    private boolean[]  trappingCells;
    private boolean[]  temporaryWall;
    private int[]      matchedGoal;
    private int[]      matchedBox;
    private int[]      possibleBoxMoves;
    private int[]      tunnels;
    private int[]      goalsInPrioOrder;
    private int[]      prioForGoal;
    private int        movedBoxesCnt;
    private int[][]    goalSideDist;
    private int[]      boxReachableSideIndex;
    private int[]      currentReachableBoxDir;

    private double boardDensity;

    private int[]                playerAndBoxesHashCells;
    private HashMap<Long, int[]> gameStateHash;

    private BoardStateBackwards boardStateBackwards;


    private String pathWithBackwards;

    int mostUpLeftPos;

    public int pathFromHashCnt = 0;
    public int pathFromHashSuccessCnt = 0;

    public BoardState(List<String> lines) {
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
                if (isPlayer(row * width + col)) {
                    playerPos = row * width + col;
                    initialPlayerPos = playerPos;
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

        boolean[] visited = new boolean[totalSize];
        setOutsideSpaceDFS(playerPos, visited);
        for (int pos = 0; pos < totalSize; pos++) {
            if (isFree(pos) && !visited[pos]) {
                board[pos] = WALL;
            }
        }

        freeCellCount = 0;
        // Calculate board density
        for (int pos = 0; pos < totalSize; pos++) {
            if (isFree(pos)) {
                freeCellCount++;
            }
        }
        boardDensity = ((double) boxCnt) / (boxCnt + freeCellCount);

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

            // Deadlock check needs to be more effective to be worth it right now
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
            //                possibleBoxMoves = null;
            //                return;
            //            }

            addTemporaryWallsDfs(lastMovedBoxPos);
            updateMatchingForBox(lastMovedBoxIndex);

            if (temporaryWall[lastMovedBoxPos]) {
                if (!checkIfGoalsStillReachable()) {
                    possibleBoxMoves = null;
                    return;
                }
            }
        }

        playerAndBoxesHashCells[boxCnt] = totalSize + mostUpLeftPos;

        int onlyPushBox = -1;

        if ((tunnels[playerPos] & TUNNEL) == TUNNEL) {
            int dir = directionLastMove();

            if (movedBoxLastMove()) {
                int boxPos = playerPos + dx[dir];
                if ((tunnels[boxPos] & TUNNEL) == TUNNEL || tunnels[boxPos] == ROOM) {
                    if (!isGoal(boxPos)) {
                        onlyPushBox = boxPos;
                    }
                }
            }
        }
        LinkedList<Integer> moves = new LinkedList<Integer>();
        //        if (onlyPushBox == -1 && lastMovedBoxPos != -1) {
        //            addMovesForBox(lastMovedBoxPos, boardSections, moves);
        //        }
        for (int i = 0; i < goalCnt; i++) {
            int goal = goalsInPrioOrder[i];
            int box = matchedBox[goal];
            int boxPos = boxCells[box];
            //            if (boxPos == lastMovedBoxPos) continue;
            if (onlyPushBox != -1 && onlyPushBox != boxPos) { continue; }
            if (aggressive && lastMovedBoxIndex != -1 && box != lastMovedBoxIndex && getGoalSideDistValue(boxCells[lastMovedBoxIndex], matchedGoal[lastMovedBoxIndex]) != 0) {
                continue;
            }
            addMovesForBox(boxPos, boardSections, moves);
        }

        Collections.shuffle(moves);

        possibleBoxMoves = new int[moves.size()];
        int i = 0;
        for (int move : moves) {
            possibleBoxMoves[i++] = move;
        }
    }

    private void addMovesForBox(int boxPos, int[] boardSections, LinkedList<Integer> moves) {
        for (int dir = 0; dir < 2; dir++) {
            int newPos = boxPos + dx[dir];
            if (isFree(newPos)) {
                int newPos2 = boxPos + dx[dir + 2];
                if (isFree(newPos2)) {
                    if (boardSections[newPos] == 1) {
                        int move = (boxPos << 2) + dir + 2;
                        if (isGoodMove(move)) {
                            moves.add(move);
                        }
                    }
                    if (boardSections[newPos2] == 1) {
                        int move = (boxPos << 2) + dir;
                        if (isGoodMove(move)) {
                            moves.add(move);
                        }
                    }
                }
            }
        }
    }

    public boolean isDeadLock() {
        boolean[] reachable = new boolean[totalSize];
        checkDeadlockDfs(playerPos, reachable);
        boolean deadLock = false;
        for (int box = 0; box < boxCnt; box++) {
            int boxPos = boxCells[box];
            int goal = matchedGoal[box];
            if (!isGoal(boxPos)) {
                boolean good = false;
                for (int dir = 0; dir < 4; dir++) {
                    int pos = boxPos + dx[dir];
                    int oppPos = boxPos + dx[getOppositeDirection(dir)];
                    if (reachable[pos] && !trappingCells[oppPos] && (isFree(oppPos) || isBox(oppPos) && reachable[oppPos])) {
                        good = true;
                    }
                }
                if (!good) { deadLock = true; }
            }
        }
        return deadLock;
    }

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

    public boolean moveLatestBoxToGoalIfPossible() {
        if (!movedBoxLastMove()) { return false; }
        int boxPos = playerPos + dx[directionLastMove()];
        int boxIndex = getBoxNumber(boxPos);
        int goal = matchedGoal[boxIndex];
        if (getGoalSideDistValue(boxPos, goal) == 0) { return false; }
        LinkedList<Integer> moves = new LinkedList<Integer>();
        board[boxPos] &= ~BOX;
        boolean possible = moveBoxToGoalDfs(playerPos, directionLastMove(), goal, moves);
        board[boxPos] |= BOX;
        if (possible) {
            for (int move : moves) {
                performBoxMove(move);
            }
            previousMove.val |= moves.size() << 22;
            return true;
        }
        return false;
    }

    private boolean moveBoxToGoalDfs(int pos, int forward, int goal, LinkedList<Integer> moves) {
        int boxPos = pos + dx[forward];
        if (getGoalSideDistValue(boxPos, goal) == 0) { return true; }
        int left = forward == 0 ? 3 : forward - 1;
        int right = (forward + 1) & 3;
        int backward = (forward + 2) & 3;
        boolean[] reachable = new boolean[4];
        reachable[backward] = true;

        // Try walking to the left of the box
        if (checkIfFreePath(pos, new int[]{ left, forward })) {
            reachable[left] = true;
            if (checkIfFreePath(boxPos + dx[left], new int[]{ forward, right })) {
                reachable[forward] = true;
                if (checkIfFreePath(boxPos + dx[forward], new int[]{ right, backward })) {
                    reachable[right] = true;
                }
            }
        }

        // Try walking to the right of the box
        if (checkIfFreePath(pos, new int[]{ right, forward })) {
            reachable[right] = true;
            if (checkIfFreePath(boxPos + dx[right], new int[]{ forward, left })) {
                reachable[forward] = true;
                if (checkIfFreePath(boxPos + dx[forward], new int[]{ left, backward })) {
                    reachable[left] = true;
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            if (reachable[i]) {
                int moveDir = (i + 2) & 3;
                int newBoxPos = boxPos + dx[moveDir];
                if (isFree(newBoxPos) && goalSideDist[4 * newBoxPos + i][goal] < goalSideDist[4 * boxPos + i][goal]) {
                    boolean possible = moveBoxToGoalDfs(boxPos, moveDir, goal, moves);
                    if (possible) {
                        moves.addFirst((boxPos << 2) + moveDir);
                    }
                    return possible;
                }
            }
        }
        return false;
    }

    private boolean checkIfFreePath(int pos, int[] moves) {
        if (!isFree(pos)) { return false; }
        for (int move : moves) {
            pos += dx[move];
            if (!isFree(pos)) { return false; }
        }
        return true;
    }

    private boolean checkIfGoalsStillReachable() {
        boolean[] reachable = new boolean[4 * totalSize];
        for (int box = 0; box < boxCnt; box++) {
            int boxPos = boxCells[box];
            int side = currentReachableBoxDir[box];
            if (!reachable[4 * boxPos + side]) {
                checkIfGoalsStillReachableDfs(boxPos, side, reachable);
            }
        }
        for (int goal = 0; goal < goalCnt; goal++) {
            boolean good = false;
            for (int dir = 0; dir < 4; dir++) {
                if (reachable[4 * goalCells[goal] + dir]) {
                    good = true;
                    break;
                }
            }
            if (!good) { return false; }
        }
        return true;
    }

    private void checkIfGoalsStillReachableDfs(int pos, int side, boolean[] visited) {
        int sideIndex = boxReachableSideIndex[4 * pos + side];
        for (int dir = 0; dir < 4; dir++) {
            if (!visited[4 * pos + dir] && boxReachableSideIndex[4 * pos + dir] == sideIndex) {
                visited[4 * pos + dir] = true;
                int newPos = pos + dx[getOppositeDirection(dir)];
                if (!isWallOrTemporaryWall(newPos) && !visited[4 * newPos + dir]) {
                    int oppPos = pos + dx[dir];
                    if (!isWallOrTemporaryWall(oppPos)) {
                        checkIfGoalsStillReachableDfs(newPos, dir, visited);
                    }
                }
            }
        }
    }

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
            //            goalDist[goalPos][i] = 0;
            LinkedList<Integer> q = new LinkedList<Integer>();
            q.add(goalPos);
            while (!q.isEmpty()) {
                int pos = q.removeFirst();
                for (int dir = 0; dir < 4; dir++) {
                    int newPos = pos + dx[dir];
                    int d = goalSideDist[pos * 4 + dir][i] + 1;
                    if (!isWall(newPos) && d < goalSideDist[newPos * 4 + dir][i]) {
                        int newPos2 = newPos + dx[dir];
                        if (!isWall(newPos2)) {


                            int boxSideZoneIndex = boxReachableSideIndex[newPos * 4 + dir];
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
        temporaryWall = new boolean[totalSize];
        for (int pos = 0; pos < totalSize; pos++) {
            if (isBox(pos) && !temporaryWall[pos]) {
                addTemporaryWallsDfs(pos);
            }
        }

        computeTunnels();
        initializeBoxToGoalMapping();
        analyzeBoard(false);
    }

    public String goalDistToString(int goal) {
        StringBuilder sb = new StringBuilder();
        sb.append("Goal distance matrices for goal: " + goal + '\n');
        for (int dir = 0; dir < 4; dir++) {
            for (int pos = 0; pos < board.length; pos++) {
                int value = goalSideDist[pos * 4 + dir][goal];
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
        }
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

    private void addTemporaryWallsDfs(int pos) {
        if (checkIfTemporaryWall(pos)) {
            temporaryWall[pos] = true;
            for (int dir = 0; dir < 4; dir++) {
                int newPos = pos + dx[dir];
                if (isBox(newPos) && !temporaryWall[newPos]) {
                    addTemporaryWallsDfs(newPos);
                }
            }
        }
    }

    private void removeTemporaryWallsDfs(int pos) {
        if (!checkIfTemporaryWall(pos)) {
            temporaryWall[pos] = false;
            for (int dir = 0; dir < 4; dir++) {
                int newPos = pos + dx[dir];
                if (isBox(newPos) && temporaryWall[newPos]) {
                    removeTemporaryWallsDfs(newPos);
                }
            }
        }
    }

    private boolean checkIfTemporaryWall(int pos) {
        if (!isBox(pos)) { return false; }
        if (isBlockedRectangle(pos) || isBlockedRectangle(pos - 1) || isBlockedRectangle(pos - width) || isBlockedRectangle(pos - width - 1)) {
            return true;
        }
        int blockedSides = 0;
        for (int dir = 0; dir < 2; dir++) {
            int oppDir = dir + 2;
            if (isWallOrTemporaryWall(pos + dx[dir]) || isWallOrTemporaryWall(pos + dx[oppDir])) {
                blockedSides++;
                int dir2 = dir + 1;
                int oppDir2 = (dir2 + 2) & 3;
                int otherPos = pos + dx[dir2];
                if (isBox(otherPos) && (isWallOrTemporaryWall(otherPos + dx[dir]) || isWallOrTemporaryWall(otherPos + dx[oppDir]))) {
                    return true;
                }
                otherPos = pos + dx[oppDir2];
                if (isBox(otherPos) && (isWallOrTemporaryWall(otherPos + dx[dir]) || isWallOrTemporaryWall(otherPos + dx[oppDir]))) {
                    return true;
                }
            }
        }
        return blockedSides == 2;
    }

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
                if (getGoalSideDistValue(boxCells[box], goal) < INF) {
                    distCnt++;
                    distSum += getGoalSideDistValue(boxCells[box], goal);
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
                    if (bestBox == -1 || getGoalSideDistValue(boxCells[box], goal) < getGoalSideDistValue(boxCells[bestBox], goal)) {
                        bestBox = box;
                    }
                }
            }
            if (getGoalSideDistValue(boxCells[bestBox], goal) < INF) {
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
                    if (getGoalSideDistValue(boxPos, goal) < INF) {
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
            updateMatchingForBox(box);
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

    private boolean match(int goal, boolean[] visited) {
        if (matchedBox[goal] == -1) { return true; }
        if (visited[goal]) { return false; }
        visited[goal] = true;
        int matchingBox = matchedBox[goal];
        int boxPos = boxCells[matchingBox];
        for (int newGoal = 0; newGoal < goalCnt; newGoal++) {
            if (getGoalSideDistValue(boxPos, newGoal) < INF) {
                if (match(newGoal, visited)) {
                    matchedBox[newGoal] = matchingBox;
                    matchedGoal[matchingBox] = newGoal;
                    return true;
                }
            }
        }
        return false;
    }

    public boolean performMove(int direction) {
        int newPos = playerPos + dx[direction];
        boolean successful = false;

        // We don't check if the move is outside the board since the player always is surrounded by walls
        if (isFree(newPos)) {
            movePlayer(newPos);
            successful = true;
            previousMove = new StackEntry(direction, previousMove);
        } else if (isBox(newPos)) {
            int newPos2 = newPos + dx[direction];
            if (isFree(newPos2)) {
                moveBox(newPos, newPos2);
                movePlayer(newPos);
                successful = true;
                movedBoxesCnt++;
                previousMove = new StackEntry(direction | 4, previousMove);
                currentReachableBoxDir[getBoxNumber(newPos2)] = getOppositeDirection(direction);
            }
        }
        return successful;
    }

    public boolean performBoxMove(int boxMove) {
        int boxPos = boxMove >>> 2;
        int dir = boxMove & 3;
        int newBoxPos = boxPos + dx[dir];
        moveBox(boxPos, newBoxPos);
        movePlayer(boxPos);
        previousMove = new StackEntry(boxMove, previousMove);
        movedBoxesCnt++;
        currentReachableBoxDir[getBoxNumber(newBoxPos)] = getOppositeDirection(dir);
        return true;
    }

    public int[] getPossibleBoxMoves() {
        return possibleBoxMoves;
    }

    /*
     * Determines if the move does not create an unsolvable situation
     * TODO Check for s-formation:
     *           $#
     *          #$
     */
    public boolean isGoodMove(int boxMove) {
        int boxPos = boxMove >>> 2;
        int dir = boxMove & 3;
        int newBoxPos = boxPos + dx[dir];
        if (isFree(newBoxPos) && !isTrappingCell(newBoxPos)) {
            boolean good = true;
            moveBox(boxPos, newBoxPos);
            good &= checkIfValidBox(newBoxPos - width - 1);
            good &= checkIfValidBox(newBoxPos - width);
            good &= checkIfValidBox(newBoxPos - 1);
            good &= checkIfValidBox(newBoxPos);
            moveBox(newBoxPos, boxPos);
            return good;
        } else {
            return false;
        }
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
     * Bits 2 and up determine the position of the box that was moved
     */
    public boolean reverseMove() {
        if (previousMove == null) { return false; }
        int reverseCount = (previousMove.val >>> 22) + 1;
        previousMove.val &= (1 << 22) - 1;
        for (int i = 0; i < reverseCount; i++) {
            int oldBoxPos = previousMove.val >>> 2;
            int dir = previousMove.val & 3;
            int newBoxPos = oldBoxPos + dx[dir];
            int oldPlayerPos = initialPlayerPos;
            if (previousMove.prev != null) {
                oldPlayerPos = (previousMove.prev.val & ((1 << 22) - 1)) >>> 2;
            }
            movePlayer(oldPlayerPos);
            moveBox(newBoxPos, oldBoxPos);
            if (temporaryWall[newBoxPos]) {
                removeTemporaryWallsDfs(newBoxPos);
            }
            updateMatchingForBox(getBoxNumber(oldBoxPos));
            movedBoxesCnt--;
            previousMove = previousMove.prev;
            currentReachableBoxDir[getBoxNumber(oldBoxPos)] = getOppositeDirection(dir);
        }
        return true;
    }

    public boolean reverseMove(int[] board, int moveVal) {
        int oldBoxPos = moveVal >>> 2;
        int dir = moveVal & 3;
        int newBoxPos = oldBoxPos + dx[dir];
        int oldPlayerPos = oldBoxPos + dx[getOppositeDirection(dir)];
        if (!isBox(board, newBoxPos) || !isFree(board, oldBoxPos) || !isFree(board, oldPlayerPos)) return false;
        moveBox(board, newBoxPos, oldBoxPos);
        return true;
    }

    public int directionLastMove() {
        if (previousMove == null) { return -1; }
        return previousMove.val & 3;
    }

    public boolean movedBoxLastMove() {
        if (previousMove == null) { return false; }
        return true;
    }

    /*
     * Resets the board to the starting position and returns the path that was taken
     */
    public String backtrackPath() {
        StringBuilder sb = new StringBuilder();
        while (previousMove != null) {
            previousMove.val &= (1 << 22) - 1;
            int boxPos = previousMove.val >>> 2;
            int dir = previousMove.val & 3;
            int newBoxPos = boxPos + dx[dir];
            int startPos = boxPos + dx[getOppositeDirection(dir)];
            int endPos = initialPlayerPos;
            if (previousMove.prev != null) {
                endPos = (previousMove.prev.val & ((1 << 22) - 1)) >>> 2;
            }
            sb.append(directionCharacters[dir]);
            moveBox(newBoxPos, boxPos);
            backtrackPathJumpBFS(board, endPos, startPos, sb);
            moveBox(boxPos, newBoxPos);
            reverseMove();
        }
        return sb.reverse().toString();
    }

    private void backtrackPathJumpBFS(int[] board, int startPos, int endPos, StringBuilder sb) {
        int[] prev = new int[board.length];
        Arrays.fill(prev, -2);
        LinkedList<Integer> q = new LinkedList<Integer>();
        q.add(startPos);
        prev[startPos] = -1;
        while (!q.isEmpty()) {
            int pos = q.removeFirst();
            if (pos == endPos) {
                int tempPos = endPos;
                while (prev[tempPos] != -1) {
                    int dir = prev[tempPos];
                    sb.append(directionCharacters[dir]);
                    tempPos += dx[getOppositeDirection(dir)];
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

    public String backtrackPathFromHash(int[] board, long prime) {
        long hashCode = getHashForBoard(board, prime, dx);
        int[] keyValues = gameStateHash.get(hashCode);
        StringBuilder sb = new StringBuilder();
        int previousMoveVal = keyValues[2];

        int startPos = -1;
        int endPos = -1;
        while (previousMoveVal != -1) {

            int prevBoxPos = previousMoveVal >>> 2;
            int prevDir = previousMoveVal & 3;
            int prevPlayerPos = prevBoxPos + dx[getOppositeDirection(prevDir)];
            startPos = prevBoxPos;

            if (endPos != -1) {
                backtrackPathJumpBFS(board, startPos, endPos, sb);
            }
            sb.append(directionCharacters[prevDir]);
            endPos = prevPlayerPos;

            if (!reverseMove(board, previousMoveVal)) {
                return "";
            }
            for (int i = 0; i < board.length; i++) {
                board[i] &= ~PLAYER;
            }
            board[prevPlayerPos] |= PLAYER;

            hashCode = getHashForBoard(board, prime, dx);
            keyValues = gameStateHash.get(hashCode);
            if (keyValues != null) {
                previousMoveVal = keyValues[2];
            } else {
                previousMoveVal = -1;
            }
        }
        // Add path from initial position
        if (endPos != -1) {
            backtrackPathJumpBFS(board, initialPlayerPos, endPos, sb);
        }
        return sb.reverse().toString();
    }

    public boolean hashCurrentBoardState(int currentIteration) {
        boolean good = false;
        long[] hashes = new long[HASH_PRIMES.length];
        int savedPreviousMove = -1;
        if (previousMove != null) {
            savedPreviousMove = previousMove.val;
        }
        for (int i = 0; i < hashes.length; i++) {
            long prime = HASH_PRIMES[i];
            hashes[i] = getHashCode(playerAndBoxesHashCells, prime);

            int[] cashedDepthInfo = gameStateHash.get(hashes[i]);
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
                gameStateHash.put(hashes[i], new int[]{ movedBoxesCnt, currentIteration, savedPreviousMove });
                good = true;
            }
        }
        if (!good) {
            return false;
        }

        // If we found a collision for all primes we want to check the bidirectional path
        if (boardStateBackwards != null) {
            for (long hash : hashes) {
                if (boardStateBackwards.getGameStateHash().get(hash) == null) {
                    return true;
                }
            }
            pathFromHashCnt++;
            for (long prime : HASH_PRIMES) {
                if (pathWithBackwards == null) {
                    //We found our way home! Probably...
                    int[] boardCopy = new int[board.length];
                    for (int i = 0; i < board.length; i++) {
                        boardCopy[i] = board[i];
                    }
                    String backwardsPath = boardStateBackwards.backtrackPathFromHash(boardCopy, prime);

                    long hashCode = getHashCode(playerAndBoxesHashCells, prime);
                    int[] backwardsHashKey = boardStateBackwards.getGameStateHash().get(hashCode);

                    int backwardsPathPrevBoxMove = backwardsHashKey[2];
                    int backwardsBoxPos = BoardStateBackwards.boxPosLastMove(backwardsPathPrevBoxMove);
                    int backwardsDir = backwardsPathPrevBoxMove & 3;
                    int backwardsPlayerPos = backwardsBoxPos + dx[backwardsDir] * 2;

                    int playerStartPos = initialPlayerPos;
                    if (previousMove != null) {
                        playerStartPos = previousMove.val >>> 2;
                    }
                    StringBuilder tmpSB = new StringBuilder();
                    backtrackPathJumpBFS(board, playerStartPos, backwardsPlayerPos, tmpSB);
                    String connectionPath = tmpSB.reverse().toString();

                    int[] boardCopy2 = new int[board.length];
                    for (int i = 0; i < board.length; i++) {
                        boardCopy2[i] = board[i];
                    }
                    String forwardPath = backtrackPathFromHash(boardCopy2, prime);

                    pathWithBackwards = forwardPath + connectionPath + backwardsPath;
                    if (!Main.investigatePath(pathWithBackwards)) {
                        pathWithBackwards = null;
                    } else {
                        pathFromHashSuccessCnt++;
                    }
                }
            }
        }
        return true;
    }

    private String buildPathFromHash(int backwardsPreviousMove) {
        int[] boardCopy = new int[board.length];
        for (int i = 0; i < board.length; i++) {
            boardCopy[i] = board[i];
        }
        return null;
    }

    public void clearCache() {
        gameStateHash.clear();
    }

    public static long getHashForBoard(int[] board, long prime, int[] dx) {
        long res = 0;
        int playerPos = -1;
        for (int i = 0; i < board.length; i++) {
            if ((board[i] & BOX) != 0) {
                res = res * prime + i;
            }
            if ((board[i] & PLAYER) != 0) {
                playerPos = i;
            }
        }
        boolean[] visited = new boolean[board.length];
        int mostUpLeftPos = getHashForBoardDfs(playerPos, board, dx, visited);
        res = res * prime + mostUpLeftPos + board.length;
        return res;
    }

    private static int getHashForBoardDfs(int pos, int[] board, int[] dx, boolean[] visited) {
        visited[pos] = true;
        int res = pos;
        for (int dir = 0; dir < 4; dir++) {
            int newPos = pos + dx[dir];
            if (isFree(board, newPos) && !visited[newPos]) {
                res = Math.min(res, getHashForBoardDfs(newPos, board, dx, visited));
            }
        }
        return res;
    }

    private static long getHashCode(int[] array, long prime) {
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

    public boolean isWallOrTemporaryWall(int pos) {
        return board[pos] == WALL || temporaryWall[pos];
    }

    public boolean isGoal(int pos) {
        return (board[pos] & GOAL) != 0;
    }

    public boolean isBox(int pos) {
        return (board[pos] & BOX) != 0;
    }

    public static boolean isBox(int[] board, int pos) {
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

    public static boolean isFree(int[] board, int pos) {
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
        return true;
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
            if (temporaryWall[pos]) {
                sb.append('T');
            } else {
                sb.append(boardCharacters[board[pos] & 15]);
            }
            if (pos % width == width - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
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

    public int getNumFree() {
        return freeCellCount + boxCnt;
    }

    public int getNumBoxes() {
        return boxCnt;
    }

    public int getNumTrappingCells() {
        int count = 0;

        for (int pos = 0; pos < totalSize; pos++) {
            if (trappingCells[pos]) {
                count++;
            }
        }

        return count;
    }

    private int getTunnelCount(int type) {
        int count = 0;

        for (int pos = width; pos < totalSize - width; pos++) {
            if ((tunnels[pos] & type) == type) {
                count++;
            }
        }

        return count;
    }

    public int getNumTunnels() {
        return getTunnelCount(TUNNEL);
    }

    public int getNumDeadEnds() {
        return getTunnelCount(DEAD_END);
    }

    public int getNumRoomsCells() {
        return getTunnelCount(ROOM);
    }

    public int getNumOpenings() {
        return getTunnelCount(OPENING);
    }

    public int getNumWalls() {
        int count = 0;

        for (int pos = 0; pos < totalSize; pos++) {
            if (isWall(pos)) {
                count++;
            }
        }

        return count;
    }

    public HashMap<Long, int[]> getGameStateHash() {
        return gameStateHash;
    }

    public void setBoardStateBackwards(BoardStateBackwards boardStateBackwards) {
        this.boardStateBackwards = boardStateBackwards;
    }

    public String getPathWithBackwards() {
        return pathWithBackwards;
    }
}
