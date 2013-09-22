import java.io.*;
import java.util.*;

public class Main {

    public static void main(String[] args) throws IOException {
        ArrayList<String> lines = new ArrayList<String>();

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

        String line;
        while((line = in.readLine()) != null) {
            if(line.contains(";")){
                break;
            }
            lines.add(line);
        }

        BoardState board = new BoardState(lines);

        System.out.println(board);
        System.out.println(board.aliveCellsToString());

        performMoves(board, "L U R U R U R U L L R D D L D D R U U U");
    }

    public static void performMoves(BoardState board, String moves) {
        StringTokenizer st = new StringTokenizer(moves);
        while (st.hasMoreTokens()) {
            String move = st.nextToken();
            assert move.length() == 1;
            char ch = move.charAt(0);
            boolean success = false;
            switch (ch) {
                case 'U':
                    success = board.performMove(BoardState.UP); break;
                case 'R':
                    success = board.performMove(BoardState.RIGHT); break;
                case 'D':
                    success = board.performMove(BoardState.DOWN); break;
                case 'L':
                    success = board.performMove(BoardState.LEFT); break;
                default:
                    throw new RuntimeException("Invalid move: " + move);
            }
            System.out.print(board);
            System.out.println(success ? "Move successful!" : "Move not successful :/");
        }
    }
}