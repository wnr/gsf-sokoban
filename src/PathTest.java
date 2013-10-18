import java.io.IOException;


public class PathTest {
    public static void main(String[] args) throws IOException {
        boolean displayPath = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i].contains("display") || args[i].contains("-d")) {
                displayPath = true;
                args = Main.removeArrayElement(args, i);
                break;
            }
        }

        BoardStateLight board = null;
        int boardNum = -1;
        if (args.length == 2) {

            try {
                boardNum = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Argument must be a board number");
                System.exit(0);
            }
        } else {
            System.err.println("Usage: java PathTest [display -d] <boardIndex> <path>");
            System.exit(0);
        }

        board = BoardUtil.getTestBoardLight(boardNum);

        if (board == null) {
            System.out.println("Invalid board number: " + boardNum);
            System.exit(0);
        }

        if (Main.investigatePath(board, args[1], displayPath))
            System.out.println("YAY! This path solved the board.");
        else
            System.out.println("This path does not solve the board. :(");


    }
}
