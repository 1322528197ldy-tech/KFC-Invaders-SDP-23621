package engine;

/** Input state that must not remain held or queued after the game window loses focus. */
public interface ResettableInput {
    void resetInput();
}
