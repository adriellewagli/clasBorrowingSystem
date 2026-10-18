package com.borrowclas.clasborrowingsystem;

public class Launcher {
    public static void main(String[] args) {
        // Force high-DPI scaling and crisp LCD text rendering
        System.setProperty("prism.lcdtext", "true");
        System.setProperty("prism.allowhidpi", "true");
        System.setProperty("glass.win.minRenderScale", "1.0");

        MainApp.main(args);
    }
}