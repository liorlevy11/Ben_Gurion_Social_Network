package bgu.spl.net.api;

import bgu.spl.net.api.MessagePackage.BackMessage;
import bgu.spl.net.api.MessagePackage.Notification;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.*;

public class ClientProtocol implements BidiMessagingProtocol<String> {

    private Connections<String> connections;
    private DataBaseServer dataBaseServer;
    private boolean shouldTerminate = false;
    private int connectionId;
    private String userName;

    public ClientProtocol(DataBaseServer dataBaseServer) {
        this.dataBaseServer = dataBaseServer;
        connections = new ConnectionsImplementation<>();
    }

    @Override
    public void process(String msg) {
         if (msg != null) {
            String opcode = msg.substring(0, 2);
            msg = msg.substring(2);
            String[] separatedBySpace = msg.split(":");
            switch (getOpcode(opcode)) {
                case 1://register
                    register(separatedBySpace);
                    break;
                case 2://login
                    login(separatedBySpace);
                    break;
                case 3://logout
                    logout();
                    break;
                case 4:
                    follow(separatedBySpace);
                    break;
                case 5://post
                    post(separatedBySpace);
                    break;
                case 6://pm
                    PM(separatedBySpace);
                    break;
                case 7:
                    logStat();
                    break;
                case 8:
                    stat(separatedBySpace);
                    break;
                case 12:
                    block(separatedBySpace);
                    break;
            }
        } else {
            System.out.println("message was null");
        }

    }

    private void block(String[] separatedBySpace) {
        BackMessage backMessage;
        String toBlock = getToBlock(separatedBySpace);
        backMessage = dataBaseServer.block(userName, toBlock);
        if (backMessage.getStatus() == BackMessage.Status.PASSED) {
            if (!connections.send(connectionId, backMessage.getMessage()))
                System.out.println("failed to send ack logstat message " + backMessage.getMessage());
        } else if (!connections.send(connectionId, backMessage.getMessage())) {
            System.out.println("failed to send error of type logstat message with the error" + backMessage.getMessage());
        }


    }

    private String getToBlock(String[] separatedBySpace) {
        return separatedBySpace[0];
    }

    private void stat(String[] separated) {
        try {
            BackMessage backMessage;
            List<String> users = getUsers(separated);
            backMessage = dataBaseServer.stat(userName, users);
            if (backMessage.getStatus() == BackMessage.Status.PASSED) {
                List<String> ackStatMessages = backMessage.getMessages();
                for (String message : ackStatMessages) {
                      if (!connections.send(connectionId,message)) {
                        System.out.println("failed to send ack message " + message);
                    }
                }
            } else if (!connections.send(connectionId, backMessage.getMessage())) {
                System.out.println("failed to send an error message" + backMessage.getMessage());
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    private void logStat() {
        BackMessage backMessage;
        backMessage = dataBaseServer.logStat(userName);
        if (backMessage.getStatus() == BackMessage.Status.PASSED) {
            List<String> logStatMessages = backMessage.getMessages();
            for (String log : logStatMessages) {
                System.out.println("log message check in log stat " + log);
                if (!connections.send(connectionId, log))
                    System.out.println("failed to send ack logstat message " + log);
            }
        } else if (!connections.send(connectionId, backMessage.getMessage())) {
            System.out.println("failed to send error of type logstat message with the error" + backMessage.getMessage());
        }
    }

    private void PM(String[] separated) {
        BackMessage backMessage;
        String to = getToSendMessage(separated);
        String messageContent = getMessageContent(separated);
        String date = getDateAndTime(separated);
        backMessage = dataBaseServer.PM(userName, to, messageContent, date);
        String name = "pm";
        sendMessage(backMessage, name);//ack
        int to_id = dataBaseServer.getId(to);
        if ((to_id >=0)&&(backMessage.getMessages().size()>1)) {//if errorr -to_id is not on the map
            List<Integer> lst = new LinkedList<>();
            lst.add(to_id);
            sendMessage(backMessage, name, lst);//actual message
        }
    }

    private void sendMessage(BackMessage backMessage, String name) {
        if (backMessage.getStatus() == BackMessage.Status.PASSED) {//logical
            if (!connections.send(connectionId, backMessage.getMessage())) {//connection error
                System.out.println("failed to send ack " + name + " message");//debugging
            }
        } else {
            if (!connections.send(connectionId, backMessage.getMessage())) {//ERROR 1
                System.out.println("failed to send error " + name + " message");//connection problem
            }
        }
    }

    private void sendMessage(BackMessage backMessage, String name, List<Integer> tags) {//only to tag and not for followers
        for (Integer tag_id : tags) {
            if (backMessage.getStatus() == BackMessage.Status.PASSED) {//logical
                if (!tags.isEmpty() && !connections.send(tag_id, backMessage.getMessages().get(1))) {//connection error
                    System.out.println("failed to send " + name + " message");//debugging
                }
            } else {
                if (!connections.send(tag_id, backMessage.getMessages().get(0))) {//ERROR 1
                    System.out.println("failed to send error " + name + " message");//connection problem
                }
            }
        }
    }

    private void sendMessageFollow(BackMessage backMessage, String name, List<Integer> tags) {//only to tag and not for followers
        for (Integer tag_id : tags) {
            if (backMessage.getStatus() == BackMessage.Status.PASSED) {//logical
                if (!connections.send(tag_id, backMessage.getMessages().get(0))) {//connection error
                    System.out.println("failed to send " + name + " message");//debugging
                }
            } else {
                if (!connections.send(tag_id, backMessage.getMessages().get(0))) {//ERROR 1
                    System.out.println("failed to send error " + name + " message");//connection problem
                }
            }
        }
    }

    private void post(String[] separated) {
        BackMessage backMessage;
        String content = getContent(separated);
        List<String> tags = getTags(separated);
        backMessage = dataBaseServer.post(content, this.userName, tags);
        String name = "post";
        List<Integer> tag_ids = dataBaseServer.getIds(tags);
        sendMessage(backMessage, name);
        sendMessage(backMessage, name, tag_ids);

    }

    private void follow(String[] separated) {
        BackMessage backMessage;
        String follow = getFollow(separated);
        int sign = getSign(separated);
        backMessage = dataBaseServer.follow(userName, follow, sign);
        String name = "follow";
        sendMessage(backMessage, name);
      //  int id = dataBaseServer.getId(follow);
        List<Integer> lst = new LinkedList<>();
      //  lst.add(id);
       sendMessageFollow(backMessage, name, lst);
    }

    private int getSign(String[] separated) {
        return Integer.parseInt(separated[0]);
    }

    private void logout() {//TODO make the backMessage an ack message
        BackMessage backMessage;
        backMessage = dataBaseServer.logout(this.userName);
        if (backMessage.getStatus() == BackMessage.Status.PASSED) {
            if (!connections.send(connectionId, backMessage.getMessage()))
                System.out.println("error while sending logout ack");
            this.userName = "";
            connections.disconnect(connectionId);
            shouldTerminate = true;
        } else {
            if (!connections.send(connectionId, backMessage.getMessage()))
                System.out.println("error while sending the logout error " + backMessage.getMessages());
        }
    }

    private void login(String[] separated) {
        BackMessage backMessage;
        userName = getUserName(separated);
        String password = getUserPassword(separated);
        int captcha = getUserCaptcha(separated);

        backMessage = dataBaseServer.login(userName, password, captcha);
        if (backMessage.getStatus() == BackMessage.Status.PASSED) {// i need to send ACK 2 notification1
            if (!connections.send(connectionId, backMessage.getMessage())) {//send ACK 2
                System.out.println("error while sending login ack");
            } else {
                Queue<String> notifications = dataBaseServer.getNotifications(userName);//Notification....
                while (!notifications.isEmpty()) {
                    if (!connections.send(connectionId, notifications.poll())) {//NOTIFICATION
                        System.out.println("error while sending the notification");
                        return;
                    }
                }
                dataBaseServer.deleteNotifications(userName);
            }
        } else {
            if (!connections.send(connectionId, backMessage.getMessage())) {
                System.out.println("error while sending register error " + backMessage.getMessage());
            }
        }
    }

    private int getUserCaptcha(String[] separated) {
        return Integer.parseInt(separated[2]);
    }

    private void register(String[] separated) {
        BackMessage backMessage;
        this.userName = getUserName(separated);
        String password = getUserPassword(separated);
        String date = getUserBirthday(separated);

        backMessage = dataBaseServer.register(userName, password, date, connectionId);
        String name = "register";
        sendMessage(backMessage, name);

    }

    private String getUserBirthday(String[] separated) {
        return separated[2];
    }

    private String getUserPassword(String[] separated) {
        return separated[1];
    }


    private int getOpcode(String msg) {
        return Integer.parseInt(msg);
    }

    private List<String> getUsers(String[] separated) {
        List<String> users = new LinkedList<>();
        if (separated[0] != null) {
            String usersWithDelimiter = separated[0];
            String[] usersArray = usersWithDelimiter.split("\\|");
            Collections.addAll(users, usersArray);
        }
        return users;
    }

    private String getDateAndTime(String[] separated) {
        return separated[2];
    }

    private String getToSendMessage(String[] separated) {
        return separated[0];
    }

    private String getMessageContent(String[] separated) {
        return separated[1];
    }

    private List<String> getTags(String[] separated) {
        List<String> userNameList = new LinkedList<>();
        String[] spaceSeparated = separated[0].split(" ");
        Arrays.toString(spaceSeparated);
        for (String s : spaceSeparated) {
            if (s.charAt(0) == '@') {
                userNameList.add(s.substring(1));
            }
        }
        return userNameList;
    }

    private String getContent(String[] separated) {
        return separated[0];
    }

    private String getFollow(String[] separated) {//the person i want to follow
        return separated[1];
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    @Override
    public void start(int connectionId, Connections<String> connections) {
        System.out.println("initialized");
        this.connectionId = connectionId;
        this.connections = connections;
        userName = "";
    }

    private String getUserName(String[] separated) {//in login and in register
        return separated[0];
    }
}


