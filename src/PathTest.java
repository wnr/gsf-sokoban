import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: martinpettersson
 * Date: 2013-09-25
 * Time: 17:26
 * To change this template use File | Settings | File Templates.
 */
public class PathTest {
    public static void main(String[] args) throws IOException {
        BoardState board = null;
        int boardNum = -1;
        if (args.length == 2) {

            try {
                boardNum = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Argument must be a board number");
                System.exit(0);
            }
        } else {
            System.err.println("Wrong number of arguments.");
            System.exit(0);
        }

        board = BoardUtil.getTestBoard(boardNum);

        if (board == null) {
            System.out.println("Invalid board number: " + boardNum);
            System.exit(0);
        }

        if (Main.isValidAnswer(board, args[1]))
            System.out.println("YAY! This path solved the board.");
        else
            System.out.println("This path does not solve the board. :(");


    }
}
