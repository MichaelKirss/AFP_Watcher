package ru.venets.afp;

public class Main {

    public static void main(String[] args) throws Exception {

        FileWatcherService service =
                new FileWatcherService();

        service.start();
    }
}