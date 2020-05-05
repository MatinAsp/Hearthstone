package Data;

import java.io.*;
import java.util.List;

public class GameConstants {
    private static GameConstants gameConstants = null;
    private static String defaultAddress =
            "src"+File.separator+"main"+File.separator+"resources"+File.separator+"Configs.properties";
    private Configs configs;

    private GameConstants(String address) throws IOException {
        configs = new Configs();
        configs.load(new FileReader(new File(address)));
    }

    public static GameConstants getInstance() throws IOException {
        return getInstance("default");
    }

    public static GameConstants getInstance(String address) throws IOException {
        if(gameConstants == null){
            if(address.equals("default")){
                address = defaultAddress;
            }
            gameConstants = new GameConstants(address);
            defaultAddress = address;
        }
        return gameConstants;
    }

    public int getInteger (String name){
        return configs.readInteger(name);
    }

    public boolean getBoolean (String name){
        return configs.readBoolean(name);
    }

    public String getString (String name){
        return configs.getProperty(name);
    }

    public double getDouble(String name) {
        return configs.readDouble(name);
    }

    public List<String> getStingList(String name) {
        return configs.readStringList(name);
    }

    public List<Integer> getIntegerList(String name) {
        return configs.readIntegerList(name);
    }

    public void setProperty(String key, String newValue){
        configs.setProperty(key, newValue);
    }

    public void save() throws IOException {
        configs.store(new FileOutputStream(defaultAddress), "");
    }
}
