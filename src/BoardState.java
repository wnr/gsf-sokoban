import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;

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
    private int playerPos;
    public int goalCnt, boxCnt;

    private StackEntry  previousMove;
    private int[]       board;
    private int[]     boxCells;
    private int[]     goalCells;
    private int[][]   goalDist;
    private boolean[] trappingCells;
    private int[]       matchedGoal;
    private int[]     possibleJumpPositions;
    private int[]     tunnels;

    private int[]                   playerAndBoxesHashCells;
    private HashMap<Long, int[]> gameStateHash;

    public BoardState(List<String> lines) {
        height = lines.size();
        width = 0;
        for (String line : lines) {
            width = Math.max(width, line.length());
        }
        totalSize = width*height;
        dx = new int[] {-width, 1, width, -1};
        board = new int[totalSize];
        int row = 0;
        List<Integer> tempGoalCells = new ArrayList<Integer>();
        for (String line : lines) {
            int col = 0;
            for (char cell : line.toCharArray()) {
                board[row * width + col] = characterMapping.get(cell);
                if (isPlayer(row * width + col)) {
                    playerPos = row * width + col;
                }
                if (isGoal(row * width + col)) {
                    tempGoalCells.add(row * width + col);
                    goalCnt++;
                }
                if (isBox(row * width + col)) {
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

        if (Main.useGameStateHash) {
            gameStateHash = new HashMap<Long, int[]>();
            playerAndBoxesHashCells = new int[boxCnt + 1];
        }

        boxCells = new int[boxCnt];
        goalCells = new int[goalCnt];
        for (int i = 0; i < goalCells.length; i++) {
            goalCells[i] = tempGoalCells.get(i);
        }
    }

    public void analyzeBoard() {
        int boardSections[] = new int[totalSize];
        int boxIndex = computeBoardSections(boardSections);

        if (movedBoxLastMove()) {
            int dir = directionLastMove();
            int box = getBoxNumber(playerPos + dx[dir]);
            updateMatchingForBox(box);
        }

        int playerSection = boardSections[playerPos];
        if (Main.useGameStateHash) { playerAndBoxesHashCells[boxIndex] = totalSize + playerSection; }

        if ((tunnels[playerPos] & TUNNEL) == TUNNEL) {
            int dir = directionLastMove();

            if (movedBoxLastMove()) {
                int boxPos = playerPos + dx[dir];
                if ((tunnels[boxPos] & TUNNEL) == TUNNEL || tunnels[boxPos] == ROOM) {
                    if (!isGoal(boxPos)) {
                        possibleJumpPositions = new int[0];
                        return;
                    }
                }
            }
        }
        LinkedList<Integer> moves = new LinkedList<Integer>();
        for (int i = 0; i < boxCnt; i++) {
            int boxPos = boxCells[i];
            for (int dir = 0; dir < 2; dir++) {
                int newPos = boxPos + dx[dir];
                if (isFree(newPos)) {
                    int newPos2 = boxPos + dx[dir + 2];
                    if (isFree(newPos2)) {
                        if (playerSection == boardSections[newPos] && playerPos != newPos) {
                            moves.add(newPos);
                            boardSections[newPos] = -1;
                        }
                        if (playerSection == boardSections[newPos2] && playerPos != newPos2) {
                            moves.add(newPos2);
                            boardSections[newPos2] = -1;
                        }
                    }
                }
            }
        }

        possibleJumpPositions = new int[moves.size()];
        int i = 0;
        for (int move : moves) {
            possibleJumpPositions[i++] = move;
        }
    }

    private int computeBoardSections(int[] boardSections) {
        int sectionIndex = 1;
        int boxIndex = 0;
        for (int pos = 0; pos < totalSize; pos++) {
            if (isFree(pos)) {
                if (boardSections[pos] == 0 && boardSections[playerPos] == 0) {
                    analyzeBoardDfs(pos, sectionIndex, boardSections);
                    sectionIndex++;
                }
            } else if (isBox(pos)) {
                int boxNum = getBoxNumber(pos);
                boxCells[boxNum] = pos;
                if (Main.useGameStateHash) { playerAndBoxesHashCells[boxIndex] = pos; }
                boxIndex++;
            }
        }
        return boxIndex;
    }

    private void analyzeBoardDfs(int pos, int sectionIndex, int[] boardSections) {
        boardSections[pos] = sectionIndex;
        for (int dir = 0; dir < 4; dir++) {
            int newPos = pos + dx[dir];
            if (isFree(newPos) && boardSections[newPos] == 0) {
                analyzeBoardDfs(newPos, sectionIndex, boardSections);
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
        computeTunnels();
        analyzeBoard();
        goalDist = new int[totalSize][goalCnt];
        for (int pos = 0; pos < totalSize; pos++) {
            Arrays.fill(goalDist[pos], INF);
        }
        for (int i = 0; i < goalCells.length; i++) {
            int goalPos = goalCells[i];
            goalDist[goalPos][i] = 0;
            LinkedList<Integer> q = new LinkedList<Integer>();
            q.add(goalPos);
            while (!q.isEmpty()) {
                int pos = q.removeFirst();
                for (int dir = 0; dir < 4; dir++) {
                    int newPos = pos + dx[dir];
                    int d = goalDist[pos][i] + 1;
                    if (!isWall(newPos) && d < goalDist[newPos][i]) {
                        int newPos2 = newPos + dx[dir];
                        if (!isWall(newPos2)) {
                            goalDist[newPos][i] = d;
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
                trappingCells[pos] &= goalDist[pos][goal] == INF;
            }
        }
        initializeBoxToGoalMapping();
    }

    public void computeTunnels() {
        tunnels = new int[totalSize];

        ArrayList<Integer> deads = new ArrayList<Integer>();

        //Iterate over board, but do not check outer rows or cols.
        for (int pos = width; pos < totalSize - width; pos++) {
            if (onBorder(pos)) continue;
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
            if (onBorder(pos)) continue;
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
            for (int dir = 0; dir < 4 ; dir++) {
                int newPos = pos + dx[dir];
                int cell = tunnels[newPos];
                if ((cell & TUNNEL) == TUNNEL && (cell & DEAD_END) != DEAD_END) {
                    tunnels[newPos] |= DEAD_END;
                    updateTunnels(newPos);
                }
            }
        }
    }

    // TODO method to update mapping for only one cell
    public void initializeBoxToGoalMapping() {
        PriorityQueue<int[]> leastCostPairs = new PriorityQueue<int[]>(goalCnt * boxCnt, new Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                if (a[2] < b[2]) { return -1; }
                if (a[2] > b[2]) { return 1; }
                return 0;
            }
        });
        for (int box = 0; box < boxCnt; box++) {
            int boxPos = boxCells[box];
            for (int goal = 0; goal < goalCnt; goal++) {
                if (goalDist[boxPos][goal] < INF) {
                    leastCostPairs.add(new int[]{ box, goal, goalDist[boxPos][goal] });
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
            int boxPos = boxCells[box];
            if (matchedGoal[box] == -1) {
                Arrays.fill(visited, false);
                for (int goal = 0; goal < goalCnt; goal++) {
                    if (goalDist[boxPos][goal] < INF) {
                        if (match(goal, matchedBy, visited)) {
                            matchedGoal[box] = goal;
                            matchedBy[goal] = box;
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
            if (goalDist[boxPos][g] + goalDist[boxPos2][g2] > goalDist[boxPos][g2] + goalDist[boxPos2][g]) {
                matchedGoal[box] = g2;
                matchedGoal[otherBox] = g;
            }
        }
    }

    private boolean match(int goal, int[] matchedBy, boolean[] visited) {
        if (matchedBy[goal] == -1) { return true; }
        if (visited[goal]) { return false; }
        visited[goal] = true;
        int matchingBox = matchedBy[goal];
        int boxPos = boxCells[matchingBox];
        for (int newGoal = 0; newGoal < goalCnt; newGoal++) {
            if (goalDist[boxPos][newGoal] < INF) {
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
                previousMove = new StackEntry(direction | 4, previousMove);
            }
        }
        return successful;
    }


    public int[] getPossibleJumpPositions() {
        return possibleJumpPositions;
    }

    /*
     * Teleports the player to the given position. No error checking done!
     * Should only use positions given by getPossibleJumpPositions()
     */
    public boolean performJump(int pos) {
        previousMove = new StackEntry(8 | playerPos << 4, previousMove);
        movePlayer(pos);
        return true;
    }

    /*
     * Determines if the move does not create an unsolvable situation
     */
    public boolean isGoodMove(int direction) {
        int newPos = playerPos + dx[direction];
        if (isFree(newPos)) { return true; }
        boolean good = false;
        if (isBox(newPos)) {
            int newPos2 = newPos + dx[direction];
            if (isFree(newPos2) && !isTrappingCell(newPos2)) {
                good = true;
                moveBox(newPos, newPos2);
                good &= checkIfValidBox(newPos2 - width - 1);
                good &= checkIfValidBox(newPos2 - width);
                good &= checkIfValidBox(newPos2 - 1);
                good &= checkIfValidBox(newPos2);
                moveBox(newPos2, newPos);
            }
        }
        return good;
    }

    /*
     * Checks if the 2x2 box with top-left corner at (row, col) is valid, that is that it isn't
     * completely filled with walls/boxes or that every box is at a goal
     */
    private boolean checkIfValidBox(int pos) {
        boolean unmatchedBox = false;
        for (int posDiff1 = 0; posDiff1 <= width; posDiff1 += width) {
            for (int posDiff2 = 0; posDiff2 <= 1; posDiff2++) {
                if (isFree(pos + posDiff1 + posDiff2)) return true;
                unmatchedBox |= (board[pos + posDiff1 + posDiff2] & 15) == BOX;
            }
        }
        return !unmatchedBox;
    }

    /*
     * previousMove has the following format (bits 0-indexed:
     * Bits 0 and 1 together contain the direction of the move
     * Bit 2 decides whether a board was pushed by the move or not
     * If bit 3 is set, the move was a jump, and bits 0-2 can be ignored.
     * Bits 4 and up contain the position that the jump was made from
     */
    public boolean reverseMove() {
        if (previousMove == null) { return false; }
        if ((previousMove.val & 8) != 0) {
            // Move was jump
            movePlayer(previousMove.val >> 4);
        } else {
            boolean movedBox = (previousMove.val & 4) != 0;
            int dir = previousMove.val & 3;
            int oppositeDir = getOppositeDirection(dir);
            int p1 = playerPos;
            int p0 = p1 + dx[oppositeDir];
            movePlayer(p0);
            if (movedBox) {
                int p2 = p1 + dx[dir];
                moveBox(p2, p1);
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
                int startPos = previousMove.val >> 4;
                int[] prev = new int[totalSize];
                Arrays.fill(prev, -2);
                LinkedList<Integer> q = new LinkedList<Integer>();
                q.add(startPos);
                prev[startPos] = -1;
                while (!q.isEmpty()) {
                    int pos = q.removeFirst();
                    if (pos == playerPos) {
                        int tempPos = playerPos;
                        while (prev[tempPos] != -1) {
                            int dir = prev[tempPos];
                            sb.append(directionCharacters[dir]);
                            tempPos += dx[getOppositeDirection(dir)];
                        }
                        break;
                    }
                    for (int dir = 0; dir < 4; dir++) {
                        int newPos = pos + dx[dir];
                        if (isFree(newPos) && prev[newPos] == -2) {
                            prev[newPos] = dir;
                            q.add(newPos);
                        }
                    }
                }
            } else {
                sb.append(directionCharacters[previousMove.val & 3]);
            }
            reverseMove();
        }
        return sb.reverse().toString();
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

    /*
     * Helper method that does not do error checking
     */
    private void movePlayer(int newPos) {
        board[playerPos] &= ~PLAYER;
        board[newPos] |= PLAYER;
        playerPos = newPos;
    }


    public boolean hashCurrentBoardState(int currentDepth, int currentIteration) {
        long hashCode = getHashCode(playerAndBoxesHashCells);
        int[] cashedDepthInfo = gameStateHash.get(hashCode);
        if (cashedDepthInfo != null) {
            int minDepth = cashedDepthInfo[0];
            int prevIteration = cashedDepthInfo[1];
            if (minDepth > currentDepth || minDepth == currentDepth && currentIteration != prevIteration) {
                // We have been here before but with a bigger depth or in a previous iteration
                cashedDepthInfo[0] = currentDepth;
                cashedDepthInfo[1] = currentIteration;
                return true;
            }
            return false;
        }
        gameStateHash.put(hashCode, new int[]{ currentDepth, currentIteration });
        return true;
    }

    private long getHashCode(int[] array){
        long hash = 0;
        for(int i = 0; i<array.length; i++){
            hash = hash*31 + array[i];
        }
        return hash;
    }

    // TODO This should be updated while moving (maybe)
    public int getBoardValue() {
        int res = 0;
        for (int box = 0; box < boxCnt; box++) {
            if (matchedGoal[box] == -1) { return INF; }
            res += goalDist[boxCells[box]][matchedGoal[box]];
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
            if ((tunnels[pos] & DEAD_END) == DEAD_END) {
                sb.append("\033[41m");
            } else if ((tunnels[pos] & TUNNEL) == TUNNEL) {
                sb.append("\033[43m");
            } else if ((tunnels[pos] & ROOM) == ROOM) {
                sb.append("\033[42m");
            }
            sb.append(boardCharacters[board[pos] & 15]);
            if ((tunnels[pos] & TUNNEL) == TUNNEL || (tunnels[pos] & ROOM) == ROOM) {
                sb.append("\033[0m");
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
        return board[pos] >> 4;
    }
}
