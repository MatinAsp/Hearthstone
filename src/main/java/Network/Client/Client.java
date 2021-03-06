package Network.Client;

import Data.JacksonMapper;
import Graphics.Controller;
import Log.LogCenter;
import Logic.Game;
import Models.Cards.Card;
import Models.InfoPack;
import Models.Player;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;

public class Client extends Thread{
    private Socket socket;
    private PrintStream printStream;
    private Receiver receiver;
    private Controller controller;
    private Player player = null;
    private Gson gson;

    public Client(String serverIP, int serverPort, Controller controller) throws IOException {
        this.controller = controller;
        socket = new Socket(serverIP, serverPort);
        printStream = new PrintStream(socket.getOutputStream());
        receiver = new Receiver(this, socket.getInputStream());
        gson = new Gson();
    }

    @Override
    public void run() {
        try {
            receiver = new Receiver(this, socket.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
        receiver.start();
        while (receiver.isAlive()){
            if(!socket.isConnected()) Platform.runLater(() -> controller.setConnectionWait(true));
            else Platform.runLater(() -> controller.setConnectionWait(false));
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendLogInRequest(String username, String password){
        send(new String[]{"logIn", username, password});
    }

    public void sendSignInRequest(String username, String password){
        send(new String[]{"signIn", username, password});
    }

    private void logIn(){
        Platform.runLater(() ->{
            controller.logInActionUpdate();
        });
    }

    private void signIn(){
        LogCenter.getInstance().createLogFile(player);
        Platform.runLater(() ->{
            controller.logInActionUpdate();
        });
    }

    public void stopRunning() {
        send(new String[]{"exit"});
    }

    private void error(String className, String gsonString){
        try {
            Exception exception = (Exception) gson.fromJson(gsonString, Class.forName(className));
            controller.handleException(exception);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void update(String updateMethod){
        Platform.runLater(() ->{
            controller.currentDeckCheck();
            if(!updateMethod.equals("null")) {
                try {
                    Controller.class.getMethod(updateMethod).invoke(controller);
                } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void sendUpdateRequest(String updateMethodName){
        send(new String[]{"update", updateMethodName});
    }

    private synchronized void send(String[] massages){
        ArrayList<String> massagesList = new ArrayList<>();
        if(player != null) massagesList.add(gson.toJson(player));
        else massagesList.add("null");
        massagesList.addAll(Arrays.asList(massages));
        printStream.println(gson.toJson(massagesList));
        printStream.flush();
        System.out.println("send: "+gson.toJson(massagesList));
    }

    public void getMassage(String string) {
        ArrayList<String> massagesList = gson.fromJson(string, new TypeToken<ArrayList<String>>(){}.getType());
        if(massagesList.get(0).equalsIgnoreCase("null")) player = null;
        else player = gson.fromJson(massagesList.get(0), Player.class);
        String methodName = massagesList.get(1);
        massagesList.remove(0);
        massagesList.remove(0);
        for(Method method: Client.class.getDeclaredMethods()){
            if(method.getName().equals(methodName)){
                try{
                    if(massagesList.size() == 0){
                        method.invoke(this);
                    }
                    else {
                        method.invoke(this, massagesList.toArray());
                    }
                } catch (IllegalAccessException | InvocationTargetException e) {
                    //todo
                    e.printStackTrace();
                }
                break;
            }
        }
    }

    public synchronized void logInfo(String massage){
        if(player == null) return;
        LogCenter.getInstance().info(player, massage);
    }

    public synchronized void logError(String massage){
        if(player == null) return;
        LogCenter.getInstance().error(player, massage);
    }

    public synchronized void logError(Exception massage){
        massage.printStackTrace();
        if(player == null) return;
        LogCenter.getInstance().error(player, massage);
    }

    public Player getPlayer(){
        return player;
    }

    public void exitClient() {
        send(new String[]{"exit"});
    }

    public void buyRequest(String name) {
        send(new String[]{"buyCard", name});
    }

    public void sellRequest(String name) {
        send(new String[]{"sellCard", name});
    }

    public void deleteRequest(String password) {
        send(new String[]{"delete", password});
    }

    private void startGame(String gameJson, String time){
        Platform.runLater(() -> {
            try {
                controller.starGame(JacksonMapper.getNetworkMapper().readValue(gameJson, Game.class), time);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        });
    }

    private void updateGame(String gameJson, String time){
        try {
            controller.updateGame(JacksonMapper.getNetworkMapper().readValue(gameJson, Game.class), time);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    public void sendOnlinePlayRequest() {
        send(new String[]{"startOnlinePlay"});
    }

    public void sendPerformAction(InfoPack[] parameters) {
        send(new String[]{"performAction", gson.toJson(Arrays.asList(parameters))});
    }

    public void sendEndTurn() {
        send(new String[]{"endTurn"});
    }

    public void sendCardSelection(ArrayList<Card> cardsSelected) {
        send(new String[]{"cardSelection", gson.toJson(cardsSelected)});
    }

    private void gameOver(String gameJson){
        updateGame(gameJson, String.valueOf(0));
        controller.endGame();
    }

    public void sendCancelGame() {
        send(new String[]{"cancelGame"});
    }

    public void sendDeckReaderPlayRequest() {
        send(new String[]{"startDeckReaderPlay"});
    }

    public void sendOfflinePlayRequest() {
        send(new String[]{"startOfflinePlay"});
    }

    public void sendRankingRequest() {
        send(new String[]{"getRanking"});
    }

    private void updateRanking(String usernamesJson, String cupsJson, String ownRank){
        Platform.runLater(() -> controller.loadRanking(gson.fromJson(usernamesJson, new TypeToken<ArrayList<String>>(){}.getType()), gson.fromJson(cupsJson, new TypeToken<ArrayList<String>>(){}.getType()), Integer.parseInt(ownRank)));
    }

    private void backFormGame(){
        Platform.runLater(() -> controller.backFormGame());
    }
}
