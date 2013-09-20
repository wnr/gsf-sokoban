import java.util.*;

public class BoardState {

    public static final char FREE_SPACE_CHAR = ' ';
    public static final char GOAL_CHAR = '.';
    public static final char WALL_CHAR = '#';
    public static final char PLAYER_CHAR = '@';
    public static final char PLAYER_ON_GOAL_CHAR = '+';
    public static final char BOX_CHAR = '$';
    public static final char BOX_ON_GOAL_CHAR = '*';

    public static final int UP = 0;
    public static final int RIGHT = 1;
    public static final int DOWN = 2;
    public static final int LEFT = 3;

    private static final int FREE_SPACE = 0;
    private static final int WALL = 1;
    private static final int GOAL = 2;
    private static final int PLAYER = 4;
    private static final int BOX = 8;
    private static final int PLAYER_ON_GOAL = PLAYER | GOAL;
    private static final int BOX_ON_GOAL = BOX | GOAL;
    private static final int NOT_FREE = WALL | BOX;

    private char[] boardCharacters = {FREE_SPACE_CHAR, WALL_CHAR, GOAL_CHAR, 0, PLAYER_CHAR, 0, PLAYER_ON_GOAL_CHAR, 0, BOX_CHAR, 0, BOX_ON_GOAL_CHAR};
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
    private static int[] dr = {-1, 0, 1, 0};
    private static int[] dc = {0, 1, 0, -1};
    private static char[] directionCharacters = {'U', 'R', 'D', 'L'};

    private int width, height;
    private int playerRow, playerCol;
    public int goalCnt, boxCnt;
    public int playerCnt;
    private int[][] board;

    public BoardState(List<String> lines) {
        height = lines.size();
        width = 0;
        for (String line : lines) {
            width = Math.max(width, line.length());
        }
        board = new int[height][width];
        int row = 0;
        for (String line : lines) {
            int col = 0;
            for (char cell : line.toCharArray()) {
                board[row][col] = characterMapping.get(cell);
                if (isPlayer(row, col)) {
                    playerCnt++;
                    playerRow = row;
                    playerCol = col;
                }
                if (isBox(row, col)) {
                    boxCnt++;
                }
                if (isGoal(row, col)) {
                    goalCnt++;
                }
                col++;
            }
            row++;
        }
    }

    public boolean performMove(int direction) {
        int newRow = playerRow + dr[direction];
        int newCol = playerCol + dc[direction];
        if (onBoard(newRow, newCol)) {
            if (isFree(newRow, newCol)) {
                movePlayer(newRow, newCol);
                return true;
            } else if (isBox(newRow, newCol)) {
                int newRow2 = newRow + dr[direction];
                int newCol2 = newCol + dc[direction];
                if (onBoard(newRow2, newCol2) && isFree(newRow2, newCol2)) {
                    moveBox(newRow, newCol, newRow2, newCol2);
                    movePlayer(newRow, newCol);
                    return true;
                }
            }
        }
        return false;
    }

    /*
     * Helper method that does not do error checking
     */
    private void moveBox(int oldRow, int oldCol, int newRow, int newCol) {
        if (board[oldRow][oldCol] == BOX_ON_GOAL) {
            board[oldRow][oldCol] = GOAL;
        } else {
            board[oldRow][oldCol] = FREE_SPACE;
        }
        if (board[newRow][newCol] == GOAL) {
            board[newRow][newCol] = BOX_ON_GOAL;
        } else {
            board[newRow][newCol] = BOX;
        }
    }

    /*
     * Helper method that does not do error checking
     */
    private void movePlayer(int newRow, int newCol) {
        if (board[playerRow][playerCol] == PLAYER_ON_GOAL) {
            board[playerRow][playerCol] = GOAL;
        } else {
            board[playerRow][playerCol] = FREE_SPACE;
        }
        if (board[newRow][newCol] == GOAL) {
            board[newRow][newCol] = PLAYER_ON_GOAL;
        } else {
            board[newRow][newCol] = PLAYER;
        }
        playerRow = newRow;
        playerCol = newCol;
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

    public boolean isPlayer(int row, int col) {
        return (board[row][col] & PLAYER) != 0;
    }

    public boolean isFree(int row, int col) {
        return (board[row][col] & NOT_FREE) == 0;
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
                sb.append(boardCharacters[board[row][col]]);
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
