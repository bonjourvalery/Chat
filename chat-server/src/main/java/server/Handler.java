package server;

import common.enums.Command;
import server.error.NickAlreadyIsBusyException;
import server.error.WrongCredentialsException;
import server.error.WrongPasswordException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import static common.constants.MessageConstants.REGEX;
import static common.enums.Command.*;

public class Handler {
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private Thread handlerThread;
    private Server server;
    private String user;

    public Handler(Socket socket, Server server) {
        try {
            this.server = server;
            this.socket = socket;
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
            System.out.println("Handler created");
        } catch (IOException e) {
            System.err.println("Connection problems with user: " + user);
        }
    }

    public void handle() {
        handlerThread = new Thread(() -> {
            authorize();
            System.out.println("Auth done");
            while (!Thread.currentThread().isInterrupted() && !socket.isClosed()) {
                try {
                    String message = in.readUTF();
                    parseMessage(message);
                } catch (IOException e) {
                    System.out.println("Connection broken with client: " + user);
                    server.removeHandler(this);
                }
            }
        });
        handlerThread.start();
    }

    private void parseMessage(String message) {
        String[] split = message.split(REGEX);
        Command command = Command.getByCommand(split[0]);

        switch (command) {
            case BROADCAST_MESSAGE -> server.broadcast(user, split[1]);
            case PRIVATE_MESSAGE -> server.privateMessage(user, split[1], split[2]);
            case CHANGE_NICK -> changeNick(split[1]);
            case CHANGE_PASSWORD -> changePassword(user, split[1], split[2]);
            default -> System.out.println("Unknown message " + message);
        }
    }

    private void changePassword(String nick, String oldPassword, String newPassword) {
        try {
            server.getUserService().changePassword(nick, oldPassword, newPassword);
            send(String.valueOf(CHANGE_PASSWORD_OK));
        } catch (WrongPasswordException e) {
            send(ERROR_MESSAGE.getCommand() + REGEX + "Wrong password");
        }
    }

    private void changeNick(String newNick) {
       try {
          server.getUserService().changeNick(user, newNick);
          user = newNick;
          server.updateHandlerUsername();
          send(CHANGE_NICK_OK.getCommand() + REGEX + newNick);
        } catch (NickAlreadyIsBusyException e) {
           send(ERROR_MESSAGE.getCommand() + REGEX + "This nickname already in use");
       }
    }

    private void authorize() {
        System.out.println("Authorizing");

        try {
            while (!socket.isClosed()) {
                String msg = in.readUTF();
                if (msg.startsWith(AUTH_MESSAGE.getCommand())) {
                    String[] parsed = msg.split(REGEX);
                    String response = "";
                    String nickname = null;

                    try {
                        nickname = server.getUserService().authenticate(parsed[1], parsed[2]);
                    } catch (WrongCredentialsException e) {
                        response = ERROR_MESSAGE.getCommand() + REGEX + e.getMessage();
                        System.out.println("Wrong credentials: " + parsed[1]);
                    }
                    
                    if (server.isUserAlreadyOnline(nickname)) {
                        response = ERROR_MESSAGE.getCommand() + REGEX + "This client already connected";
                        System.out.println("Already connected");
                    }
                    
                    if (!response.equals("")) {
                        send(response);
                    } else {
                        System.out.println("Auth ok");
                        this.user = nickname;
                        send(AUTH_OK.getCommand() + REGEX + nickname);
                        server.addHandler(this);
                        break;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void send(String msg) {
        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Thread getHandlerThread() {
        return handlerThread;
    }

    public String getUser() {
        return user;
    }
}
