import java.util.*;

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
    private int[]      matchedGoal;
    private int[]      matchedBox;

    private int[]      possibleBoxJumpMoves;
    private int[]      initialPossibleJumpPositions;
    private int[]      tunnels;
    private int[]  goalsInPrioOrder;
    private int[]  prioForGoal;
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
    private ArrayList<Integer> possibleStartingPos;

    private ArrayList<int[]> possibleGoalsInPrioOrder;
    private ArrayList<int[]> possiblePrioForGoal;
    private ArrayList<int[]> possibleMatchedGoal;
    private ArrayList<int[]> possibleMatchedBox;
    private ArrayList<int[]>     possibleCurrentReachableBoxDir;

    public int pathFromHashCnt = 0;
    public int pathFromHashSuccessCnt = 0;

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
                    movePlayer(playerPos);
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
        }

        playerAndBoxesHashCells[boxCnt] = totalSize + mostUpLeftPos;

        if ((tunnels[playerPos] & TUNNEL) == TUNNEL) {

            if (movedBoxLastMove()) {
                int dir = directionLastMove();
                int boxPos = playerPos + dx[getOppositeDirection(dir)];
                if ((tunnels[boxPos] & TUNNEL) == TUNNEL || tunnels[boxPos] == ROOM) { //TODO: Maybe should pull box out of tunnel
                    if (!isGoal(boxPos)) {
                        if(isFree(playerPos + dx[dir])){
                            possibleBoxJumpMoves = new int[] {dir | boxPos << 2};
                        }else{
                            possibleBoxJumpMoves = new int[0];
                        }
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
            if (aggressive && lastMovedBoxIndex != -1 && box != lastMovedBoxIndex && getMinimumGoalSideDistValue(boxCells[lastMovedBoxIndex], matchedGoal[lastMovedBoxIndex]) != 0) {
                continue;
            }

            //Altered for backwards
            for (int dir = 0; dir < 4; dir++) {
                int newPos = boxPos + dx[dir];
                if (isFree(newPos)) {
                    int newPos2 = newPos + dx[dir];
                    if (isFree(newPos2)) {
                        if (1 == boardSections[newPos]) {
                            boxMoves.add(dir | boxPos << 2);
                        }
                    }
                }
            }
        }

        Collections.shuffle(boxMoves);

        //TODO: Test ignore convert to array and just go with arraylist
        possibleBoxJumpMoves = new int[boxMoves.size()];
        int i = 0;
        for (int move : boxMoves) {
            possibleBoxJumpMoves[i++] = move;
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

    private void analyzeBoardDfsSimple(int pos, int[] boardSections) {
        boardSections[pos] = 1;
        for (int dir = 0; dir < 4; dir++) {
            int newPos = pos + dx[dir];
            if (isFree(newPos) && boardSections[newPos] == 0) {

                analyzeBoardDfsSimple(newPos, boardSections);
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

        computeTunnels();


        possibleStartingPos = new ArrayList<Integer>();
        possiblePrioForGoal = new ArrayList<int[]>();
        possibleGoalsInPrioOrder = new ArrayList<int[]>();
        possibleMatchedBox = new ArrayList<int[]>();
        possibleMatchedGoal = new ArrayList<int[]>();
        possibleCurrentReachableBoxDir = new ArrayList<int[]>();
        int[] boardSection = new int[totalSize];
        ArrayList<Integer> tempPossibleStartingPos = new ArrayList<Integer>();
        ArrayList<int[]> tempPossibleCurrentReachableBoxDir = new ArrayList<int[]>();
        for (int i = 0; i < totalSize; i++) {
            if (isFree(i) && boardSection[i] == 0) {
                analyzeBoardDfsSimple(i, boardSection);
                tempPossibleStartingPos.add(i);
                boolean[] visited = new boolean[totalSize];
                int[] tmpCurrentReachableBoxDir = new int[boxCnt];
                analyzeCurrentBoxDirDFS(i, visited, tmpCurrentReachableBoxDir);
                tempPossibleCurrentReachableBoxDir.add(tmpCurrentReachableBoxDir);
            }
        }
        for (int i = 0; i < tempPossibleStartingPos.size(); i++) {
            int tempStartingPos = tempPossibleStartingPos.get(i);
            movePlayer(tempStartingPos);
            try {
                currentReachableBoxDir = tempPossibleCurrentReachableBoxDir.get(i);
                initializeBoxToGoalMapping();
                if(getBoardValue() < INF){
                possibleStartingPos.add(tempStartingPos);
                possibleGoalsInPrioOrder.add(goalsInPrioOrder);
                possiblePrioForGoal.add(prioForGoal);
                possibleMatchedGoal.add(matchedGoal);
                possibleMatchedBox.add(matchedBox);
                possibleCurrentReachableBoxDir.add(tempPossibleCurrentReachableBoxDir.get(i));
//                    System.out.println(Arrays.toString(goalsInPrioOrder));
//                    System.out.println(Arrays.toString(prioForGoal));
//                    System.out.println(Arrays.toString(matchedGoal));
//                    System.out.println(Arrays.toString(matchedBox));
//                    System.out.println(Arrays.toString(currentReachableBoxDir));
                }
            }
            catch (ArrayIndexOutOfBoundsException e) {

            }
        }


        //        analyzeBoard(false);
    }

    private void analyzeCurrentBoxDirDFS(int pos, boolean[] visited, int[] tmpCurrentReachableBoxDir) {
        visited[pos] = true;
        for(int dir = 0; dir < 4; dir++){
            int newPos = pos + dx[dir];
            if(!isWall(newPos) && !visited[newPos]){
                analyzeCurrentBoxDirDFS(newPos, visited, tmpCurrentReachableBoxDir);
                if(isBox(newPos)){
                    int boxIndex = getBoxNumber(newPos);
                    tmpCurrentReachableBoxDir[boxIndex] = getOppositeDirection(dir);
                }
            }
        }
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
            updateMatchingForBox(box, boxCells[box]);
        }
    }

    private int updateMatchingForBox(int box, int boxPos) {
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
                return otherBox;
            }
        }
        return -1;
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
        currentReachableBoxDir[getBoxNumber(newBoxPos)] = dir;
        movePlayer(newPlayerPos);

        int matchSwitchBox = updateMatchingForBox(getBoxNumber(newBoxPos), newBoxPos);
        previousMove = new StackEntry(dir | oldBoxPos << 2 | matchSwitchBox << 17, previousMove);

        return true;
    }


    public int[] getPossibleBoxJumpMoves() {
        return possibleBoxJumpMoves;
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
        int prevBoxPos = (previousMove.val >>> 2) & ((1 << 15) -1);
        int dir = previousMove.val & 3;
        int currentBoxPos = prevBoxPos + dx[dir];

        StackEntry nextPrev = previousMove.prev;
        if (nextPrev != null) {
            int nextPrevBoxPos = (nextPrev.val >>> 2) & ((1 << 15) -1);
            int nextPrevDir = nextPrev.val & 3;
            int prevPlayerPos = nextPrevBoxPos + dx[nextPrevDir] + dx[nextPrevDir];
            movePlayer(prevPlayerPos);
        }

        moveBox(currentBoxPos, prevBoxPos);


        currentReachableBoxDir[getBoxNumber(prevBoxPos)] = dir;
        movedBoxesCnt--;
        int switchedBoxIndex = previousMove.val >> 17;
        if(switchedBoxIndex != -1){
            int movedBoxIndex = getBoxNumber(prevBoxPos);
            int g = matchedGoal[movedBoxIndex];
            int g2 = matchedGoal[switchedBoxIndex];
            matchedGoal[movedBoxIndex] = g2;
            matchedGoal[switchedBoxIndex] = g;
        }
        previousMove = nextPrev;
        return true;
    }

    public boolean reverseMove(int[] board, int prevMoveVal) {
        int prevBoxPos = boxPosLastMove(prevMoveVal);
        int dir = prevMoveVal & 3;
        int currentBoxPos = prevBoxPos + dx[dir];
        int currentPlayerPos = currentBoxPos + dx[dir];
        if (!isFree(board, currentPlayerPos) || !isBox(board, currentBoxPos) || !isFree(board, prevBoxPos)) {
            return false;
        }

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
        return true;
    }

    public int boxPosLastMove(){
        if (previousMove == null) { return -1; }
        return (previousMove.val >>> 2) & ((1 << 15) -1);
    }
    public static int boxPosLastMove(int previousMoveVal){
        if (previousMoveVal == -1) { return -1; }
        return (previousMoveVal >>> 2) & ((1 << 15) -1);
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
            int prevBoxPos =boxPosLastMove();
            int prevDir = directionLastMove();
            sb.append(directionCharacters[getOppositeDirection(prevDir)]);

            int prevPlayerPos = prevBoxPos + dx[prevDir];
            int nextPrevPlayerPos = -1;

            StackEntry nextPrev = previousMove.prev;
            if (nextPrev != null) {
                int nextPrevBoxPos = boxPosLastMove(nextPrev.val);
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

            int prevBoxPos = boxPosLastMove(previousMoveVal);
            int prevDir = previousMoveVal & 3;
            int prevPlayerPos = prevBoxPos + dx[prevDir];
            startPos = prevPlayerPos + dx[prevDir];

            if (endPos != -1) {
                backtrackPathBFS(board, startPos, endPos, sb);
            }
            sb.append(directionCharacters[getOppositeDirection(prevDir)]);
            endPos = prevPlayerPos;

            if (!reverseMove(board, previousMoveVal)) {
                return "";
            }
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
        long[] hashes = new long[BoardState.HASH_PRIMES.length];
        int savedPreviousMove = -1;
        if (previousMove != null) {
            savedPreviousMove = previousMove.val;
        }
        for (int i = 0; i < hashes.length; i++) {
            long prime = BoardState.HASH_PRIMES[i];
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
        if (boardStateForwards != null) {
            for (long hash : hashes) {
                if (boardStateForwards.getGameStateHash().get(hash) == null) {
                    return true;
                }
            }
            pathFromHashCnt++;
            for (long prime : BoardState.HASH_PRIMES) {
                if (pathWithForwards == null) {
                    //We found our way home! Probably...
                    int[] boardCopy = new int[board.length];
                    for (int i = 0; i < board.length; i++) {
                        boardCopy[i] = board[i];
                    }
                    String forwardPath = boardStateForwards.backtrackPathFromHash(boardCopy, prime);

                    long hashCode = getHashCode(playerAndBoxesHashCells, prime);
                    int[] forwardHashKey = boardStateForwards.getGameStateHash().get(hashCode);

                    int forwardPathPrevBoxMove = forwardHashKey[2];
                    int forwardBoxPos = forwardPathPrevBoxMove >>> 2;
                    int forwardDir = forwardPathPrevBoxMove & 3;
                    int forwardPlayerPos = forwardBoxPos;

                    StringBuilder tmpSB = new StringBuilder();
                    backtrackPathBFS(board, playerPos, forwardPlayerPos, tmpSB);
                    String connectionPath = tmpSB.toString();

                    int[] boardCopy2 = new int[board.length];
                    for (int i = 0; i < board.length; i++) {
                        boardCopy2[i] = board[i];
                    }
                    String backwardPath = backtrackPathFromHash(boardCopy2, prime);

                    pathWithForwards = forwardPath + connectionPath + backwardPath;
                    if (!Main.investigatePath(pathWithForwards)) {
                        pathWithForwards = null;
                    } else {
                        pathFromHashSuccessCnt++;
                    }
                }
            }
            if (pathWithForwards != null) { return true; }
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

    public boolean isWall(int pos) {
        return board[pos] == WALL;
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
            sb.append(boardCharacters[board[pos] & 15]);
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

    public ArrayList<Integer> getPossibleStartingPos() {
        return possibleStartingPos;
    }

    public void updateInitialStartingPos(int startingPosIndex) {
        movePlayer(possibleStartingPos.get(startingPosIndex));
        goalsInPrioOrder = possibleGoalsInPrioOrder.get(startingPosIndex);
        prioForGoal = possiblePrioForGoal.get(startingPosIndex);
        matchedGoal = possibleMatchedGoal.get(startingPosIndex);
        matchedBox = possibleMatchedBox.get(startingPosIndex);
        currentReachableBoxDir = possibleCurrentReachableBoxDir.get(startingPosIndex);
//        System.out.println();
//        System.out.println(possibleStartingPos.get(startingPosIndex));
//        System.out.println(Arrays.toString(possibleGoalsInPrioOrder.get(startingPosIndex)));
//        System.out.println(Arrays.toString(possiblePrioForGoal.get(startingPosIndex)));
//        System.out.println(Arrays.toString(possibleMatchedGoal.get(startingPosIndex)));
//        System.out.println(Arrays.toString(possibleMatchedBox.get(startingPosIndex)));
//        System.out.println(Arrays.toString(possibleCurrentReachableBoxDir.get(startingPosIndex)));

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
