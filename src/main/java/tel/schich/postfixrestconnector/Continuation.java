package tel.schich.postfixrestconnector;

public interface Continuation {
    void run(Throwable t);
}
