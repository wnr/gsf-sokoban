import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * Class used to play a board via the terminal.
 *
 * Great for testing BoardState stuff!
 */
public class Play {

    public static void main(String[] args) throws IOException {
        ArrayList<String> lines = new ArrayList<String>();
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

        String line;
        while ((line = in.readLine()) != null) {
            lines.add(line);
        }
        BoardState board = new BoardState(lines);
        boardInteract(board);
    }


    public static void boardInteract(BoardState board) {
        final BoardState b = board;

        JFrame frame = new JFrame();

        frame.setVisible(true);

        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        manager.addKeyEventDispatcher(new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent keyEvent) {
                if (keyEvent.getID() == KeyEvent.KEY_PRESSED) {
                    boolean changed = false;
                    switch (keyEvent.getKeyCode()) {
                        case KeyEvent.VK_LEFT:
                            changed = b.performMove(BoardState.LEFT);
                            break;
                        case KeyEvent.VK_UP:
                            changed = b.performMove(BoardState.UP);
                            break;
                        case KeyEvent.VK_RIGHT:
                            changed = b.performMove(BoardState.RIGHT);
                            break;
                        case KeyEvent.VK_DOWN:
                            changed = b.performMove(BoardState.DOWN);
                            break;
                        case KeyEvent.VK_SPACE:
                            changed = b.reverseMove();
                            break;
                        case KeyEvent.VK_ESCAPE:
                            System.exit(0);
                            break;
                        default:
                            return false;
                    }
                    if (changed) System.out.println(b);
                    return true;
                }
                return false;
            }
        });
        System.out.println("Started awesome board player!");
        System.out.println();
        System.out.println("Instructions:");
        System.out.println("1. Use arrow keys to move");
        System.out.println("2. Space bar to regret the last move");
        System.out.println("3. Escape to quit");
        System.out.println();
        System.out.println(b);
    }
}
