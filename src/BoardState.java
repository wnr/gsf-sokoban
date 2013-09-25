import java.util.*;

public class BoardState {

    public static final int INF = 100000000;

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

    private int width, height;
    private int playerRow, playerCol;
    public int goalCnt, boxCnt;

    private StackEntry               previousMove;
    public  int                      playerCnt;
    private int[][]                  board;
    private int[][]                  boxCells;
    private int[][]                  goalCells;
    private int[][][]                goalDist;
    private boolean[][]              trappingCells;
    private int[]                    matchedGoal;

    private Set<Integer>         playerAndBoxesHashCells;
    private Map<Object, Integer> gameStateHash;


    public BoardState(List<String> lines) {
        height = lines.size();
        width = 0;
        for (String line : lines) {
            width = Math.max(width, line.length());
        }
        board = new int[height][width];
        int row = 0;
        List<int[]> tempBoxCells = new ArrayList<int[]>();
        List<int[]> tempGoalCells = new ArrayList<int[]>();
        for (String line : lines) {
            int col = 0;
            for (char cell : line.toCharArray()) {
                board[row][col] = characterMapping.get(cell);
                if (isPlayer(row, col)) {
                    playerCnt++;
                    playerRow = row;
                    playerCol = col;
                }
                if (isGoal(row, col)) {
                    tempGoalCells.add(new int[]{ row, col });
                    goalCnt++;
                }
                if (isBox(row, col)) {
                    board[row][col] |= boxCnt<<4;
                    tempBoxCells.add(new int[]{row, col});
                    boxCnt++;
                }
                col++;
            }
            row++;
        }

        if (Main.useGameStateHash) {

            gameStateHash = new HashMap<Object, Integer>();
            playerAndBoxesHashCells = new HashSet<Integer>();
            hashNewPosition(playerRow, playerCol, playerRow, playerCol, false);
            for (int i = 0; i < boxCnt; i++) {
                int boxRow = tempBoxCells.get(i)[0];
                int boxCol = tempBoxCells.get(i)[1];
                hashNewPosition(boxRow, boxCol, boxRow, boxCol, true);
            }
        }

        boxCells = new int[tempBoxCells.size()][];
        for (int i = 0; i < boxCells.length; i++) {
            boxCells[i] = tempBoxCells.get(i);
        }

        goalCells = new int[tempGoalCells.size()][];
        for (int i = 0; i < goalCells.length; i++) {
            goalCells[i] = tempGoalCells.get(i);
        }
    }

    public void initializeGoalDistancesAndMapping() {
        goalDist = new int[height][width][goalCnt];
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                Arrays.fill(goalDist[r][c], INF);
            }
        }
        for (int i = 0; i < goalCells.length; i++) {
            int[] goal = goalCells[i];
            goalDist[goal[0]][goal[1]][i] = 0;
            LinkedList<int[]> q = new LinkedList<int[]>();
            q.add(goal);
            while (!q.isEmpty()) {
                int[] p = q.removeFirst();
                int r = p[0], c = p[1];
                for (int dir = 0; dir < 4; dir++) {
                    int nr = r + dr[dir];
                    int nc = c + dc[dir];
                    int d = goalDist[r][c][i] + 1;
                    if (!isWall(nr, nc) && d < goalDist[nr][nc][i]) {
                        int nr2 = nr + dr[dir];
                        int nc2 = nc + dc[dir];
                        if (!isWall(nr2, nc2)) {
                            goalDist[nr][nc][i] = d;
                            q.add(new int[]{nr, nc});
                        }
                    }
                }
            }
        }
        // Trapping cells
        trappingCells = new boolean[height][width];
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                trappingCells[row][col] = true;
                for (int goal = 0; goal < goalCnt; goal++) {
                    trappingCells[row][col] &= goalDist[row][col][goal] == INF;
                }
            }
        }
        initializeBoxToGoalMapping();
    }

    // TODO method to update mapping for only one cell
    public void initializeBoxToGoalMapping() {
        PriorityQueue<int[]> leastCostPairs = new PriorityQueue<int[]>(goalCnt*boxCnt, new Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                if (a[2] < b[2]) return -1;
                if (a[2] > b[2]) return 1;
                return 0;
            }
        });
        for (int box = 0; box < boxCnt; box++) {
            int boxR = boxCells[box][0];
            int boxC = boxCells[box][1];
            for (int goal = 0; goal < goalCnt; goal++) {
                if (goalDist[boxR][boxC][goal] < INF) {
                    leastCostPairs.add(new int[]{box, goal, goalDist[boxR][boxC][goal]});
                }
            }
        }
        matchedGoal = new int[boxCnt];
        int[] matchedBy = new int[goalCnt];
        boolean[] visited = new boolean[goalCnt];
        Arrays.fill(matchedBy, -1);
        Arrays.fill(matchedGoal, -1);
        while (!leastCostPairs.isEmpty()) {
            int[] boxGoalPair = leastCostPairs.poll();
            if (matchedGoal[boxGoalPair[0]] == -1 && matchedBy[boxGoalPair[1]] == -1) {
                matchedGoal[boxGoalPair[0]] = boxGoalPair[1];
                matchedBy[boxGoalPair[1]] = boxGoalPair[0];
            }
        }
        for (int box = 0; box < boxCnt; box++) {
            int boxR = boxCells[box][0];
            int boxC = boxCells[box][1];
            if (matchedGoal[box] == -1) {
                Arrays.fill(visited, false);
                for (int goal = 0; goal < goalCnt; goal++) {
                    if (goalDist[boxR][boxC][goal] < INF) {
                        if (match(goal, matchedBy, visited)) {
                            matchedGoal[box] = goal;
                            matchedBy[goal] = box;
                            break;
                        }
                    }
                }
            }
        }
    }

    private boolean match(int goal, int[] matchedBy, boolean[] visited) {
        if (matchedBy[goal] == -1) return true;
        if (visited[goal]) return false;
        visited[goal] = true;
        int matchingBox = matchedBy[goal];
        int boxR = boxCells[matchingBox][0];
        int boxC = boxCells[matchingBox][1];
        for (int newGoal = 0; newGoal < goalCnt; newGoal++) {
            if (goalDist[boxR][boxC][newGoal] < INF) {
                if (match(newGoal, matchedBy, visited)) {
                    matchedBy[newGoal] = matchingBox;
                    matchedGoal[matchingBox] = newGoal;
                    return true;
                }
            }
        }
        return false;
    }

    public boolean performMove(int direction) {
        int newRow = playerRow + dr[direction];
        int newCol = playerCol + dc[direction];
        boolean successful = false;

        // We don't check if the move is outside the board since the player always is surrounded by walls
        if (isFree(newRow, newCol)) {
            movePlayer(newRow, newCol);
            successful = true;
            previousMove = new StackEntry(direction, previousMove);
        }
        else if (isBox(newRow, newCol)) {
            int newRow2 = newRow + dr[direction];
            int newCol2 = newCol + dc[direction];
            if (isFree(newRow2, newCol2)) {
                moveBox(newRow, newCol, newRow2, newCol2);
                movePlayer(newRow, newCol);
                successful = true;
                previousMove = new StackEntry(direction | 4, previousMove);
            }
        }
        return successful;
    }

    public int[][] getPossibleMovePositions() {
        LinkedList<int[]> moves = new LinkedList<int[]>();
        boolean[][] visited = new boolean[height][width];
        getPossibleMovePositionsRec(playerRow, playerCol, visited, moves);
        int[][] res = new int[moves.size()][];
        int i = 0;
        for (int[] move : moves) {
            res[i++] = move;
        }
        return res;
    }

    private void getPossibleMovePositionsRec(int row, int col, boolean[][] visited, LinkedList<int[]> moves) {
        visited[row][col] = true;
        // Check if this is a cell from where we can move a box (and is not the current player cell)
        if (row != playerRow || col != playerCol) {
            for (int dir = 0; dir < 4; dir++) {
                int newRow = row + dr[dir], newCol = col + dc[dir];
                if (isBox(newRow, newCol)) {
                    int newRow2 = newRow + dr[dir], newCol2 = newCol + dc[dir];
                    if (isFree(newRow2, newCol2)) {
                        moves.add(new int[]{ row, col });
                        break;
                    }
                }
            }
        }
        for (int dir = 0; dir < 4; dir++) {
            int newRow = row + dr[dir], newCol = col + dc[dir];
            if (isFree(newRow, newCol) && !visited[newRow][newCol]) {
                getPossibleMovePositionsRec(newRow, newCol, visited, moves);
            }
        }
    }

    /*
     * Teleports the player to the given position. No error checking done!
     * Should only use positions given by getPossibleMovePositions()
     */
    public boolean performJump(int row, int col) {
        previousMove = new StackEntry(8 | playerRow << 4 | playerCol << 14, previousMove);
        movePlayer(row, col);
        return true;
    }

    /*
     * Determines if the move does not create an unsolvable situation
     */
    public boolean isGoodMove(int direction) {
        int newRow = playerRow + dr[direction];
        int newCol = playerCol + dc[direction];
        if (isFree(newRow, newCol)) { return true; }
        boolean good = false;
        if (isBox(newRow, newCol)) {
            int newRow2 = newRow + dr[direction];
            int newCol2 = newCol + dc[direction];
            if (isFree(newRow2, newCol2) && !isTrappingCell(newRow2, newCol2)) {
                good = true;
                moveBox(newRow, newCol, newRow2, newCol2);
                good &= checkIfValidBox(newRow2 - 1, newCol2 - 1);
                good &= checkIfValidBox(newRow2 - 1, newCol2);
                good &= checkIfValidBox(newRow2, newCol2 - 1);
                good &= checkIfValidBox(newRow2, newCol2);
                moveBox(newRow2, newCol2, newRow, newCol);
            }
        }
        return good;
    }

    /*
     * Checks if the 2x2 box with top-left corner at (row, col) is valid, that is that it isn't
     * completely filled with walls/boxes or that every box is at a goal
     */
    private boolean checkIfValidBox(int row, int col) {
        boolean unmatchedBox = false;
        for (int rowDiff = 0; rowDiff < 2; rowDiff++) {
            for (int colDiff = 0; colDiff < 2; colDiff++) {
                if (isFree(row + rowDiff, col + colDiff)) return true;
                unmatchedBox |= (board[row + rowDiff][col + colDiff]&15) == BOX;
            }
        }
        return !unmatchedBox;
    }

    /*
     * previousMove has the following format (bits 0-indexed:
     * Bits 0 and 1 together contain the direction of the move
     * Bit 2 decides whether a board was pushed by the move or not
     * If bit 3 is set, the move was a jump, and bits 0-2 can be ignored.
     * Bits 4-13 together determine the row that the jump was made from.
     * Bits 14-23 together determine the column that the jump was made from.
     */
    public boolean reverseMove() {
        if (previousMove == null) { return false; }
        if ((previousMove.val & 8) != 0) {
            // Move was jump
            int r = previousMove.val >> 4 & ((1 << 10) - 1);
            int c = previousMove.val >> 14;
            movePlayer(r, c);
        }
        else {
            boolean movedBox = (previousMove.val & 4) != 0;
            int dir = previousMove.val & 3;
            int oppositeDir = getOppositeDirection(dir);
            int r1 = playerRow, c1 = playerCol;
            int r0 = r1 + dr[oppositeDir], c0 = c1 + dc[oppositeDir];
            movePlayer(r0, c0);
            if (movedBox) {
                int r2 = r1 + dr[dir], c2 = c1 + dc[dir];
                moveBox(r2, c2, r1, c1);
            }
        }
        previousMove = previousMove.prev;
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
        StringBuilder sb = new StringBuilder();
        while (previousMove != null) {
            if ((previousMove.val & 8) != 0) {
                // Move was jump
                // Have to search for path
                int startRow = previousMove.val >> 4 & ((1 << 10) - 1);
                int startCol = previousMove.val >> 14;
                int[][] prev = new int[height][width];
                for (int i = 0; i < height; i++) {
                    Arrays.fill(prev[i], -2);
                }
                LinkedList<int[]> q = new LinkedList<int[]>();
                q.add(new int[]{ startRow, startCol });
                prev[startRow][startCol] = -1;
                while (!q.isEmpty()) {
                    int[] state = q.removeFirst();
                    int row = state[0], col = state[1];
                    if (row == playerRow && col == playerCol) {
                        int r = playerRow, c = playerCol;
                        while (prev[r][c] != -1) {
                            int dir = prev[r][c];
                            sb.append(directionCharacters[dir]);
                            int oppDir = getOppositeDirection(dir);
                            r += dr[oppDir];
                            c += dc[oppDir];
                        }
                        break;
                    }
                    for (int dir = 0; dir < 4; dir++) {
                        int newRow = row + dr[dir];
                        int newCol = col + dc[dir];
                        if (isFree(newRow, newCol) && prev[newRow][newCol] == -2) {
                            prev[newRow][newCol] = dir;
                            q.add(new int[]{ newRow, newCol });
                        }
                    }
                }
            }
            else {
                sb.append(directionCharacters[previousMove.val & 3]);
            }
            reverseMove();
        }
        return sb.reverse().toString();
    }

    public String getPathTaken() {
        StringBuilder sb = new StringBuilder();
        StackEntry tempVar = previousMove;
        while (tempVar != null) {
            sb.append(directionCharacters[tempVar.val & 3]);
            tempVar = tempVar.prev;
        }
        return sb.reverse().toString();
    }

    /*
     * Helper method that does not do error checking
     */
    private void moveBox(int oldRow, int oldCol, int newRow, int newCol) {
        if (Main.useGameStateHash) {
            hashNewPosition(oldRow, oldCol, newRow, newCol, true);
        }
        int boxNum = getBoxNumber(oldRow, oldCol);
        boxCells[boxNum][0] = newRow;
        boxCells[boxNum][1] = newCol;
        board[oldRow][oldCol] &= ~BOX;
        board[newRow][newCol] |= BOX;
        board[newRow][newCol] |= -16 & board[oldRow][oldCol];
        board[oldRow][oldCol] &= 15;
    }

    private void hashNewPosition(int oldRow, int oldCol, int newRow, int newCol, boolean isBox) {
        if (isBox) {
            playerAndBoxesHashCells.remove(height * width + oldCol + oldRow * width);
            playerAndBoxesHashCells.add(height * width + newCol + newRow * width);
        }
        else {
            playerAndBoxesHashCells.remove(oldCol + oldRow * width);
            playerAndBoxesHashCells.add(newCol + newRow * width);
        }
    }

    /*
     * Helper method that does not do error checking
     */
    private void movePlayer(int newRow, int newCol) {
        if (Main.useGameStateHash) {
            hashNewPosition(playerRow, playerCol, newRow, newCol, false);
        }
        board[playerRow][playerCol] &= ~PLAYER;
        board[newRow][newCol] |= PLAYER;
        playerRow = newRow;
        playerCol = newCol;
    }

    public void hashCurrentGameState(int depth) {
        gameStateHash.put(new HashSet<Integer>(playerAndBoxesHashCells), depth);
    }

    public void clearHashTable() {
        gameStateHash.clear();
    }

    public boolean isPreviousGameState(int currentDepth) {
        Integer oldDepth = gameStateHash.get(playerAndBoxesHashCells);
        if (oldDepth == null) {
            return false;
        } else if (oldDepth > currentDepth) {
            hashCurrentGameState(currentDepth);
            return false;
        }
        else {
            return true;
        }
    }

    // TODO This should be updated while moving (maybe)
    public int getBoardValue() {
        int res = 0;
        for (int box = 0; box < boxCnt; box++) {
            if (matchedGoal[box] == -1) return INF;
            int boxR = boxCells[box][0];
            int boxC = boxCells[box][1];
            res += goalDist[boxR][boxC][matchedGoal[box]];
        }
        return res;
    }

    public static int getOppositeDirection(int direction) {
        return (direction + 2) & 3;
    }

    public boolean isTrappingCell(int row, int col) {
        return trappingCells[row][col];
    }

    public boolean isWall(int row, int col) {
        return board[row][col] == WALL;
    }

    public boolean isGoal(int row, int col) {
        return (board[row][col] & GOAL) != 0;
    }

    public boolean isBox(int row, int col) {
        return (board[row][col] & BOX) != 0;
    }

    public boolean isBoxInDirection(int direction) {
        return isBox(playerRow + dr[direction], playerCol + dc[direction]);
    }

    public int getBoxIndexInDirection(int direction) {
        return getBoxNumber(playerRow + dr[direction], playerCol + dc[direction]);
    }

    public boolean isPlayer(int row, int col) {
        return (board[row][col] & PLAYER) != 0;
    }

    public boolean isFree(int row, int col) {
        return (board[row][col] & NOT_FREE) == 0;
    }

    // TODO this should be updated while moving
    public boolean isBoardSolved() {
        for (int[] goal : goalCells) {
            if (!((board[goal[0]][goal[1]] & 15) == BOX_ON_GOAL)) {
                return false;
            }
        }
        return true;
    }

    public boolean onBoard(int row, int col) {
        return row >= 0 && row < height && col >= 0 && col < width;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                sb.append(boardCharacters[board[row][col] & 15]);
            }
            sb.append('\n');
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

    public int getBoxNumber(int row, int col){
        return board[row][col] >> 4;
    }
}
