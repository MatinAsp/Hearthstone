package Network.Server;

import Data.DataManager;
import Logic.PlayersManager;
import Models.Cards.Card;
import Models.Deck;
import Models.Hero;
import Models.Player;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sun.prism.shader.Solid_TextureRGB_AlphaTest_Loader;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class ClientHandler extends Thread{
    private Server server;
    private Player player = null;
    private Scanner scanner;
    private PrintStream printStream;
    private Gson gson;
    public ClientHandler(InputStream inputStream, OutputStream outputStream, Server server){
        this.server = server;
        scanner = new Scanner(inputStream);
        printStream = new PrintStream(outputStream);
        gson = new Gson();
    }

    @Override
    public void run() {
        while(!isInterrupted()){
            String string = scanner.nextLine();
            System.out.println("get: "+string);
            ArrayList<String> massagesList = gson.fromJson(string, new TypeToken<ArrayList<String>>(){}.getType());
            if(massagesList.get(0).equalsIgnoreCase("null")) player = null;
            else{
                Player player1 = gson.fromJson(massagesList.get(0), Player.class);
                if(isPlayerOk(player1)){
                    PlayersManager.getInstance().changePlayer(player, player1);
                    player = player1;
                }
            }
            String methodName = massagesList.get(1);
            massagesList.remove(0);
            massagesList.remove(0);
            if(methodName.equals("exit")){
                server.exitClient(this);
                return;
            }
            if(player == null && !methodName.equalsIgnoreCase("logIn") && !methodName.equalsIgnoreCase("signIn")){
                continue;
            }
            for(Method method: ClientHandler.class.getDeclaredMethods()){
                if(method.getName().equals(methodName)){
                    try{
                        if(massagesList.size() == 0){
                            method.invoke(this);
                        }
                        else {
                            method.invoke(this, massagesList.toArray());
                        }
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        sendException((Exception) e.getCause());
                    }
                    break;
                }
            }
        }
    }

    private boolean isPlayerOk(Player player1) {
        for(Hero hero: player1.getAllHeroes()){
            if(!player.haveHero(hero.getName())){
                return false;
            }
        }
        for(Card card: player1.getAllCards()){
            if(!player.haveCard(card.getName())){
                return false;
            }
        }
        for(Deck deck: player1.getAllDecks()){
            for(Card card: deck.getCards()){
                if(!player.haveCard(card.getName())){
                    return false;
                }
            }
        }
        if(player1.getWallet() != player.getWallet()) return false;
        return true;
    }

    private void update(String updateMethod){
        send(new String[]{"update", updateMethod});
    }

    private void logIn(String username, String password) throws Exception {
        player = PlayersManager.getInstance().logIn(username, password);
        send(new String[]{"logIn"});
    }

    private void signIn(String username, String password) throws Exception {
        player = PlayersManager.getInstance().signIn(username, password);
        send(new String[]{"signIn"});
    }

    public void sendException(Exception exception){
        send(new String[]{"error", exception.getClass().getName(), gson.toJson(exception)});
    }

    private void delete(String password) throws Exception {
        PlayersManager.getInstance().deletePlayer(player.getUsername(), password);
        player = null;
        send(new String[]{"update", "deleteUpdate"});
    }

    private void buyCard(String cardName) throws Exception {
        Card card = DataManager.getInstance().getObject(Card.class, cardName);
        if (player.getWallet() < card.getPrice()) throw new Exception("Don't have enough coin.");
        if (player.haveCard(cardName)) throw new Exception("You have this card.");
        player.setWallet(player.getWallet()-card.getPrice());
        player.addToCards(card);
        send(new String[]{"update", "storeCardsRender"});
    }

    private void sellCard(String cardName) throws Exception {
        if (!player.haveCard(cardName)) throw new Exception("You don't have this card.");
        Card card = DataManager.getInstance().getObject(Card.class, cardName);
        player.setWallet(player.getWallet()+card.getPrice());
        player.removeCard(card);
        send(new String[]{"update", "storeCardsRender"});
    }

    public synchronized void send(String[] massages){
        ArrayList<String> massagesList = new ArrayList<>();
        if(player != null) massagesList.add(gson.toJson(player));
        else massagesList.add("null");
        massagesList.addAll(Arrays.asList(massages));
        printStream.println(gson.toJson(massagesList));
        printStream.flush();
        System.out.println("send: "+gson.toJson(massagesList));
    }
}