package ru.itis.garticphone.server;

public class ChainStep {
    private final String text;
    private final byte[] drawing;
    private final boolean isTextStep;

    public ChainStep(String text) {
        this.text = text;
        this.drawing = null;
        this.isTextStep = true;
    }

    public ChainStep(byte[] drawing) {
        this.text = null;
        this.drawing = drawing;
        this.isTextStep = false;
    }

    public String getText() { return text; }
    public byte[] getDrawing() { return drawing; }
    public boolean isTextStep() { return isTextStep; }
}
