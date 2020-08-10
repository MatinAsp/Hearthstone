package Network.Server;

import Logic.Game;
import Logic.PlayersManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class Server extends Thread{
    private static Server server;
    private static int defaultPort = 8080;
    private ArrayList<ClientHandler> clientHandlers;
    private ArrayList<ClientHandler>  waitingList;
    private ServerSocket serverSocket;
    private HashMap<ClientHandler, Game> gameMap;

    private Server(int serverPort) throws IOException {
        serverSocket = new ServerSocket(serverPort);
        gameMap = new HashMap<>();
        clientHandlers = new ArrayList<>();
        waitingList = new ArrayList<>();
    }

    public synchronized static Server getInstance() throws IOException {
        return getInstance(-1);
    }

    public synchronized static Server getInstance(int port) throws IOException {
        if(port == -1) port = defaultPort;
        if(server == null){
            server = new Server(port);
        }
        return server;
    }

    @Override
    public void run() {
        Thread thread = new Thread(()->{
            while (!isInterrupted()){
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    break;
                }
                PlayersManager.getInstance().save();
            }
        });
        thread.start();
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
           while (!isInterrupted()){
               if(scanner.nextLine().equals("exit")){
                   thread.interrupt();
                   try {
                       serverSocket.close();
                   } catch (IOException e) {
                       e.printStackTrace();
                   }
                   break;
               }
           }
        }).start();
        while(!isInterrupted() && !serverSocket.isClosed()){
            try {
                Socket socket = serverSocket.accept();
                ClientHandler  clientHandler = new ClientHandler(socket.getInputStream(), socket.getOutputStream(), this);
                clientHandlers.add(clientHandler);
                clientHandler.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void moveInGame(ClientHandler clientHandler, int move) {
//        Game game = gameMap.get(clientHandler);
//        ClientHandler enemyClientHandler = null;
//        for(ClientHandler clientHandler1: gameMap.keySet()){
//            if(game == gameMap.get(clientHandler1) && clientHandler1 != clientHandler){
//                enemyClientHandler = clientHandler1;
//                break;
//            }
//        }
//        try {
//            game.move(game.getClientHandlerIndex(clientHandler), move);
//        } catch (InvalidMoveException ignore) {}
//        catch (GameOverException e) {
//            recordWinning(clientHandler.getUsername());
//            gameMap.remove(clientHandler);
//            gameMap.remove(enemyClientHandler);
//        }
//        clientHandler.send(new String[]{"state", "" + game.getClientHandlerIndex(clientHandler) + game.getGameState()});
//        enemyClientHandler.send(new String[]{"state", "" + game.getClientHandlerIndex(enemyClientHandler) + game.getGameState()});
    }

    public synchronized void startGame(ClientHandler clientHandler){
//        waitingList.add(clientHandler);
//        if(waitingList.size() > 1){
//            ClientHandler clientHandler1 = waitingList.get(0), clientHandler2 = waitingList.get(1);
//            Game game = new Game(clientHandler1, clientHandler2);
//            gameMap.put(clientHandler1, game);
//            gameMap.put(clientHandler2, game);
//            waitingList.remove(clientHandler1);
//            waitingList.remove(clientHandler2);
//            clientHandler1.send(new String[]{"state", "" + game.getClientHandlerIndex(clientHandler1) + game.getGameState()});
//            clientHandler2.send(new String[]{"state", "" + game.getClientHandlerIndex(clientHandler2) + game.getGameState()});
//        }
    }

    public void exitClient(ClientHandler clientHandler) {
        clientHandlers.remove(clientHandler);
        clientHandler.send(new String[]{"exit"});
    }

}