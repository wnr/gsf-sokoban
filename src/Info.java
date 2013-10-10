import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class Info {
    public static void main(String[] args) throws IOException {
        BoardState board = null;

        ArrayList<String> lines = new ArrayList<String>();

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

        String line;
        while ((line = in.readLine()) != null) {
            if (line.contains(";")) {
                break;
            }
            lines.add(line);
        }

        board = new BoardState(lines);

        board.setup();

        System.out.println("free: " + board.getNumFree());
        System.out.println("walls: " + board.getNumWalls());
        System.out.println("boxes: " + board.getNumBoxes());
        System.out.println("trapping: " + board.getNumTrappingCells());
        System.out.println("tunnels: " + board.getNumTunnels());
        System.out.println("deadends: " + board.getNumDeadEnds());
        System.out.println("roomcells: " + board.getNumRoomsCells());
        System.out.println("openings: " + board.getNumOpenings());

        System.exit(0);
    }
}
