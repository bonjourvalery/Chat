package server;

import server.service.impl.InMemoryUserServiceImpl;

public class App {
    public static void main(String[] args) {
        new Server(new InMemoryUserServiceImpl()).start();
    }
}
