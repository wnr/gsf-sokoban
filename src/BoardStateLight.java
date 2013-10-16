import java.util.*;

public class BoardStateLight {

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
    private static char[] directionCharacters = { 'U', 'R', 'D', 'L' };

    private int[] dx;

    private int width, height, totalSize;
    private int playerPos;
    public  int goalCnt, boxCnt;

    private StackEntry previousMove;
    private int[]      board;
    private int[]      goalCells;


    public BoardStateLight(List<String> lines) {
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

        goalCells = new int[goalCnt];
        for (int i = 0; i < goalCells.length; i++) {
            goalCells[i] = tempGoalCells.get(i);
        }
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

    public boolean reverseMove() {
        if (previousMove == null) { return false; }
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
        previousMove = previousMove.prev;
        return true;
    }

    public String backtrackPath() {
        StringBuilder sb = new StringBuilder();
        while (previousMove != null) {
            sb.append(directionCharacters[previousMove.val&3]);
            reverseMove();
        }
        return sb.reverse().toString();
    }

    private void moveBox(int oldPos, int newPos) {
        board[oldPos] &= ~BOX;
        board[newPos] |= BOX;
        board[newPos] |= -16 & board[oldPos];
        board[oldPos] &= 15;
    }

    private void movePlayer(int newPos) {
        board[playerPos] &= ~PLAYER;
        board[newPos] |= PLAYER;
        playerPos = newPos;
    }

    public static int getOppositeDirection(int direction) {
        return (direction + 2) & 3;
    }

    public boolean isGoal(int pos) {
        return (board[pos] & GOAL) != 0;
    }

    public boolean isBox(int pos) {
        return (board[pos] & BOX) != 0;
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
            sb.append(boardCharacters[board[pos] & 15]);
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
}
