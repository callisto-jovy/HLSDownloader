package m3u8.util;

public class Node {

    private String content;
    private Node previous;

    public Node(final String content) {
        this.content = content;
    }

    public Node(final String content, final Node previous) {
        this.content = content;
        this.previous = previous;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setPrevious(Node previous) {
        this.previous = previous;
    }

    public Node getPrevious() {
        return previous;
    }

    public String getContent() {
        return content;
    }
}
